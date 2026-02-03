package com.example.fyp_25_s4_23.boundary.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.domain.entities.FirebaseCallRecord
import kotlin.math.roundToInt

/**
 * Displays account analysis metrics showing number of calls, average call time, and confidence score.
 * Clicking on this card navigates to the daily/weekly summary page.
 */
@Composable
fun AccountAnalysisCard(
    firebaseCalls: List<FirebaseCallRecord>,
    onClick: () -> Unit
) {
    // Filter only incoming calls (where user is callee, not caller)
    val incomingCalls = firebaseCalls.filter { !it.isCaller }
    
    val totalCalls = incomingCalls.size
    
    // Calculate average call time in minutes (only for completed incoming calls)
    val completedCalls = incomingCalls.filter { it.isCompleted() }
    val avgCallTime = if (completedCalls.isNotEmpty()) {
        val totalMinutes = completedCalls.sumOf { call ->
            (call.duration / 60.0)
        }
        (totalMinutes / completedCalls.size.toDouble()).roundToInt()
    } else {
        0
    }
    
    // Calculate average confidence score (mock for now - can be enhanced with actual confidence data)
    val avgConfidence = if (incomingCalls.isNotEmpty()) {
        // This is a placeholder - you would calculate actual confidence from call analysis
        val mockConfidence = (75..95).random()
        mockConfidence
    } else {
        0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Account Analysis",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // No of Calls Received
                MetricColumn(
                    label = "No of Calls Received",
                    value = totalCalls.toString()
                )

                Divider(
                    modifier = Modifier
                        .height(60.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )

                // Avg Call Time
                MetricColumn(
                    label = "Avg Call Time",
                    value = "${avgCallTime}MIN"
                )

                Divider(
                    modifier = Modifier
                        .height(60.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )

                // Avg Confidence Score
                MetricColumn(
                    label = "Avg Confidence Score",
                    value = "$avgConfidence%"
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}
