package com.example.fyp_25_s4_23.data.remote.firebase

import android.util.Log
import com.example.fyp_25_s4_23.data.remote.dto.RemoteUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseUserDirectory {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun getAllUsers(): List<RemoteUser> {
        val currentUid = auth.currentUser?.uid ?: return emptyList()
        return runCatching {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable("listUsers").call().await()
            @Suppress("UNCHECKED_CAST")
            val authUserList = result.data as? List<Map<String, Any>> ?: emptyList()

            // Fetch Firestore public_users
            val snapshot = db.collection("public_users").get().await()
            val firestoreUsers = snapshot.documents.associateBy { it.id }

            authUserList.mapNotNull { userMap ->
                val uid = userMap["uid"] as? String ?: return@mapNotNull null
                val disabled = userMap["disabled"] as? Boolean ?: false
                val authDisplayName = userMap["displayName"] as? String ?: ""
                val email = userMap["email"] as? String ?: ""

                val firestoreDoc = firestoreUsers[uid]
                val username = firestoreDoc?.getString("username") ?: email
                val displayName = firestoreDoc?.getString("displayName") ?: authDisplayName.ifBlank { username }

                if (uid != currentUid) {
                    RemoteUser(
                        uid = uid,
                        username = username,
                        displayName = displayName,
                        disabled = disabled
                    )
                } else null
            }
        }.getOrElse { e ->
            Log.e("FirebaseUserDirectory", "Failed to fetch users from cloud function and Firestore", e)
            emptyList()
        }
    }
}
