package com.example.fyp_25_s4_23.domain.entities

data class UserSettings(
    val threshold: Float = 0.7f,
    val allowBackgroundMonitoring: Boolean = false,
    val analyticsConsent: Boolean = false
)

