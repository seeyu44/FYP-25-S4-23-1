package com.example.fyp_25_s4_23.data.remote.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class GlobalBlockRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun globalBlockedRef(): CollectionReference {
        return firestore.collection("global_blocked")
    }

    data class GlobalBlockedUser(
        val userId: String,
        val username: String? = null,
        val phoneNumber: String? = null,
        val label: String = "flag",
        val flaggedAt: Long? = null
    )

    suspend fun isGloballyBlocked(userId: String): Boolean {
        if (userId.isBlank()) return false

        val snapshot = globalBlockedRef().document(userId).get().await()
        if (!snapshot.exists()) return false

        val label = snapshot.getString("label")?.lowercase()
        return label == "flag" || label == "blacklisted"
    }

    suspend fun listFlaggedUsers(): List<GlobalBlockedUser> {
        val snapshot = globalBlockedRef()
            .whereEqualTo("label", "flag")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val userId = doc.getString("userId") ?: doc.id
            if (userId.isBlank()) return@mapNotNull null
            val flaggedAtMillis = when (val raw = doc.get("flaggedAt")) {
                is com.google.firebase.Timestamp -> raw.toDate().time
                is Number -> raw.toLong()
                else -> null
            }
            GlobalBlockedUser(
                userId = userId,
                username = doc.getString("username"),
                phoneNumber = doc.getString("phoneNumber"),
                label = doc.getString("label") ?: "flag",
                flaggedAt = flaggedAtMillis
            )
        }
    }

    suspend fun updateLabel(userId: String, label: String) {
        if (userId.isBlank()) return
        val cleanLabel = label.lowercase()
        val data = mutableMapOf<String, Any>(
            "label" to cleanLabel,
            "reviewedAt" to System.currentTimeMillis()
        )
        auth.currentUser?.uid?.let { data["reviewedBy"] = it }
        globalBlockedRef().document(userId).set(data, SetOptions.merge()).await()
    }

    suspend fun removeUser(userId: String) {
        if (userId.isBlank()) return
        globalBlockedRef().document(userId).delete().await()
    }

    suspend fun flagUser(
        userId: String,
        username: String?,
        phoneNumber: String?,
        callId: String?
    ) {
        if (userId.isBlank()) return

        val doc = globalBlockedRef().document(userId)
        val existing = doc.get().await()
        val existingLabel = existing.getString("label")?.lowercase()
        val finalLabel = if (existingLabel == "blacklisted") "blacklisted" else "flag"

        val data = mutableMapOf(
            "userId" to userId,
            "label" to finalLabel,
            "flaggedAt" to System.currentTimeMillis()
        )

        auth.currentUser?.uid?.let { data["flaggedBy"] = it }
        if (!username.isNullOrBlank()) {
            data["username"] = username
        }
        if (!phoneNumber.isNullOrBlank()) {
            data["phoneNumber"] = phoneNumber
        }
        if (!callId.isNullOrBlank()) {
            data["lastCallId"] = callId
        }

        doc.set(data, SetOptions.merge()).await()
        Log.d("GLOBAL_BLOCK", "Flagged userId=$userId label=$finalLabel")
    }
}
