package com.example.fyp_25_s4_23.control.usecases

import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseContactRepository
import com.example.fyp_25_s4_23.data.remote.firebase.PhoneLookupService
import com.google.firebase.auth.FirebaseAuth

class SyncContactsUseCase(
    private val firebaseRepo: FirebaseContactRepository,
    private val localRepo: ContactRepository,
    private val phoneLookupService: PhoneLookupService
) {
    private val auth = FirebaseAuth.getInstance()
    
    suspend fun execute() {
        val currentUserId = auth.currentUser?.uid ?: return
        val remoteContacts = firebaseRepo.fetchContacts()

        // Merge remote contacts into local database without overwriting local
        // displayName/phoneNumber (custom names should remain local).
        val seenUsernames = mutableSetOf<String>()
        val seenPhones = mutableSetOf<String>()

        val localContacts = localRepo.getAllContactsOnce(currentUserId)
        val localPhoneContacts = localContacts
            .filter { it.phoneNumber != "VOIP_USER" }
        val phoneToLocalContact = localPhoneContacts.associateBy { it.phoneNumber }

        // Build username -> phone mapping from local contacts
        val usernameToPhone = mutableMapOf<String, String>()
        localPhoneContacts.forEach { localContact ->
            try {
                val result = phoneLookupService.getUserByPhoneNumber(localContact.phoneNumber)
                usernameToPhone[result.username] = localContact.phoneNumber
            } catch (_: Exception) {
                // ignore lookup errors
            }
        }

        remoteContacts.forEach { contact ->
            val username = contact.displayName ?: ""
            val phone = contact.phoneNumber

            val alreadySeen = (username.isNotEmpty() && seenUsernames.contains(username)) ||
                (phone != "VOIP_USER" && seenPhones.contains(phone))

            if (alreadySeen) return@forEach

            // Try to find matching local contact
            var localContact: com.example.fyp_25_s4_23.domain.entities.Contact? = null

            // 1. First try: exact username match
            if (username.isNotEmpty()) {
                localContact = localRepo.getContactByUsername(currentUserId, username)
            }

            // 2. Second try: if this is a VOIP contact, check if the username maps to a phone number we have locally
            if (localContact == null && phone == "VOIP_USER" && username.isNotEmpty()) {
                val knownPhone = usernameToPhone[username]
                if (knownPhone != null) {
                    localContact = phoneToLocalContact[knownPhone]
                }
            }

            // 3. Third try: exact phone match (for phone contacts)
            if (localContact == null && phone != "VOIP_USER") {
                localContact = phoneToLocalContact[phone]
            }

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
        localContacts
            .filter { it.phoneNumber == "VOIP_USER" }
            .filter { (it.displayName ?: "").isNotEmpty() }
            .filter { (it.displayName ?: "") !in seenUsernames }
            .forEach { localRepo.deleteContact(it) }
    }
}
