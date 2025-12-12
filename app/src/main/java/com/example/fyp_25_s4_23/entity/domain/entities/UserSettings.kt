package com.example.fyp_25_s4_23.entity.domain.entities

import com.example.fyp_25_s4_23.entity.domain.valueobjects.AlertAction
import com.example.fyp_25_s4_23.entity.domain.valueobjects.AlertSeverity

data class UserSettings(
    val realTimeDetectionEnabled: Boolean = true,
    val detectionThreshold: Double = 0.7
)
