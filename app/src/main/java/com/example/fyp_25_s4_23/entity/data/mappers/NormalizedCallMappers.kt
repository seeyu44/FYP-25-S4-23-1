package com.example.fyp_25_s4_23.entity.data.mappers

import com.example.fyp_25_s4_23.entity.data.dao.CompleteCallRecord
import com.example.fyp_25_s4_23.entity.data.entities.CallEntity
import com.example.fyp_25_s4_23.entity.data.entities.CallMetadataEntity
import com.example.fyp_25_s4_23.entity.data.entities.DetectionResultEntity
import com.example.fyp_25_s4_23.entity.domain.entities.CallMetadata
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.DetectionResult
import com.example.fyp_25_s4_23.entity.domain.valueobjects.CallDirection
import com.example.fyp_25_s4_23.entity.domain.valueobjects.CallStatus
import java.util.UUID

/**
 * Maps CompleteCallRecord (joined entity data) to CallRecord domain object
 */
fun CompleteCallRecord.toDomain(): CallRecord {
    val metadata = CallMetadata(
        phoneNumber = this.metadata?.phoneNumber ?: "Unknown",
        displayName = this.metadata?.displayName,
        startTimeMillis = this.metadata?.startTimeMillis ?: call.createdMillis,
        endTimeMillis = this.metadata?.endTimeMillis,
        direction = this.metadata?.direction?.let { 
            runCatching { CallDirection.valueOf(it) }.getOrDefault(CallDirection.UNKNOWN) 
        } ?: CallDirection.UNKNOWN
    )
    
    val detectionResults = this.detections.map { it.toDomain() }
    
    return CallRecord(
        id = call.id,
        metadata = metadata,
        status = runCatching { CallStatus.valueOf(call.status) }.getOrDefault(CallStatus.UNKNOWN),
        detections = detectionResults,
        notes = call.notes,
        createdMillis = call.createdMillis,
        updatedMillis = call.updatedMillis
    )
}

/**
 * Maps CallRecord domain object to normalized entities (Call + Metadata + Detections)
 */
fun CallRecord.toEntities(userId: Long? = null): Triple<CallEntity, CallMetadataEntity, List<DetectionResultEntity>> {
    val callEntity = CallEntity(
        id = id,
        userId = userId,
        status = status.name,
        createdMillis = createdMillis,
        updatedMillis = updatedMillis,
        notes = notes
    )
    
    val metadataEntity = CallMetadataEntity(
        callId = id,
        phoneNumber = metadata.phoneNumber,
        displayName = metadata.displayName,
        startTimeMillis = metadata.startTimeMillis,
        endTimeMillis = metadata.endTimeMillis,
        direction = metadata.direction.name,
        durationSeconds = metadata.endTimeMillis?.let { 
            ((it - metadata.startTimeMillis) / 1000).toInt() 
        }
    )
    
    val detectionEntities = detections.mapIndexed { index, detection ->
        DetectionResultEntity(
            id = "${id}_detection_${index + 1}",
            callId = id,
            probability = detection.probability,
            isDeepfake = detection.isDeepfake,
            timestampMillis = detection.timestampMillis,
            modelVersion = detection.modelVersion,
            confidenceLevel = when {
                detection.probability >= 0.8f -> "HIGH"
                detection.probability >= 0.5f -> "MEDIUM"
                else -> "LOW"
            }
        )
    }
    
    return Triple(callEntity, metadataEntity, detectionEntities)
}

/**
 * Maps DetectionResultEntity to DetectionResult domain object
 */
fun DetectionResultEntity.toDomain(): DetectionResult {
    return DetectionResult(
        probability = probability,
        isDeepfake = isDeepfake,
        timestampMillis = timestampMillis,
        modelVersion = modelVersion
    )
}

/**
 * Maps DetectionResult domain object to DetectionResultEntity
 */
fun DetectionResult.toEntity(callId: String, detectionId: String = UUID.randomUUID().toString()): DetectionResultEntity {
    return DetectionResultEntity(
        id = detectionId,
        callId = callId,
        probability = probability,
        isDeepfake = isDeepfake,
        timestampMillis = timestampMillis,
        modelVersion = modelVersion,
        confidenceLevel = when {
            probability >= 0.8f -> "HIGH"
            probability >= 0.5f -> "MEDIUM"
            else -> "LOW"
        }
    )
}
