package com.example.fyp_25_s4_23.util

import android.util.Log
import com.example.fyp_25_s4_23.entity.domain.valueobjects.UserRole

fun mapUserRole(role: String): UserRole {
    val upperRole = role.uppercase()
    Log.d("UserRoleMapper", "Mapping role: '$role' -> uppercase: '$upperRole'")
    
    val mappedRole = when (upperRole) {
        "ADMIN" -> UserRole.ADMIN
        "REGISTERED" -> UserRole.REGISTERED
        else -> UserRole.REGISTERED
    }
    
    Log.d("UserRoleMapper", "Mapped to: $mappedRole")
    return mappedRole
}