package com.example.fyp_25_s4_23.entity.domain.entities

/**
 * Represents an audit log entry for admin actions.
 */
data class AuditLog(
    val id: String = "",
    val action: String, // e.g., "USER_DISABLED", "USER_DELETED", "REVIEW_DELETED", etc.
    val actor: String, // Admin who performed the action
    val target: String, // User ID or other target identifier
    val timestamp: Long = System.currentTimeMillis() / 1000,
    val details: Map<String, Any> = emptyMap() // Additional context about the action
)
