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

    fun addContactByUsername(username: String, label: ContactLabel, onSuccess: () -> Unit) {
        val cleanUsername = username.trim().lowercase()
        if (cleanUsername.isEmpty()) {
            _uiMessage.value = "Please enter a username"
            return
        }

        viewModelScope.launch {
            try {
                val isAvailable = usernameService.checkUsername(cleanUsername)

                if (!isAvailable) {
                    val newContact = Contact(
                        id = java.util.UUID.randomUUID().toString(),
                        displayName = cleanUsername,
                        phoneNumber = "VOIP_USER",
                        label = label
                    )
                    repository.insertContact(newContact)
                    _uiMessage.value = "Contact added successfully"
                    onSuccess()
                } else {
                    _uiMessage.value = "User '$cleanUsername' not found on server"
                }
            } catch (e: Exception) {
                _uiMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    fun clearMessage() { _uiMessage.value = null }
}
