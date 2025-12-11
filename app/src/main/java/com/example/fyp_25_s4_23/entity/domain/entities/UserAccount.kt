package com.example.fyp_25_s4_23.entity.domain.entities

import com.example.fyp_25_s4_23.entity.domain.valueobjects.UserRole

data class UserAccount(
    val id: Long,
    val username: String,
    val displayName: String,
    val role: UserRole,
    val createdAtSeconds: Long
)

