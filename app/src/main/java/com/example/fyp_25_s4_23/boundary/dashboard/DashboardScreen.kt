package com.example.fyp_25_s4_23.boundary.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import com.example.fyp_25_s4_23.entity.domain.entities.UserSettings
import com.example.fyp_25_s4_23.entity.domain.valueobjects.UserRole
import com.example.fyp_25_s4_23.control.call.CallManager
import com.example.fyp_25_s4_23.boundary.audio.AudioTestCard


@Composable
fun DashboardScreen(
    user: UserAccount,
    userSettings: UserSettings,
    callRecords: List<CallRecord>,
    users: List<UserAccount>,
    message: String?,
    isBusy: Boolean,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onSeedData: () -> Unit,
    onToggleDetection: (Boolean) -> Unit
) {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "Welcome, ${user.displayName}", style = MaterialTheme.typography.titleLarge)
                Text(text = "Role: ${user.role}")
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = onRefresh, enabled = !isBusy) { Text("Refresh") }
                Button(onClick = onLogout, modifier = Modifier.padding(top = 4.dp)) { Text("Logout") }
            }
        }

        if (message != null) {
            Text(text = message, modifier = Modifier.padding(top = 8.dp))
        }

        DetectionToggleCard(
            enabled = userSettings.realTimeDetectionEnabled,
            onToggleDetection = onToggleDetection
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 12.dp)
        ) {
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

            if (user.role == UserRole.ADMIN) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Registered Users", style = MaterialTheme.typography.titleMedium)
                            if (users.isEmpty()) {
                                Text("No users found")
                            } else {
                                users.forEach {
                                    Text("${it.username} (${it.role})")
                                }
                            }
                        }
                    }
                }
            }

            item {
                TestingPanel(onSeedData = onSeedData)
            }

            item {
                CallLabSection()
            }
            
            item {
                AudioTestCard()
            }
        }
    }
}

@Composable
private fun TestingPanel(onSeedData: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Testing Lab", style = MaterialTheme.typography.titleMedium)
            Text(
                "Use these helpers to seed SQLite data so each teammate can work on their feature without touching git history."
            )
            Button(onClick = onSeedData, modifier = Modifier.padding(top = 8.dp)) {
                Text("Add sample call & alert")
            }
        }
    }
}

@Composable
private fun DetectionToggleCard(enabled: Boolean, onToggleDetection: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Real-time Deepfake Detection", style = MaterialTheme.typography.titleMedium)
                Text("Automatically monitors calls for synthetic voices. Disable when you need to save battery.")
            }
            Switch(checked = enabled, onCheckedChange = onToggleDetection)
        }
    }
}

@Composable
private fun CallLabSection() {
    val context = LocalContext.current
    val callManager = remember { CallManager(context) }
    val numberState = remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Call Lab", style = MaterialTheme.typography.titleMedium)
            Text("Set this app as the default dialer to route calls through the detector and place a quick test call.")
            Button(
                onClick = {
                    val activity = context as? android.app.Activity ?: return@Button
                    callManager.requestDefaultDialer(activity)
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Request default dialer role")
            }
            androidx.compose.material3.OutlinedTextField(
                value = numberState.value,
                onValueChange = { numberState.value = it },
                label = { Text("Phone number") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
            Button(
                onClick = { if (numberState.value.isNotBlank()) callManager.placeCall(numberState.value) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Place call")
            }
        }
    }
}
