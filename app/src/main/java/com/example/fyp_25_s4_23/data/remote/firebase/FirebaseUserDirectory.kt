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
        val currentUid = auth.currentUser?.uid
        Log.d("FirebaseUserDirectory", "Current UID: $currentUid")
        
        if (currentUid == null) {
            Log.w("FirebaseUserDirectory", "No authenticated user")
            return emptyList()
        }

        return runCatching {
            val snapshot = db.collection("public_users").get().await()
            Log.d("FirebaseUserDirectory", "Total documents in public_users: ${snapshot.size()}")

            val users = snapshot.documents.mapNotNull { doc ->
                val username = doc.getString("username")
                val displayName = doc.getString("displayName")
                Log.d("FirebaseUserDirectory", "Doc ID: ${doc.id}, username: $username, displayName: $displayName")

                if (username != null && doc.id != currentUid) {
                    Log.d("FirebaseUserDirectory", "Adding user: ${doc.id} ($username)")
                    RemoteUser(
                        uid = doc.id,
                        username = username,
                        displayName = displayName ?: username
                    )
                } else {
                    Log.d("FirebaseUserDirectory", "Skipping doc ${doc.id}: username=$username, isCurrentUser=${doc.id == currentUid}")
                    null
                }
            }
            Log.d("FirebaseUserDirectory", "Returning ${users.size} users")
            users
        }.getOrElse { e ->
            Log.e("FirebaseUserDirectory", "Failed to fetch public users", e)
            emptyList()
        }
    }
}
