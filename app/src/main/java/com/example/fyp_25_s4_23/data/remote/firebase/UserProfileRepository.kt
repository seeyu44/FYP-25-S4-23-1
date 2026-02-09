package com.example.fyp_25_s4_23.data.remote.firebase

import android.util.Log
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

        // Try to get role from custom claims first (for admin verification via Firebase)
        var roleFromFirebase = snapshot.getString("role") ?: "REGISTERED"
        
        try {
            val customClaimRole = FirebaseAuthManager.getCustomClaimString("admin")
            if (customClaimRole != null && customClaimRole.toBoolean()) {
                roleFromFirebase = "ADMIN"
                Log.d("UserProfile", "Using ADMIN role from Firebase custom claims for uid=$uid")
            }
        } catch (e: Exception) {
            // Custom claims not available or error occurred, use Firestore role
            Log.d("UserProfile", "Custom claims not available, using Firestore role: $roleFromFirebase")
        }

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

    suspend fun finalizeAdminUser(uid: String, displayName: String) {
        val functions = Firebase.functions
        val addAdminUser = functions.getHttpsCallable("addAdminUser")
        
        val data = hashMapOf(
            "uid" to uid,
            "displayName" to displayName,
            "role" to "ADMIN"
        )
        
        addAdminUser.call(data).await()
    }
}
