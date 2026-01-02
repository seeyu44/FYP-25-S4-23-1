package com.example.fyp_25_s4_23.data.remote.dto

data class UserProfileResponse(
    val id: String,
    val email: String,
    val username: String,
    val display_name: String,
    val role: String,
    val plan_tier: String,
    val verified: Boolean,
    val created_at_seconds: Long
)