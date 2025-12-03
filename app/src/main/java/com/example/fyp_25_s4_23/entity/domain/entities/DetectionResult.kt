package com.example.fyp_25_s4_23.domain.entities

data class DetectionResult(
    val probability: Float,
    val isDeepfake: Boolean,
    val timestampMillis: Long = System.currentTimeMillis(),
    val modelVersion: String = "0.0.1"
)

