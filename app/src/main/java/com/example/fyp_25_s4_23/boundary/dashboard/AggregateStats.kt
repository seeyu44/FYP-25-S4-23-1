package com.example.fyp_25_s4_23.boundary.dashboard

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Data class for aggregate stats from Firestore
 */
data class AggregateStats(
    val avgConfidence: Double = 0.0,
    val deepfakeRate: Double = 0.0,
    val totalScans: Int = 0,
    val updatedAt: String = ""
)

/**
 * Fetches aggregate stats for today from Firestore
 */
suspend fun fetchAggregateStatsDaily(): AggregateStats? {
    val db = FirebaseFirestore.getInstance()
    val todayId = java.time.LocalDate.now().toString()
    val doc = db.collection("aggregate_stats_daily").document(todayId).get().await()
    if (!doc.exists()) return null
    return AggregateStats(
        avgConfidence = doc.getDouble("avg_confidence") ?: 0.0,
        deepfakeRate = doc.getDouble("deepfake_rate") ?: 0.0,
        totalScans = (doc.getLong("total_scans") ?: 0L).toInt(),
        updatedAt = doc.get("updated_at")?.toString() ?: ""
    )
}
