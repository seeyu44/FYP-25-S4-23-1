package com.example.fyp_25_s4_23.entity.data.repositories

import com.example.fyp_25_s4_23.entity.domain.entities.DetectionResult

/**
 * Simple in-memory repository placeholder for detections.
 */
class DetectionsRepo {
    private val items = mutableListOf<DetectionResult>()
    fun add(result: DetectionResult) { items += result }
    fun all(): List<DetectionResult> = items.toList()
}

