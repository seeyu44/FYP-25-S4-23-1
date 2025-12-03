package com.example.fyp_25_s4_23.domain.entities

import com.example.fyp_25_s4_23.domain.valueobjects.AlertAction
import com.example.fyp_25_s4_23.domain.valueobjects.AlertSeverity

data class UserSettings(
    val threshold: Float = 0.7f,
    val allowBackgroundMonitoring: Boolean = false,
    val analyticsConsent: Boolean = false,
    val realTimeDetectionEnabled: Boolean = true,
    val preferredAlertSeverity: AlertSeverity = AlertSeverity.WARNING,
    val defaultAlertActions: Set<AlertAction> = setOf(AlertAction.NOTIFIED_USER),
    val autoBlockUnknownNumbers: Boolean = false,
    val autoBlockRepeatOffenders: Boolean = false
)
