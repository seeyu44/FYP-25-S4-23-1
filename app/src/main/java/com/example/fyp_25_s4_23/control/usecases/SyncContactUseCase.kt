package com.example.fyp_25_s4_23.control.usecases

import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseContactRepository

class SyncContactsUseCase(
    private val firebaseRepo: FirebaseContactRepository,
    private val localRepo: ContactRepository
) {
    suspend fun execute() {
        val remoteContacts = firebaseRepo.fetchContacts()

        // Clear local database
        localRepo.clearAll()
        
        // Insert contacts from Firebase, avoiding duplicates by username/phone
        val addedUsernames = mutableSetOf<String>()
        val addedPhones = mutableSetOf<String>()
        
        remoteContacts.forEach { contact ->
            val username = contact.displayName ?: ""
            val phone = contact.phoneNumber
            
            // Skip if we've already added this username or phone
            val isDuplicate = (username.isNotEmpty() && addedUsernames.contains(username)) ||
                             (phone != "VOIP_USER" && addedPhones.contains(phone))
            
            if (!isDuplicate) {
                localRepo.insertContact(contact)
                if (username.isNotEmpty()) addedUsernames.add(username)
                if (phone != "VOIP_USER") addedPhones.add(phone)
            }
        }
    }
}
