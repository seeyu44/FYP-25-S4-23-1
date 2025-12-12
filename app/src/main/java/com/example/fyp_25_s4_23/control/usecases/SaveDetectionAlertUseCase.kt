package com.example.fyp_25_s4_23.control.usecases

import com.example.fyp_25_s4_23.data.repositories.AlertRepository
import com.example.fyp_25_s4_23.domain.entities.AlertEvent
import com.example.fyp_25_s4_23.domain.valueobjects.AlertAction
import com.example.fyp_25_s4_23.domain.valueobjects.AlertSeverity
import java.util.UUID

class SaveDetectionAlertUseCase(
    private val alertRepository: AlertRepository
) {
    suspend operator fun invoke(callId: String, probability: Float) {
        val severity = AlertSeverity.CRITICAL
        val message = "Deepfake probability ${(probability * 100).toInt()}% detected."
        val actions = setOf(AlertAction.NOTIFIED_USER)

        val alert = AlertEvent(
            id = UUID.randomUUID().toString(),
            callId = callId,
            triggerMillis = System.currentTimeMillis(),
            severity = severity,
            probability = probability,
            message = message,
            actionsTaken = actions,
            acknowledged = false
        )
        alertRepository.upsert(alert)
    }
}