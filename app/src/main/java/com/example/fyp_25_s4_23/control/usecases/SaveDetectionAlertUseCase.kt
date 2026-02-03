package com.example.fyp_25_s4_23.control.usecases

import com.example.fyp_25_s4_23.entity.data.repositories.AlertRepository
import com.example.fyp_25_s4_23.entity.domain.entities.AlertEvent
import com.example.fyp_25_s4_23.entity.domain.valueobjects.AlertAction
import com.example.fyp_25_s4_23.entity.domain.valueobjects.AlertSeverity
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
            triggerSeconds = System.currentTimeMillis() / 1000,
            severity = severity,
            probability = probability,
            message = message,
            actionsTaken = actions,
            acknowledged = false
        )
        
        try {
            alertRepository.upsert(alert)
        } catch (e: Exception) {
            // Silently catch DB errors (e.g., foreign key constraint if callId doesn't exist)
            // This happens when testing model with bundled audio (no active call)
            android.util.Log.w("SaveDetectionAlert", "Failed to save alert (callId may not exist): ${e.message}")
        }
    }
}