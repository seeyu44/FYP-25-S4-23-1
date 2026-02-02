package com.example.fyp_25_s4_23.boundary.callhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseContactRepository
import com.example.fyp_25_s4_23.data.remote.firebase.PhoneLookupService
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
    private val phoneLookupService = PhoneLookupService()

    private val _uiMessage = MutableStateFlow<String?>(null)
    private val firebaseRepo = FirebaseContactRepository()
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
      val currentUsername = com.google.firebase.auth.FirebaseAuth.getInstance()
          .currentUser
          ?.displayName
          ?.lowercase()

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

                firebaseRepo.addContact(cleanUsername, label)
                repository.insertContact(newContact)
                _uiMessage.value = "Contact added"
                onSuccess()

            } catch (e: Exception) {
                _uiMessage.value = "Failed to add contact"
            }
        }
    }

    fun addContactByPhoneNumber(
        phoneNumber: String,
        contactName: String,
        label: ContactLabel,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Validate phone number format (8 digits starting with 8 or 9)
                val cleanPhone = phoneNumber.trim()
                if (!isValidPhoneNumber(cleanPhone)) {
                    _uiMessage.value = "Invalid phone number. Enter 8 digits starting with 8 or 9."
                    return@launch
                }

                // Format phone number to +65XXXXXXXX
                val formattedPhone = "+65$cleanPhone"

                // Check if phone number already exists in local database
                if (repository.existsByPhoneNumber(formattedPhone)) {
                    _uiMessage.value = "Contact with this phone number already exists"
                    return@launch
                }

                // Look up the username from the phone number
                val lookupResult = try {
                    phoneLookupService.getUserByPhoneNumber(formattedPhone)
                } catch (e: Exception) {
                    _uiMessage.value = "Phone number not found in system"
                    return@launch
                }

                // Create the contact
                val newContact = Contact(
                    id = java.util.UUID.randomUUID().toString(),
                    displayName = contactName.trim(),
                    phoneNumber = formattedPhone,
                    label = label
                )

                // Add to local database
                repository.insertContact(newContact)

                // Add to Firebase
                firebaseRepo.addContact(lookupResult.username, label)

                _uiMessage.value = "Contact added successfully"
                onSuccess()

            } catch (e: Exception) {
                _uiMessage.value = "Failed to add contact: ${e.message}"
            }
        }
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // Should be 8 digits starting with 8 or 9
        return phoneNumber.length == 8 && 
               (phoneNumber[0] == '8' || phoneNumber[0] == '9') &&
               phoneNumber.all { it.isDigit() }
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

    suspend fun verifyPhoneNumber(phoneNumber: String): Boolean {
        return try {
            val cleanPhone = phoneNumber.trim()
            if (!isValidPhoneNumber(cleanPhone)) return false
            
            val formattedPhone = "+65$cleanPhone"
            phoneLookupService.getUserByPhoneNumber(formattedPhone)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    fun blockContact(contact: Contact) {
        viewModelScope.launch {
            try {
                repository.updateContactLabel(contact.id, ContactLabel.BLACK.toString())
                _uiMessage.value = "Contact has been blocked"
            } catch (e: Exception) {
                _uiMessage.value = "Failed to block contact"
            }
        }
    }

    fun unblockContact(contact: Contact) {
        viewModelScope.launch {
            try {
                repository.updateContactLabel(contact.id, ContactLabel.WHITE.toString())
                _uiMessage.value = "Contact has been unblocked"
            } catch (e: Exception) {
                _uiMessage.value = "Failed to unblock contact"
            }
        }
    }

    fun callContact(phoneNumber: String, onComplete: (username: String?) -> Unit) {
        viewModelScope.launch {
            try {
                // If it's a VOIP_USER (username-based contact), can't call by phone
                if (phoneNumber == "VOIP_USER") {
                    _uiMessage.value = "Cannot call username-based contact by phone"
                    onComplete(null)
                    return@launch
                }

                // Phone number is already formatted as +6587654321
                val formattedPhone = if (phoneNumber.startsWith("+65")) {
                    phoneNumber
                } else if (phoneNumber.length == 8) {
                    "+65$phoneNumber"
                } else {
                    _uiMessage.value = "Invalid phone number format"
                    onComplete(null)
                    return@launch
                }

                val lookupResult = try {
                    phoneLookupService.getUserByPhoneNumber(formattedPhone)
                } catch (e: Exception) {
                    _uiMessage.value = "Phone number not found"
                    onComplete(null)
                    return@launch
                }

                onComplete(lookupResult.uid)
            } catch (e: Exception) {
                _uiMessage.value = "Failed to initiate call: ${e.message}"
                onComplete(null)
            }
        }
    }

    fun clearMessage() { _uiMessage.value = null }
}
