package com.example.fyp_25_s4_23.presentation.ui.dashboard

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.domain.entities.CallRecord
import com.example.fyp_25_s4_23.domain.entities.UserAccount
import com.example.fyp_25_s4_23.control.controllers.SystemController
import android.util.Log

/**
 * User dashboard showing recent calls and system health.
 */
@Composable
fun UserDashboard(
    user: UserAccount,
    callRecords: List<CallRecord>,
    message: String?,
    isBusy: Boolean,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onSeedData: () -> Unit,
    onNavigateToSummary: () -> Unit,
    systemController: SystemController
) {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "Welcome, ${user.displayName}", style = MaterialTheme.typography.titleLarge)
                Text(text = "User Dashboard", style = MaterialTheme.typography.bodyMedium)
                // Debug: show resolved role to help verify which dashboard is rendered
                Text(text = "Role: ${user.role}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = onRefresh, enabled = !isBusy) { Text("Refresh") }
                Button(onClick = onLogout, modifier = Modifier.padding(top = 4.dp)) { Text("Logout") }
            }
        }

        // Summary navigation for users
        Button(onClick = {
            Log.d("UserDashboard", "Summary button clicked by user=${user.username}, role=${user.role}")
            onNavigateToSummary()
        }, modifier = Modifier.padding(top = 12.dp)) {
            Text("View Daily/Weekly Summary")
        }

        val uptime = remember { mutableStateOf("00:00:00") }
        val isSystemHealthy = remember { mutableStateOf(true) }
        val lastUpdateTime = remember { mutableStateOf(System.currentTimeMillis()) }

        LaunchedEffect(Unit) {
            while (true) {
                try {
                    uptime.value = systemController.fetchUptime()
                    lastUpdateTime.value = System.currentTimeMillis()
                    isSystemHealthy.value = true
                } catch (e: Exception) {
                    isSystemHealthy.value = false
                }
                delay(1000)
            }
        }

        // Monitor if uptime stops updating (system down)
        LaunchedEffect(Unit) {
            while (true) {
                delay(3000) // Check every 3 seconds
                val timeSinceLastUpdate = System.currentTimeMillis() - lastUpdateTime.value
                if (timeSinceLastUpdate > 3000) {
                    isSystemHealthy.value = false
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator circle
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = if (isSystemHealthy.value) Color.Green else Color.Red,
                        shape = CircleShape
                    )
            )

            Text(
                text = "System Uptime: ${uptime.value}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp)
            )

            Text(
                text = if (isSystemHealthy.value) "(Online)" else "(Offline)",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSystemHealthy.value) Color.Green else Color.Red,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (message != null) {
            Text(text = message, modifier = Modifier.padding(top = 8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 12.dp)
        ) {
            // Recent Calls Section
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Recent Calls", style = MaterialTheme.typography.titleMedium)
                        if (callRecords.isEmpty()) {
                            Text("No call data yet. Use the testing panel to add samples.")
                        } else {
                            callRecords.take(5).forEach { record ->
                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                    Text("${record.metadata.displayName ?: "Unknown"} (${record.metadata.phoneNumber})")
                                    Text("Probability: ${(record.detections.lastOrNull()?.probability ?: 0f) * 100f}%")
                                }
                                Divider()
                            }
                        }
                    }
                }
            }

            // Testing Panel
            item {
                TestingPanel(onSeedData = onSeedData)
            }
        }
    }
}
