package com.example.fyp_25_s4_23.entity.data.mappers

import com.example.fyp_25_s4_23.entity.data.entities.AlertEventEntity
import com.example.fyp_25_s4_23.entity.domain.entities.AlertEvent
import com.example.fyp_25_s4_23.entity.domain.valueobjects.AlertAction
import com.example.fyp_25_s4_23.entity.domain.valueobjects.AlertSeverity

fun AlertEventEntity.toDomain(): AlertEvent = AlertEvent(
    id = id,
    callId = callId,
    triggerMillis = triggerMillis,
    severity = runCatching { AlertSeverity.valueOf(severity) }.getOrDefault(AlertSeverity.WARNING),
    probability = probability,
    message = message,
    actionsTaken = actions.split(',').filter { it.isNotBlank() }
        .mapNotNull { runCatching { AlertAction.valueOf(it) }.getOrNull() }
        .toSet(),
    acknowledged = acknowledged,
    acknowledgedMillis = acknowledgedMillis
)

fun AlertEvent.toEntity(detectionId: String? = null): AlertEventEntity = AlertEventEntity(
    id = id,
    callId = callId,
    detectionId = detectionId,
    triggerMillis = triggerMillis,
    severity = severity.name,
    probability = probability,
    message = message,
    actions = actionsTaken.joinToString(",") { it.name },
    acknowledged = acknowledged,
    acknowledgedMillis = acknowledgedMillis
)

