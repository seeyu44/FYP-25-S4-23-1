package com.example.fyp_25_s4_23.control.usecases

import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseContactRepository

class SyncContactsUseCase(
    private val firebaseRepo: FirebaseContactRepository,
    private val localRepo: ContactRepository
) {
    suspend fun execute() {
        val remoteContacts = firebaseRepo.fetchContacts()

        // Merge remote contacts into local database without overwriting local
        // displayName/phoneNumber (custom names should remain local).
        val seenUsernames = mutableSetOf<String>()
        val seenPhones = mutableSetOf<String>()

        remoteContacts.forEach { contact ->
            val username = contact.displayName ?: ""
            val phone = contact.phoneNumber

            val alreadySeen = (username.isNotEmpty() && seenUsernames.contains(username)) ||
                (phone != "VOIP_USER" && seenPhones.contains(phone))

            if (alreadySeen) return@forEach

            val localByUsername = if (username.isNotEmpty()) {
                localRepo.getContactByUsername(username)
            } else null

            val localByPhone = if (phone != "VOIP_USER") {
                localRepo.getContactByPhoneNumber(phone)
            } else null

            val localContact = localByUsername ?: localByPhone

            if (localContact != null) {
                // Update label only; keep local displayName/phoneNumber
                localRepo.updateContactLabel(localContact.id, contact.label.name)
            } else {
                localRepo.insertContact(contact)
            }

            if (username.isNotEmpty()) seenUsernames.add(username)
            if (phone != "VOIP_USER") seenPhones.add(phone)
        }

        // Remove local username-only contacts missing from Firebase
        val localContacts = localRepo.getAllContactsOnce()
        localContacts
            .filter { it.phoneNumber == "VOIP_USER" }
            .filter { (it.displayName ?: "").isNotEmpty() }
            .filter { (it.displayName ?: "") !in seenUsernames }
            .forEach { localRepo.deleteContact(it) }
    }
}
