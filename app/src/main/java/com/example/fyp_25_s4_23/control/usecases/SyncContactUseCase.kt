package com.example.fyp_25_s4_23.control.usecases

import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseContactRepository

class SyncContactsUseCase(
    private val firebaseRepo: FirebaseContactRepository,
    private val localRepo: ContactRepository
) {
    suspend fun execute() {
        val remoteContacts = firebaseRepo.fetchContacts()

        localRepo.clearAll()
        remoteContacts.forEach { contact ->
            localRepo.insertContact(contact)
        }
    }
}
