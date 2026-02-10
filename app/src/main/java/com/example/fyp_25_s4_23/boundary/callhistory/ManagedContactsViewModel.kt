package com.example.fyp_25_s4_23.boundary.callhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseContactRepository
import com.example.fyp_25_s4_23.data.remote.firebase.PhoneLookupService
import com.example.fyp_25_s4_23.domain.entities.Contact
import com.example.fyp_25_s4_23.domain.entities.ContactLabel
import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ManagedContactsViewModel(
    private val repository: ContactRepository
) : ViewModel() {

    private val phoneLookupService = PhoneLookupService()
    private val auth = FirebaseAuth.getInstance()

    private val _uiMessage = MutableStateFlow<String?>(null)
    private val firebaseRepo = FirebaseContactRepository()
    val uiMessage = _uiMessage.asStateFlow()

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    // Use switchMap pattern to restart the flow when user changes
    private val userIdFlow = MutableStateFlow(currentUserId)
    
    val contacts: StateFlow<List<Contact>> = userIdFlow
        .flatMapLatest { userId ->
            if (userId.isEmpty()) {
                flowOf(emptyList())
            } else {
                repository.getAllContacts(userId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    init {
        // Listen for auth state changes
        viewModelScope.launch {
            flow {
                var lastUserId = currentUserId
                while (true) {
                    delay(1000) // Check every second
                    val newUserId = auth.currentUser?.uid ?: ""
                    if (newUserId != lastUserId) {
                        lastUserId = newUserId
                        emit(newUserId)
                    }
                }
            }.collect { newUserId ->
                android.util.Log.d("ManagedContactsVM", "User changed: $newUserId")
                userIdFlow.value = newUserId
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
                // Validate phone number format (8 digits starting with 6, 8, or 9)
                val cleanPhone = phoneNumber.trim()
                if (!isValidPhoneNumber(cleanPhone)) {
                    _uiMessage.value = "Invalid phone number. Enter 8 digits starting with 6, 8, or 9."
                    return@launch
                }

                // Format phone number to +65XXXXXXXX
                val formattedPhone = "+65$cleanPhone"

                // Check if phone number already exists in local database
                android.util.Log.d("ADD_CONTACT", "Checking phone: $formattedPhone for userId: $currentUserId")
                if (repository.existsByPhoneNumber(currentUserId, formattedPhone)) {
                    _uiMessage.value = "Contact with this phone number already exists"
                    android.util.Log.w("ADD_CONTACT", "Phone number already exists locally")
                    return@launch
                }

                // Look up the username from the phone number
                val lookupResult = try {
                    phoneLookupService.getUserByPhoneNumber(formattedPhone)
                } catch (e: Exception) {
                    _uiMessage.value = "Phone number not found in system"
                    android.util.Log.e("ADD_CONTACT", "Phone lookup failed", e)
                    return@launch
                }

                // Check if username already exists (avoid duplicate from Firebase sync)
                android.util.Log.d("ADD_CONTACT", "Checking username: ${lookupResult.username} for userId: $currentUserId")
                val alreadyExists = repository.existsByUsername(currentUserId, lookupResult.username)
                if (alreadyExists) {
                    _uiMessage.value = "Contact already exists with this user"
                    android.util.Log.w("ADD_CONTACT", "Username already exists locally")
                    return@launch
                }

                // Create the contact
                val newContact = Contact(
                    id = java.util.UUID.randomUUID().toString(),
                    userId = currentUserId,
                    displayName = contactName.trim(),
                    phoneNumber = formattedPhone,
                    label = label
                )

                // Add to Firebase first (uses username)
                android.util.Log.d("ADD_CONTACT", "Adding to Firebase: username=${lookupResult.username}, label=$label")
                firebaseRepo.addContact(
                    username = lookupResult.username,
                    label = label,
                    displayName = newContact.displayName,
                    phoneNumber = newContact.phoneNumber
                )
                
                // Add to local database
                android.util.Log.d("ADD_CONTACT", "Adding to local DB: displayName=${newContact.displayName}, phone=${newContact.phoneNumber}")
                repository.insertContact(newContact)

                _uiMessage.value = "Contact added successfully"
                android.util.Log.d("ADD_CONTACT", "Contact added successfully")
                onSuccess()

            } catch (e: Exception) {
                _uiMessage.value = "Failed to add contact: ${e.message}"
            }
        }
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
         // Should be 8 digits starting with 6, 8, or 9
         return phoneNumber.length == 8 &&
             (phoneNumber[0] == '6' || phoneNumber[0] == '8' || phoneNumber[0] == '9') &&
             phoneNumber.all { it.isDigit() }
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
            var firebaseFailed = false

            try {
                val username = if (contact.phoneNumber == "VOIP_USER") {
                    contact.displayName
                } else {
                    val normalizedPhone = if (contact.phoneNumber.startsWith("+65")) {
                        contact.phoneNumber
                    } else {
                        "+65${contact.phoneNumber}"
                    }

                    phoneLookupService.getUserByPhoneNumber(normalizedPhone).username
                }

                if (!username.isNullOrBlank()) {
                    android.util.Log.d("DELETE_CONTACT", "Deleting from Firebase: username=$username")
                    firebaseRepo.deleteContactByUsername(username)
                }
            } catch (e: Exception) {
                android.util.Log.e("DELETE_CONTACT", "Firebase delete failed", e)
                firebaseFailed = true
            } finally {
                android.util.Log.d("DELETE_CONTACT", "Deleting from local DB: id=${contact.id}, phone=${contact.phoneNumber}")
                repository.deleteContact(contact)
                _uiMessage.value = if (firebaseFailed) {
                    "Contact deleted locally; sync pending"
                } else {
                    "Contact deleted"
                }
            }
        }
    }

    fun blockContact(contact: Contact) {
        viewModelScope.launch {
            var firebaseFailed = false
            try {
                // Update local database
                repository.updateContactLabel(contact.id, ContactLabel.BLACK.toString())
                
                // Update Firebase - need to get the actual username from phone number
                try {
                    val username = if (contact.phoneNumber == "VOIP_USER") {
                        contact.displayName // For VOIP contacts, displayName is the username
                    } else {
                        // For phone contacts, look up the username from the phone number
                        val normalizedPhone = if (contact.phoneNumber.startsWith("+65")) {
                            contact.phoneNumber
                        } else {
                            "+65${contact.phoneNumber}"
                        }
                        try {
                            phoneLookupService.getUserByPhoneNumber(normalizedPhone).username
                        } catch (e: Exception) {
                            android.util.Log.e("BLOCK_CONTACT", "Phone lookup failed for $normalizedPhone", e)
                            null
                        }
                    }
                    
                    if (!username.isNullOrBlank()) {
                        android.util.Log.d("BLOCK_CONTACT", "Blocking contact with username: $username (phone: ${contact.phoneNumber})")
                        firebaseRepo.updateContactLabel(username, ContactLabel.BLACK)
                    } else {
                        android.util.Log.e("BLOCK_CONTACT", "Could not determine username for contact")
                        firebaseFailed = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BLOCK_CONTACT", "Firebase update failed", e)
                    firebaseFailed = true
                }
                
                _uiMessage.value = if (firebaseFailed) {
                    "Contact blocked locally; sync pending"
                } else {
                    "Contact has been blocked"
                }
            } catch (e: Exception) {
                _uiMessage.value = "Failed to block contact"
            }
        }
    }

    fun unblockContact(contact: Contact) {
        viewModelScope.launch {
            var firebaseFailed = false
            try {
                // Update local database
                repository.updateContactLabel(contact.id, ContactLabel.WHITE.toString())
                
                // Update Firebase - need to get the actual username from phone number
                try {
                    val username = if (contact.phoneNumber == "VOIP_USER") {
                        contact.displayName // For VOIP contacts, displayName is the username
                    } else {
                        // For phone contacts, look up the username from the phone number
                        val normalizedPhone = if (contact.phoneNumber.startsWith("+65")) {
                            contact.phoneNumber
                        } else {
                            "+65${contact.phoneNumber}"
                        }
                        try {
                            phoneLookupService.getUserByPhoneNumber(normalizedPhone).username
                        } catch (e: Exception) {
                            android.util.Log.e("UNBLOCK_CONTACT", "Phone lookup failed for $normalizedPhone", e)
                            null
                        }
                    }
                    
                    if (!username.isNullOrBlank()) {
                        android.util.Log.d("UNBLOCK_CONTACT", "Unblocking contact with username: $username (phone: ${contact.phoneNumber})")
                        firebaseRepo.updateContactLabel(username, ContactLabel.WHITE)
                    } else {
                        android.util.Log.e("UNBLOCK_CONTACT", "Could not determine username for contact")
                        firebaseFailed = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("UNBLOCK_CONTACT", "Firebase update failed", e)
                    firebaseFailed = true
                }
                
                _uiMessage.value = if (firebaseFailed) {
                    "Contact unblocked locally; sync pending"
                } else {
                    "Contact has been unblocked"
                }
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
