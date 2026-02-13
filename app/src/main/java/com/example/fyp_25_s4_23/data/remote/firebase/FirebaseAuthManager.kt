package com.example.fyp_25_s4_23.data.remote.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

object FirebaseAuthManager {

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun isLoggedIn(): Boolean = auth.currentUser != null

    suspend fun login(
        email: String,
        password: String
    ): FirebaseUser {
        val result = auth
            .signInWithEmailAndPassword(email, password)
            .await()

        return result.user
            ?: throw IllegalStateException("Login failed: user is null")
    }



    suspend fun checkEmailExists(email: String) {
        // Firebase fetchSignInMethodsForEmail to check if email exists
        val methods = auth.fetchSignInMethodsForEmail(email).await()
        if (!methods.signInMethods.isNullOrEmpty()) {
            throw IllegalStateException("Email already exists")
        }
    }

    suspend fun createAdminUser(email: String, password: String): String {
        // Save current user
        val currentUser = auth.currentUser
        android.util.Log.d("FirebaseAuth", "Current admin UID before creating new user: ${currentUser?.uid}")
        
        // Create new admin user
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val newUser = result.user ?: throw IllegalStateException("Failed to create admin user")
        val newUid = newUser.uid
        android.util.Log.d("FirebaseAuth", "New admin user created with UID: $newUid")
        
        // Sign out the newly created user and restore previous session
        auth.signOut()
        currentUser?.let {
            // Re-authenticate the original admin
            auth.updateCurrentUser(it).await()
            android.util.Log.d("FirebaseAuth", "Restored original admin session: ${it.uid}")
        }
        
        return newUid
    }

    suspend fun logout() {
        auth.signOut()
    }

    suspend fun getIdToken(): String {
        val user = auth.currentUser
            ?: throw IllegalStateException("User not logged in")

        return user.getIdToken(true).await().token
            ?: throw IllegalStateException("Failed to fetch ID token")
    }

    suspend fun getCustomClaims(): Map<String, Any> {
        val user = auth.currentUser
            ?: throw IllegalStateException("User not logged in")

        // Force refresh to get latest custom claims
        val tokenResult = user.getIdToken(true).await()
        val token = tokenResult.token
            ?: throw IllegalStateException("Failed to fetch ID token")

        return try {
            // Parse JWT manually: header.payload.signature
            val parts = token.split(".")
            if (parts.size != 3) {
                throw IllegalStateException("Invalid JWT format")
            }

            // Decode the payload (second part) which is base64URL encoded JSON
            val payload = parts[1]
            val decodedBytes = android.util.Base64.decode(payload.padForBase64(), android.util.Base64.DEFAULT)
            val jsonString = String(decodedBytes, Charsets.UTF_8)

            // Parse JSON manually without external dependencies
            parseJsonToMap(jsonString)
        } catch (e: Exception) {
            android.util.Log.w("FirebaseAuth", "Failed to decode custom claims: ${e.message}")
            emptyMap()
        }
    }

    suspend fun getCustomClaimString(claimKey: String): String? {
        return try {
            val claims = getCustomClaims()
            claims[claimKey]?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun String.padForBase64(): String {
        val padding = 4 - length % 4
        return if (padding != 4) this + "=".repeat(padding) else this
    }

    private fun parseJsonToMap(json: String): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val trimmed = json.trim()
        
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return map
        
        val content = trimmed.substring(1, trimmed.length - 1)
        if (content.isEmpty()) return map
        
        // Simple JSON parsing for flat structure
        var current = 0
        while (current < content.length) {
            // Skip whitespace and commas
            while (current < content.length && (content[current] == ' ' || content[current] == ',')) {
                current++
            }
            if (current >= content.length) break
            
            // Parse key
            if (content[current] != '"') break
            val keyStart = current + 1
            current = content.indexOf('"', keyStart)
            if (current == -1) break
            val key = content.substring(keyStart, current).replace("\\\"", "\"")
            
            // Skip to value
            current = content.indexOf(':', current)
            if (current == -1) break
            current++ // skip ':'
            
            // Skip whitespace
            while (current < content.length && content[current] == ' ') current++
            
            // Parse value
            val value = when {
                current < content.length && content[current] == '"' -> {
                    val valueStart = current + 1
                    current = content.indexOf('"', valueStart)
                    if (current == -1) break
                    content.substring(valueStart, current).replace("\\\"", "\"") as Any
                }
                current < content.length && content[current].isDigit() -> {
                    val valueStart = current
                    while (current < content.length && 
                           (content[current].isDigit() || content[current] == '.' || content[current] == '-')) {
                        current++
                    }
                    content.substring(valueStart, current).toDoubleOrNull() ?: content.substring(valueStart, current) as Any
                }
                content.startsWith("true", current) -> {
                    current += 4
                    true as Any
                }
                content.startsWith("false", current) -> {
                    current += 5
                    false as Any
                }
                content.startsWith("null", current) -> {
                    current += 4
                    "" as Any
                }
                else -> break
            }
            
            map[key] = value
        }
        
        return map
    }
}
