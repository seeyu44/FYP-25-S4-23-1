package com.example.fyp_25_s4_23.domain.entities

/**
 * Describes the ML model used for on-device inference.
 */
data class ModelInfo(
    val version: String,
    val source: String,
    val threshold: Float,
    val description: String? = null
)

