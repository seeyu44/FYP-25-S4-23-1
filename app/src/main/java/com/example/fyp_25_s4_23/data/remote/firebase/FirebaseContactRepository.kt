package com.example.fyp_25_s4_23.data.remote.firebase

import com.example.fyp_25_s4_23.domain.entities.Contact
import com.example.fyp_25_s4_23.domain.entities.ContactLabel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

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

    suspend fun addContact(
        username: String,
        label: ContactLabel
    ) {
        val doc = contactsRef().document()

        val data = mapOf(
            "username" to username,
            "label" to label.name,
            "createdAt" to System.currentTimeMillis(),
            "addedBy" to auth.currentUser!!.uid
        )

        doc.set(data).await()
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

        snapshot.documents.forEach { doc ->
            contactsRef().document(doc.id)
                .update("label", label.name)
                .await()
        }
    }

    suspend fun fetchContacts(): List<Contact> {
        return contactsRef()
            .get()
            .await()
            .documents
            .map {
                Contact(
                    id = it.id,
                    displayName = it.getString("username")!!,
                    phoneNumber = "VOIP_USER",
                    label = ContactLabel.valueOf(it.getString("label")!!)
                )
            }
    }
}
