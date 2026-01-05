package com.example.fyp_25_s4_23.data.remote.dto

data class UserProfile(
    val uid: String,
    val email: String,
    val username: String,
    val displayName: String,
    val role: String,
    val planTier: String,
    val verified: Boolean,
    val createdAtSeconds: Long
)