package com.example.fyp_25_s4_23.data.mappers

import com.example.fyp_25_s4_23.data.entities.CallRecordEntity
import com.example.fyp_25_s4_23.domain.entities.CallMetadata
import com.example.fyp_25_s4_23.domain.entities.CallRecord
import com.example.fyp_25_s4_23.domain.entities.DetectionResult
import com.example.fyp_25_s4_23.domain.valueobjects.CallDirection
import com.example.fyp_25_s4_23.domain.valueobjects.CallStatus

fun CallRecordEntity.toDomain(): CallRecord {
    val metadata = CallMetadata(
        phoneNumber = phoneNumber,
        displayName = displayName,
        startTimeSeconds = startTimeSeconds,
        endTimeSeconds = endTimeSeconds,
        direction = runCatching { CallDirection.valueOf(direction) }.getOrDefault(CallDirection.UNKNOWN)
    )
    val detection = DetectionResult(
        probability = probability,
        isDeepfake = probability >= 0.7f,
        modelVersion = modelVersion ?: "unknown"
    )
    return CallRecord(
        id = id,
        metadata = metadata,
        status = runCatching { CallStatus.valueOf(status) }.getOrDefault(CallStatus.UNKNOWN),
        detections = listOf(detection)
    )
}

fun CallRecord.toEntity(): CallRecordEntity {
    return CallRecordEntity(
        id = id,
        phoneNumber = metadata.phoneNumber,
        displayName = metadata.displayName,
        startTimeSeconds = metadata.startTimeSeconds,
        endTimeSeconds = metadata.endTimeSeconds,
        direction = metadata.direction.name,
        status = status.name,
        probability = detections.lastOrNull()?.probability ?: 0f,
        modelVersion = detections.lastOrNull()?.modelVersion
    )
}

