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

    suspend fun register(
        email: String,
        password: String
    ): FirebaseUser {
        val result = auth
            .createUserWithEmailAndPassword(email, password)
            .await()

        return result.user
            ?: throw IllegalStateException("Registration failed")
    }

    suspend fun sendEmailVerification() {
        val user = auth.currentUser
            ?: throw IllegalStateException("No logged-in user")

        user.sendEmailVerification().await()
    }

    suspend fun createAdminUser(email: String, password: String): String {
        // Save current user
        val currentUser = auth.currentUser
        
        // Create new admin user
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val newUser = result.user ?: throw IllegalStateException("Failed to create admin user")
        val newUid = newUser.uid
        
        // Sign out the newly created user and restore previous session
        auth.signOut()
        currentUser?.let {
            // Re-authenticate the original admin
            auth.updateCurrentUser(it).await()
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
}
