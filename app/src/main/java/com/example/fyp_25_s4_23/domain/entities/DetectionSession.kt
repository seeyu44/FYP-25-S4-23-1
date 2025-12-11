package com.example.fyp_25_s4_23.domain.entities

import com.example.fyp_25_s4_23.domain.valueobjects.DetectionSessionStatus

/**
 * Represents a continuous inference session tied to a call.
 */
data class DetectionSession(
    val sessionId: String,
    val callId: String,
    val startedSeconds: Long = System.currentTimeMillis() / 1000,
    val endedSeconds: Long? = null,
    val status: DetectionSessionStatus = DetectionSessionStatus.RUNNING,
    val probabilities: List<Float> = emptyList(),
    val finalResult: DetectionResult? = null,
    val modelInfo: ModelInfo? = null
) {
    val durationSeconds: Long?
        get() = endedSeconds?.let { end ->
            val duration = end - startedSeconds
            if (duration >= 0) duration else null
        }
}

