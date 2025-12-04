package com.example.fyp_25_s4_23.entity.domain.valueobjects

data class LocationTag(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val source: String? = null
)

