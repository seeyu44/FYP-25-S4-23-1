package com.example.fyp_25_s4_23.domain.entities

/**
 * Represents a block of extracted features ready for ML inference.
 */
data class FeatureVector(
    val frameCount: Int,
    val featureDimension: Int,
    val sampleRate: Int,
    val values: FloatArray,
    val extractionMillis: Long = System.currentTimeMillis()
) {
    init {
        val expected = frameCount * featureDimension
        require(values.size == expected) {
            "FeatureVector values size $expected does not match actual ${values.size}"
        }
    }
}

