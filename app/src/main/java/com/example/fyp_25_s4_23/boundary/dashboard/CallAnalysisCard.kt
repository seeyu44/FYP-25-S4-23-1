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
import androidx.compose.ui.graphics.Color
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.example.fyp_25_s4_23.boundary.dashboard.AggregateStats
import com.example.fyp_25_s4_23.boundary.dashboard.fetchAggregateStatsDaily
import kotlin.math.roundToInt

/**
 * Displays call analysis metrics for admin users.
 * Shows statistics about call processing and AI model performance.
 */
@Composable
fun CallAnalysisCard(callRecords: List<CallRecord>) {
    val aggregateStats = remember { mutableStateOf<AggregateStats?>(null) }
    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val stats = fetchAggregateStatsDaily()
            aggregateStats.value = stats
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Call Analytics", style = MaterialTheme.typography.titleMedium)
            Text(
                "AI Model Performance & Daily Stats",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Divider()

            val stats = aggregateStats.value
            MetricRow(
                label = "Avg. Confidence Score",
                value = stats?.avgConfidence?.let { String.format("%.2f%%", it * 100) } ?: "Loading...",
                description = "Average model confidence for scans"
            )
            Spacer(modifier = Modifier.height(8.dp))
            MetricRow(
                label = "Deepfake Rate",
                value = stats?.deepfakeRate?.let { String.format("%.2f%%", it * 100) } ?: "Loading...",
                description = "Percentage of scans flagged as deepfake"
            )
            Spacer(modifier = Modifier.height(8.dp))
            MetricRow(
                label = "Total Scans",
                value = stats?.totalScans?.toString() ?: "Loading...",
                description = "Total number of scans"
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (stats?.updatedAt?.isNotBlank() == true) {
                Text("Last Updated: ${stats.updatedAt}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
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
        if (detection != null && record.metadata.endTimeSeconds != null) {
            // Analysis time is roughly from when the call ended to when detection was recorded
            // For now, we use detection timestamp - call start time as proxy for processing time
            (detection.timestampSeconds - record.metadata.startTimeSeconds).toDouble()
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
