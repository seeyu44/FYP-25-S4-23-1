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
            ?: return emptyList()

        return runCatching {
            val snapshot = db.collection("public_users").get().await()

            snapshot.documents.mapNotNull { doc ->
                val username = doc.getString("username")
                val displayName = doc.getString("displayName")

                if (username != null && doc.id != currentUid) {
                    RemoteUser(
                        uid = doc.id,
                        username = username,
                        displayName = displayName ?: username
                    )
                } else null
            }
        }.getOrElse { e ->
            Log.e("FirebaseUserDirectory", "Failed to fetch public users", e)
            emptyList()
        }
    }
}
