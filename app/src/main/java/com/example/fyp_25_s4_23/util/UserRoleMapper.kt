package com.example.fyp_25_s4_23.util

import com.example.fyp_25_s4_23.entity.domain.valueobjects.UserRole

fun mapUserRole(role: String): UserRole {
    return when (role.lowercase()) {
        "ADMIN" -> UserRole.ADMIN
        "REGISTERED" -> UserRole.REGISTERED
        else -> UserRole.REGISTERED
    }
}