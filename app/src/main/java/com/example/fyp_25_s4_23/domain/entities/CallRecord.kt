package com.example.fyp_25_s4_23.domain.entities

import com.example.fyp_25_s4_23.domain.valueobjects.CallStatus

/**
 * Aggregates metadata and detection history for a single call.
 */
data class CallRecord(
    val id: String,
    val metadata: CallMetadata,
    val status: CallStatus = CallStatus.UNKNOWN,
    val detections: List<DetectionResult> = emptyList(),
    val notes: String? = null,
    val createdMillis: Long = System.currentTimeMillis(),
    val updatedMillis: Long = System.currentTimeMillis()
) {
    val lastDetection: DetectionResult?
        get() = detections.lastOrNull()

    fun appendDetection(result: DetectionResult): CallRecord = copy(
        detections = detections + result,
        updatedMillis = System.currentTimeMillis()
    )
}

