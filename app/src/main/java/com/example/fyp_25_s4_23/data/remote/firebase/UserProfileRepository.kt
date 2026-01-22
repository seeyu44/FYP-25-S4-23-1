package com.example.fyp_25_s4_23.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
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
            "email" to email,
            "username" to username,
            "display_name" to displayName,
            "role" to role,
            "plan_tier" to "",
            "verified" to false,
            "created_at_seconds" to System.currentTimeMillis() / 1000
        )

        db.collection("users")
            .document(uid)
            .set(userProfile)
            .await()
    }
}