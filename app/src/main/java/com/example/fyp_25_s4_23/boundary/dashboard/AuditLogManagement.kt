package com.example.fyp_25_s4_23.boundary.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fyp_25_s4_23.domain.entities.AuditLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Composable for displaying and filtering audit logs in the admin dashboard.
 */
@Composable
fun AuditLogManagement(
    auditLogs: List<AuditLog>,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    // Filter audit logs based on search query
    val filteredLogs = if (searchQuery.isBlank()) {
        auditLogs
    } else {
        auditLogs.filter { log ->
            log.action.contains(searchQuery, ignoreCase = true) ||
            log.actor.contains(searchQuery, ignoreCase = true) ||
            log.target.contains(searchQuery, ignoreCase = true) ||
            log.details.toString().contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "Audit Logs",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search logs (action, actor, target)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        // Results count
        Text(
            text = "Showing ${filteredLogs.size} of ${auditLogs.size} entries",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Audit logs list
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (auditLogs.isEmpty()) "No audit logs available" else "No matching audit logs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filteredLogs) { log ->
                    AuditLogCard(log)
                }
            }
        }
    }
}

/**
 * Card displaying a single audit log entry.
 */
@Composable
private fun AuditLogCard(log: AuditLog) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Action with color coding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = getActionColor(log.action),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(6.dp)
                ) {
                    Text(
                        text = log.action,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Timestamp
                Text(
                    text = formatTimestamp(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Actor and Target info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Admin: ${log.actor}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Column {
                    Text(
                        text = "Target: ${log.target}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Details if available
            if (log.details.isNotEmpty()) {
                Text(
                    text = "Details: ${log.details}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Gets a color for the action badge based on the action type.
 */
private fun getActionColor(action: String): Color {
    return when {
        action.contains("DELETE") -> Color(0xFFD32F2F) // Red for deletions
        action.contains("DISABLE") -> Color(0xFFF57C00) // Orange for disabling
        action.contains("ENABLE") -> Color(0xFF388E3C) // Green for enabling
        action.contains("CREATE") || action.contains("ADD") -> Color(0xFF1976D2) // Blue for creations
        else -> Color(0xFF757575) // Gray for other actions
    }
}

/**
 * Formats a Unix timestamp to a readable date and time string.
 */
private fun formatTimestamp(seconds: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
        sdf.format(Date(seconds * 1000))
    } catch (e: Exception) {
        "Unknown time"
    }
}
