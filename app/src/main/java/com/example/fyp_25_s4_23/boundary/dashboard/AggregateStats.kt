package com.example.fyp_25_s4_23.boundary.dashboard

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class for aggregate stats from Firestore
 */
data class AggregateStats(
    val avgConfidence: Double = 0.0,
    val deepfakeRate: Double = 0.0,
    val totalScans: Int = 0,
    val updatedAt: String = ""
)

private fun formatUpdatedAt(value: Any?): String {
    if (value == null) return ""

    val date = when (value) {
        is Timestamp -> value.toDate()
        is Date -> value
        is Number -> {
            val raw = value.toLong()
            val millis = if (raw < 1_000_000_000_000L) raw * 1000 else raw
            Date(millis)
        }
        is String -> {
            val timestampRegex = Regex("""Timestamp\(seconds=(\d+),\s*nanoseconds=(\d+)\)""")
            val match = timestampRegex.find(value)
            if (match != null) {
                val seconds = match.groupValues[1].toLongOrNull()
                val nanos = match.groupValues[2].toLongOrNull() ?: 0L
                if (seconds != null) {
                    Date(seconds * 1000 + nanos / 1_000_000)
                } else {
                    return value
                }
            } else {
                return value
            }
        }
        else -> return value.toString()
    }

    return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(date)
}

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
        updatedAt = formatUpdatedAt(doc.get("updated_at"))
    )
}
