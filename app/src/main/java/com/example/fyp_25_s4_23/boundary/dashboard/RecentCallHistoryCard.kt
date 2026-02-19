package com.example.fyp_25_s4_23.boundary.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.domain.entities.FirebaseCallRecord
import kotlin.math.roundToInt

/**
 * Displays recent call history with clickable card to navigate to full call history.
 */
@Composable
fun RecentCallHistoryCard(
    firebaseCalls: List<FirebaseCallRecord>,
    onViewFullHistory: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable(onClick = onViewFullHistory),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Call History",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (firebaseCalls.isEmpty()) {
                Text(
                    text = "No call history yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                val displayCalls = firebaseCalls.take(4)
                displayCalls.forEachIndexed { index, record ->
                    CallHistoryItem(
                        record = record,
                        isLast = index == displayCalls.size - 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // View full history button
            TextButton(
                onClick = onViewFullHistory,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("View Full History")
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "View Full History",
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CallHistoryItem(
    record: FirebaseCallRecord,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (record.isOutgoing) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Phone,
            contentDescription = if (record.isOutgoing) "Outgoing" else "Incoming",
            tint = if (record.isOutgoing) Color.Blue else Color.Green,
            modifier = Modifier
                .size(24.dp)
                .padding(end = 8.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.getContactName(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = record.getCreatedAtFormatted(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            if (record.isDeepfake != null) {
                val isDeepfakeDetected = record.isDeepfake == true
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isDeepfakeDetected) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = if (isDeepfakeDetected) "Deepfake detected" else "Real voice",
                        tint = if (isDeepfakeDetected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isDeepfakeDetected) "Deepfake detected" else "Real voice",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDeepfakeDetected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        if (record.isCompleted()) {
            val showConfidence = !record.isOutgoing && record.detectionScore != null
            val showDuration = record.isOutgoing || !record.isOutgoing
            if (showDuration) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total time: ${record.getEffectiveDurationString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showConfidence) {
                        val confidencePercent = (record.detectionScore!! * 100).roundToInt()
                        Text(
                            text = "Confidence: $confidencePercent%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    }
}
