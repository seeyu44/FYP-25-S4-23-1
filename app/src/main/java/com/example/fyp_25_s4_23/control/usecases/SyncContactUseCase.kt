package com.example.fyp_25_s4_23.control.usecases

import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseContactRepository
import com.example.fyp_25_s4_23.data.remote.firebase.PhoneLookupService
import com.example.fyp_25_s4_23.domain.entities.Contact
import com.google.firebase.auth.FirebaseAuth
import android.util.Log

class SyncContactsUseCase(
    private val firebaseRepo: FirebaseContactRepository,
    private val localRepo: ContactRepository,
    private val phoneLookupService: PhoneLookupService
) {
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "SyncContactsUseCase"
    
    suspend fun execute() {
        val currentUserId = auth.currentUser?.uid ?: return
        val remoteContacts = firebaseRepo.fetchContacts()
        Log.d(TAG, "Syncing ${remoteContacts.size} remote contacts for user $currentUserId")

        val seenUsernames = mutableSetOf<String>()
        val seenPhones = mutableSetOf<String>()

        val localContacts = localRepo.getAllContactsOnce(currentUserId)
        Log.d(TAG, "Found ${localContacts.size} local contacts")
        
        val localPhoneContacts = localContacts
            .filter { it.phoneNumber != "VOIP_USER" }
        val phoneToLocalContact = localPhoneContacts.associateBy { it.phoneNumber }

        // Build username -> phone mapping from local contacts
        val usernameToPhone = mutableMapOf<String, String>()
        localPhoneContacts.forEach { localContact ->
            try {
                val result = phoneLookupService.getUserByPhoneNumber(localContact.phoneNumber)
                usernameToPhone[result.username] = localContact.phoneNumber
                Log.d(TAG, "Mapped username ${result.username} -> phone ${localContact.phoneNumber}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to look up username for ${localContact.phoneNumber}: ${e.message}")
            }
        }

        remoteContacts.forEach { contact ->
            val username = contact.username
            val rawPhone = contact.phoneNumber?.trim().orEmpty()
            val isVoip = rawPhone.isBlank()
            val phone = if (isVoip) "VOIP_USER" else rawPhone
            val displayName = contact.displayName?.takeIf { it.isNotBlank() } ?: username

            Log.d(TAG, "Processing remote contact: username=$username, type=$phone")

            val alreadySeen = (username.isNotEmpty() && seenUsernames.contains(username)) ||
                (!isVoip && seenPhones.contains(phone))

            if (alreadySeen) {
                Log.d(TAG, "Skipping (already seen): $username")
                return@forEach
            }

            // Try to find matching local contact
            var localContact: Contact? = null

            // 1. First try: exact username match
            if (username.isNotEmpty()) {
                localContact = localRepo.getContactByUsername(currentUserId, username)
                if (localContact != null) Log.d(TAG, "Found match by username: $username")
            }

            // 2. Second try: if this is a VOIP contact, check if we have the phone number locally
            if (localContact == null && isVoip && username.isNotEmpty()) {
                val knownPhone = usernameToPhone[username]
                if (knownPhone != null) {
                    localContact = phoneToLocalContact[knownPhone]
                    if (localContact != null) Log.d(TAG, "Found match by username->phone mapping: $username -> $knownPhone")
                } else {
                    Log.d(TAG, "No phone mapping found for username: $username")
                }
            }

            // 3. Third try: exact phone match (for phone contacts)
            if (localContact == null && !isVoip) {
                localContact = phoneToLocalContact[phone]
                if (localContact != null) Log.d(TAG, "Found match by phone: $phone")
            }

            if (localContact != null) {
                Log.d(TAG, "Updating label for existing contact: ${localContact.displayName}")
                localRepo.updateContactLabel(localContact.id, contact.label.name)
            } else {
                Log.d(TAG, "Creating new contact from remote: $username / $phone")
                localRepo.insertContact(
                    Contact(
                        id = contact.id,
                        userId = currentUserId,
                        displayName = displayName,
                        phoneNumber = phone,
                        label = contact.label
                    )
                )
            }

            if (username.isNotEmpty()) seenUsernames.add(username)
            if (!isVoip) seenPhones.add(phone)
        }

        // Remove local username-only contacts missing from Firebase
        val removedCount = localContacts
            .filter { it.phoneNumber == "VOIP_USER" }
            .filter { (it.displayName ?: "").isNotEmpty() }
            .filter { (it.displayName ?: "") !in seenUsernames }
            .onEach { 
                Log.d(TAG, "Removing local contact not in Firebase: ${it.displayName}")
                localRepo.deleteContact(it) 
            }
            .count()
        
        Log.d(TAG, "Sync complete: removed $removedCount orphaned contacts")
    }
}
