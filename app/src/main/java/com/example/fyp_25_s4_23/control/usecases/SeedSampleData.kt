package com.example.fyp_25_s4_23.control.usecases

import com.example.fyp_25_s4_23.entity.data.repositories.AlertRepository
import com.example.fyp_25_s4_23.entity.data.repositories.CallRepository
import com.example.fyp_25_s4_23.entity.domain.entities.AlertEvent
import com.example.fyp_25_s4_23.entity.domain.entities.CallMetadata
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.DetectionResult
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import com.example.fyp_25_s4_23.entity.domain.valueobjects.AlertAction
import com.example.fyp_25_s4_23.entity.domain.valueobjects.AlertSeverity
import com.example.fyp_25_s4_23.entity.domain.valueobjects.CallDirection
import com.example.fyp_25_s4_23.entity.domain.valueobjects.CallStatus
import java.util.UUID

class SeedSampleData(
    private val callRepository: CallRepository,
    private val alertRepository: AlertRepository
) {
    suspend operator fun invoke(requestedBy: UserAccount) {
        val callId = UUID.randomUUID().toString()
        val probability = 0.82f
        val callRecord = CallRecord(
            id = callId,
            metadata = CallMetadata(
                phoneNumber = "+1 222 333 4444",
                displayName = "Unknown",
                startTimeMillis = System.currentTimeMillis() - 120_000,
                endTimeMillis = System.currentTimeMillis() - 60_000,
                direction = CallDirection.INCOMING
            ),
            status = CallStatus.COMPLETED,
            detections = listOf(
                DetectionResult(
                    probability = probability,
                    isDeepfake = true,
                    modelVersion = "0.0.1"
                )
            )
        )
        callRepository.upsert(callRecord)

        val alert = AlertEvent(
            id = UUID.randomUUID().toString(),
            callId = callId,
            probability = probability,
            message = "Potential deepfake detected on call",
            severity = AlertSeverity.CRITICAL,
            actionsTaken = setOf(AlertAction.NOTIFIED_USER, AlertAction.CALL_FLAGGED)
        )
        alertRepository.upsert(alert)
    }
}

