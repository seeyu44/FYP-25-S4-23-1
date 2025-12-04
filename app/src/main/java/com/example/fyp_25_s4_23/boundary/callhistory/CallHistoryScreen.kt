package com.example.fyp_25_s4_23.boundary.callhistory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun CallHistoryScreen(
    user: UserAccount,
    callRecords: List<CallRecord>,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(
                text = "Call History for ${user.displayName}",
                style = MaterialTheme.typography.titleLarge
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }

        if (callRecords.isEmpty()) {
            Text(
                text = "No calls recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            Text(
                text = "${callRecords.size} total calls",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(callRecords) { record ->
                    CallHistoryCard(record = record)
                }
            }
        }
    }
}

@Composable
fun CallHistoryCard(record: CallRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Contact info
            Text(
                text = record.metadata.displayName ?: "Unknown",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = record.metadata.phoneNumber,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Call timestamp
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
            val startTime = dateFormat.format(Date(record.metadata.startTimeMillis))
            Text(text = "Time: $startTime", style = MaterialTheme.typography.bodyMedium)

            // Call duration
            val duration = record.metadata.durationMillis
            if (duration != null) {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
                Text(
                    text = "Duration: ${minutes}m ${seconds}s",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(text = "Duration: N/A", style = MaterialTheme.typography.bodyMedium)
            }

            // Call status
            Text(
                text = "Status: ${record.status.name}",
                style = MaterialTheme.typography.bodyMedium
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Detection result
            val detection = record.lastDetection
            if (detection != null) {
                val isDeepfake = detection.isDeepfake
                val confidence = (detection.probability * 100).toInt()

                Text(
                    text = if (isDeepfake) "⚠️ Deepfake Detected" else "✅ Real Call",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDeepfake) Color.Red else Color.Green
                )
                Text(
                    text = "Confidence Score: $confidence%",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (detection.modelVersion != null) {
                    Text(
                        text = "Model: ${detection.modelVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            } else {
                Text(
                    text = "No detection result",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            // Notes (if any)
            if (!record.notes.isNullOrBlank()) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Notes: ${record.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }
        }
    }
}
