package com.example.fyp_25_s4_23.entity.domain.entities

import com.example.fyp_25_s4_23.entity.domain.valueobjects.DetectionSessionStatus

/**
 * Represents a continuous inference session tied to a call.
 */
data class DetectionSession(
    val sessionId: String,
    val callId: String,
    val startedMillis: Long = System.currentTimeMillis(),
    val endedMillis: Long? = null,
    val status: DetectionSessionStatus = DetectionSessionStatus.RUNNING,
    val probabilities: List<Float> = emptyList(),
    val finalResult: DetectionResult? = null,
    val modelInfo: ModelInfo? = null
) {
    val durationMillis: Long?
        get() = endedMillis?.let { end ->
            val duration = end - startedMillis
            if (duration >= 0) duration else null
        }
}

