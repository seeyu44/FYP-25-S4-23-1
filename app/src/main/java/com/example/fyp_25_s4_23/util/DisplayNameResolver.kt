package com.example.fyp_25_s4_23.util

import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository

/**
 * Utility to resolve display names from user IDs
 * Prioritizes local saved contacts over Firebase data
 */
object DisplayNameResolver {
    private fun normalizePhoneVariants(phone: String?): List<String> {
        if (phone.isNullOrBlank()) return emptyList()
        val trimmed = phone.trim().replace(" ", "")
        val digits = trimmed.replace("+", "")

        val normalized = if (digits.length == 8) {
            "+65$digits"
        } else if (trimmed.startsWith("+") && digits.length >= 8) {
            trimmed
        } else {
            trimmed
        }

        return listOf(trimmed, normalized, digits).distinct()
    }
    
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
        currentUserId: String,
        userId: String,
        fallbackName: String? = null,
        fallbackPhone: String? = null
    ): String {
        // Try to find contact by username (Firebase UID)
        val contactByUsername = contactRepository.getContactByUsername(currentUserId, userId)
        if (contactByUsername != null && !contactByUsername.displayName.isNullOrBlank()) {
            return contactByUsername.displayName
        }
        
        // Try to find contact by phone number if provided
        val phoneVariants = normalizePhoneVariants(fallbackPhone)
        for (phoneVariant in phoneVariants) {
            val contactByPhone = contactRepository.getContactByPhoneNumber(currentUserId, phoneVariant)
            if (contactByPhone != null && !contactByPhone.displayName.isNullOrBlank()) {
                return contactByPhone.displayName
            }
        }
        
        // Use contact's phone number if available
        if (contactByUsername?.phoneNumber?.isNotBlank() == true) {
            return contactByUsername.phoneNumber
        }
        
        // Use fallback phone number if available
        if (phoneVariants.isNotEmpty()) {
            return phoneVariants.first()
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
        currentUserId: String,
        userId: String,
        fallbackPhone: String? = null
    ): String? {
        // Try to find contact by username (Firebase UID)
        val contactByUsername = contactRepository.getContactByUsername(currentUserId, userId)
        if (contactByUsername?.phoneNumber?.isNotBlank() == true) {
            return contactByUsername.phoneNumber
        }
        
        // Try to find contact by phone number if provided
        val phoneVariants = normalizePhoneVariants(fallbackPhone)
        for (phoneVariant in phoneVariants) {
            val contactByPhone = contactRepository.getContactByPhoneNumber(currentUserId, phoneVariant)
            if (contactByPhone?.phoneNumber?.isNotBlank() == true) {
                return contactByPhone.phoneNumber
            }
        }
        
        // Return fallback phone number
        return phoneVariants.firstOrNull()
    }
}
