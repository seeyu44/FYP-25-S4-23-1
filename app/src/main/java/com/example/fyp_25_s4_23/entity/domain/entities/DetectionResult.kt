package com.example.fyp_25_s4_23.entity.domain.entities

data class DetectionResult(
    val probability: Float,
    val isDeepfake: Boolean,
    val timestampSeconds: Long = System.currentTimeMillis() / 1000,
    val modelVersion: String = "0.0.1"
)

