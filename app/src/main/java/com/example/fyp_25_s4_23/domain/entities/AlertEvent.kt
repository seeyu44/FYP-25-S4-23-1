package com.example.fyp_25_s4_23.domain.entities

import com.example.fyp_25_s4_23.domain.valueobjects.AlertAction
import com.example.fyp_25_s4_23.domain.valueobjects.AlertSeverity

/**
 * Captures an intervention or notification triggered by detection outcomes.
 */
data class AlertEvent(
    val id: String,
    val callId: String,
    val triggerSeconds: Long = System.currentTimeMillis() / 1000,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val probability: Float,
    val message: String,
    val actionsTaken: Set<AlertAction> = emptySet(),
    val acknowledged: Boolean = false,
    val acknowledgedSeconds: Long? = null
)

