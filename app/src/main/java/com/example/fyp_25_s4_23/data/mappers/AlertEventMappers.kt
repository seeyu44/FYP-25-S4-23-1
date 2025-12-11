package com.example.fyp_25_s4_23.data.mappers

import com.example.fyp_25_s4_23.data.entities.AlertEventEntity
import com.example.fyp_25_s4_23.domain.entities.AlertEvent
import com.example.fyp_25_s4_23.domain.valueobjects.AlertAction
import com.example.fyp_25_s4_23.domain.valueobjects.AlertSeverity

fun AlertEventEntity.toDomain(): AlertEvent = AlertEvent(
    id = id,
    callId = callId,
    triggerSeconds = triggerSeconds,
    severity = runCatching { AlertSeverity.valueOf(severity) }.getOrDefault(AlertSeverity.WARNING),
    probability = probability,
    message = message,
    actionsTaken = actions.split(',').filter { it.isNotBlank() }
        .mapNotNull { runCatching { AlertAction.valueOf(it) }.getOrNull() }
        .toSet(),
    acknowledged = acknowledged,
    acknowledgedSeconds = acknowledgedSeconds
)

fun AlertEvent.toEntity(detectionId: String? = null): AlertEventEntity = AlertEventEntity(
    id = id,
    callId = callId,
    detectionId = detectionId,
    triggerSeconds = triggerSeconds,
    severity = severity.name,
    probability = probability,
    message = message,
    actions = actionsTaken.joinToString(",") { it.name },
    acknowledged = acknowledged,
    acknowledgedSeconds = acknowledgedSeconds
)

