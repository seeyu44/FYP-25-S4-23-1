package com.example.fyp_25_s4_23.boundary.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import kotlin.math.roundToInt

/**
 * Displays call analysis metrics for admin users.
 * Shows statistics about call processing and AI model performance.
 */
@Composable
fun CallAnalysisCard(callRecords: List<CallRecord>) {
    // Calculate metrics
    val averageAnalysisTime = calculateAverageAnalysisTime(callRecords)
    val totalCallsAnalyzed = callRecords.size
    val averageProbability = calculateAverageProbability(callRecords)
    val flaggedCallsCount = callRecords.count { record ->
        record.lastDetection?.isDeepfake == true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Call Analysis", style = MaterialTheme.typography.titleMedium)
            Text(
                "AI Model Performance & Operational Metrics",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Divider()

            // Metric: Average Analysis Time
            MetricRow(
                label = "Avg. Analysis Time",
                value = if (averageAnalysisTime >= 0) "${averageAnalysisTime.roundToInt()} ms" else "N/A",
                description = "Average time to analyze a single call"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metric: Total Calls Analyzed
            MetricRow(
                label = "Total Calls Analyzed",
                value = totalCallsAnalyzed.toString(),
                description = "Total number of calls processed"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metric: Average Detection Probability
            MetricRow(
                label = "Avg. Detection Probability",
                value = if (averageProbability >= 0) "${(averageProbability * 100).roundToInt()}%" else "N/A",
                description = "Average deepfake detection probability"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metric: Flagged Calls
            MetricRow(
                label = "Flagged Calls",
                value = flaggedCallsCount.toString(),
                description = "Calls flagged as deepfakes",
                isAlert = flaggedCallsCount > 0
            )
        }
    }
}

/**
 * Displays a single metric row with label, value, and description.
 */
@Composable
private fun MetricRow(
    label: String,
    value: String,
    description: String,
    isAlert: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Calculates the average analysis time per call based on detection timestamps.
 * Returns -1.0 if no data is available.
 */
private fun calculateAverageAnalysisTime(callRecords: List<CallRecord>): Double {
    if (callRecords.isEmpty()) return -1.0

    val analysisTimes = callRecords.mapNotNull { record ->
        // Calculate analysis time as the difference between detection time and call start time
        val detection = record.lastDetection
        if (detection != null && record.metadata.endTimeMillis != null) {
            // Analysis time is roughly from when the call ended to when detection was recorded
            // For now, we use detection timestamp - call start time as proxy for processing time
            (detection.timestampMillis - record.metadata.startTimeMillis).toDouble()
        } else {
            null
        }
    }

    return if (analysisTimes.isNotEmpty()) {
        analysisTimes.average()
    } else {
        -1.0
    }
}

/**
 * Calculates the average detection probability across all call records.
 * Returns -1.0 if no data is available.
 */
private fun calculateAverageProbability(callRecords: List<CallRecord>): Double {
    if (callRecords.isEmpty()) return -1.0

    val probabilities = callRecords.mapNotNull { record ->
        record.lastDetection?.probability?.toDouble()
    }

    return if (probabilities.isNotEmpty()) {
        probabilities.average()
    } else {
        -1.0
    }
}
