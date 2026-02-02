package com.example.fyp_25_s4_23.util

import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository

/**
 * Utility to resolve display names from user IDs
 * Prioritizes local saved contacts over Firebase data
 */
object DisplayNameResolver {
    
    /**
     * Resolve display name for a user ID
     * Priority:
     * 1. Local contact's saved display name
     * 2. Local contact's phone number
     * 3. Fallback display name (from Firebase)
     * 4. User ID itself
     */
    suspend fun resolveDisplayName(
        contactRepository: ContactRepository,
        userId: String,
        fallbackName: String? = null,
        fallbackPhone: String? = null
    ): String {
        // Try to find contact by username (Firebase UID)
        val contactByUsername = contactRepository.getContactByUsername(userId)
        if (contactByUsername != null && !contactByUsername.displayName.isNullOrBlank()) {
            return contactByUsername.displayName
        }
        
        // Try to find contact by phone number if provided
        if (fallbackPhone != null) {
            val contactByPhone = contactRepository.getContactByPhoneNumber(fallbackPhone)
            if (contactByPhone != null && !contactByPhone.displayName.isNullOrBlank()) {
                return contactByPhone.displayName
            }
        }
        
        // Use contact's phone number if available
        if (contactByUsername?.phoneNumber?.isNotBlank() == true) {
            return contactByUsername.phoneNumber
        }
        
        // Use fallback phone number if available
        if (!fallbackPhone.isNullOrBlank()) {
            return fallbackPhone
        }
        
        // Use fallback name if available
        if (!fallbackName.isNullOrBlank()) {
            return fallbackName
        }
        
        // Last resort: return the userId
        return userId
    }
    
    /**
     * Resolve phone number for a user ID
     * Returns the phone number from local contacts or fallback
     */
    suspend fun resolvePhoneNumber(
        contactRepository: ContactRepository,
        userId: String,
        fallbackPhone: String? = null
    ): String? {
        // Try to find contact by username (Firebase UID)
        val contactByUsername = contactRepository.getContactByUsername(userId)
        if (contactByUsername?.phoneNumber?.isNotBlank() == true) {
            return contactByUsername.phoneNumber
        }
        
        // Try to find contact by phone number if provided
        if (fallbackPhone != null) {
            val contactByPhone = contactRepository.getContactByPhoneNumber(fallbackPhone)
            if (contactByPhone?.phoneNumber?.isNotBlank() == true) {
                return contactByPhone.phoneNumber
            }
        }
        
        // Return fallback phone number
        return fallbackPhone
    }
}
