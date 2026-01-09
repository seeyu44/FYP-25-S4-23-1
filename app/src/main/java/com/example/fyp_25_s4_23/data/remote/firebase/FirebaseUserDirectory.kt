package com.example.fyp_25_s4_23.data.remote.firebase

import com.example.fyp_25_s4_23.data.remote.dto.RemoteUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log


class FirebaseUserDirectory {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getAllUsers(): List<RemoteUser> {
        return runCatching {
            val snapshot = db.collection("users").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.getString("username")?.let {
                    RemoteUser(uid = doc.id, username = it)
                }
            }
        }.getOrElse { e ->
            Log.e("FirebaseUserDirectory", "Failed to fetch users", e)
            emptyList()
        }
    }
}