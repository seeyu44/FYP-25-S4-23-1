package com.example.fyp_25_s4_23.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import com.example.fyp_25_s4_23.data.remote.dto.UserProfile

class UserProfileRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getUserProfile(uid: String): UserProfile{
        val snapshot = db.collection("users")
            .document(uid)
            .get()
            .await()

        if(!snapshot.exists()){
            throw IllegalStateException("User profile not found")
        }

        val roleFromFirebase = snapshot.getString("role") ?: "REGISTERED"

        return UserProfile(
            uid = uid,
            email = snapshot.getString("email") ?: "",
            username = snapshot.getString("username") ?: "",
            displayName = snapshot.getString("display_name") ?: "",
            role = roleFromFirebase,
            planTier = snapshot.getString("plan_tier") ?: "",
            verified = snapshot.getBoolean("verified") ?: false,
            createdAtSeconds = snapshot.getLong("created_at_seconds") ?: 0
        )
    }

    suspend fun createUserProfile(
        uid: String,
        email: String,
        username: String,
        displayName: String,
        role: String
    ) {
        val userProfile = hashMapOf(
            "username" to username,
            "displayName" to displayName,
            "role" to role,
            "createdAtSeconds" to System.currentTimeMillis() / 1000,
            "adminVerificationSent" to false,
            "needsVerificationOnFirstLogin" to true
        )

        db.collection("users")
            .document(uid)
            .set(userProfile)
            .await()
    }

    suspend fun finalizeAdminUser(uid: String, displayName: String) {
        val functions = Firebase.functions
        val addAdminUser = functions.getHttpsCallable("addAdminUser")
        
        val data = hashMapOf(
            "uid" to uid,
            "displayName" to displayName
        )
        
        addAdminUser.call(data).await()
    }
}
