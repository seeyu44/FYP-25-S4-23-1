package com.example.fyp_25_s4_23.boundary.callhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.domain.entities.Contact
import com.example.fyp_25_s4_23.domain.entities.ContactLabel
import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.example.fyp_25_s4_23.data.remote.firebase.UsernameService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManagedContactsViewModel(
    private val repository: ContactRepository
) : ViewModel() {

    private val usernameService = UsernameService()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage = _uiMessage.asStateFlow()

    val contacts: StateFlow<List<Contact>> = repository.getAllContacts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addContactByUsername(
        username: String,
        label: ContactLabel,
        onSuccess: () -> Unit
    ) {
        val cleanUsername = username.trim().lowercase()

        viewModelScope.launch {
            try {
                val exists = !usernameService.checkUsername(cleanUsername)
                if (!exists) {
                    _uiMessage.value = "User not found"
                    return@launch
                }

                val newContact = Contact(
                    id = java.util.UUID.randomUUID().toString(),
                    displayName = cleanUsername,
                    phoneNumber = "VOIP_USER",
                    label = label
                )

                val alreadyExists = repository.existsByUsername(cleanUsername)
                if (alreadyExists) {
                    _uiMessage.value = "Contact already exists"
                    return@launch
                }

                repository.insertContact(newContact)
                _uiMessage.value = "Contact added"
                onSuccess()

            } catch (e: Exception) {
                _uiMessage.value = "Failed to add contact"
            }
        }
    }

    suspend fun verifyUsername(username: String): Boolean {
        // This MUST return:
        // true  → user EXISTS in Firebase
        // false → user does NOT exist
        return try {
            // usernameService.checkUsername() returns:
            // true  = available (NOT TAKEN)
            // false = already taken (EXISTS)
            !usernameService.checkUsername(username)
        } catch (e: Exception) {
            false
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    fun clearMessage() { _uiMessage.value = null }
}
