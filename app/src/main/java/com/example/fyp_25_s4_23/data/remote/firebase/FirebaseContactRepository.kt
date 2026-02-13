package com.example.fyp_25_s4_23.data.remote.firebase

import com.example.fyp_25_s4_23.entity.domain.entities.ContactLabel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import android.util.Log

class FirebaseContactRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun contactsRef(): CollectionReference {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Not logged in")

        return firestore
            .collection("users")
            .document(uid)
            .collection("contacts")
    }

    data class RemoteContact(
        val id: String,
        val username: String,
        val userId: String?,
        val displayName: String?,
        val phoneNumber: String?,
        val label: ContactLabel
    )

    suspend fun addContact(
        username: String,
        label: ContactLabel,
        userId: String? = null,
        displayName: String? = null,
        phoneNumber: String? = null
    ) {
        val trimmedUsername = username.trim()
        if (trimmedUsername.isBlank()) {
            throw IllegalArgumentException("Username is required to save a contact")
        }

        // Use username as document id to prevent duplicate contacts for the same user.
        val doc = contactsRef().document(trimmedUsername)

        val data = mutableMapOf(
            "username" to trimmedUsername,
            "label" to label.name,
            "createdAt" to System.currentTimeMillis(),
            "addedBy" to auth.currentUser!!.uid
        )

        if (!displayName.isNullOrBlank()) {
            data["displayName"] = displayName
        }

        if (!phoneNumber.isNullOrBlank()) {
            data["phoneNumber"] = phoneNumber
        }

        if (!userId.isNullOrBlank()) {
            data["userId"] = userId
        }

        doc.set(data, SetOptions.merge()).await()
    }

    suspend fun deleteContact(contactId: String) {
        contactsRef().document(contactId).delete().await()
    }

    suspend fun deleteContactByUsername(username: String) {
        val snapshot = contactsRef()
            .whereEqualTo("username", username)
            .get()
            .await()

        snapshot.documents.forEach { doc ->
            contactsRef().document(doc.id).delete().await()
        }
    }

    suspend fun updateContactLabel(username: String, label: ContactLabel) {
        val snapshot = contactsRef()
            .whereEqualTo("username", username)
            .get()
            .await()

        Log.d("FIREBASE_CONTACT", "Found ${snapshot.documents.size} contact(s) with username=$username")
        
        snapshot.documents.forEach { doc ->
            Log.d("FIREBASE_CONTACT", "Updating contact ${doc.id} label to ${label.name}")
            contactsRef().document(doc.id)
                .update("label", label.name)
                .await()
        }
    }

    suspend fun fetchContacts(): List<RemoteContact> {
        return contactsRef()
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val username = doc.getString("username")
                if (username.isNullOrBlank()) {
                    Log.w("FIREBASE_CONTACT", "Skipping contact ${doc.id}: missing username")
                    return@mapNotNull null
                }

                val labelRaw = doc.getString("label")
                val label = runCatching { ContactLabel.valueOf(labelRaw ?: "") }
                    .getOrDefault(ContactLabel.NONE)

                val displayName = doc.getString("displayName")
                    ?: doc.getString("contactName")

                val phoneNumber = doc.getString("phoneNumber")

                val userId = doc.getString("userId")

                RemoteContact(
                    id = doc.id,
                    username = username,
                    userId = userId,
                    displayName = displayName,
                    phoneNumber = phoneNumber,
                    label = label
                )
            }
    }
}
