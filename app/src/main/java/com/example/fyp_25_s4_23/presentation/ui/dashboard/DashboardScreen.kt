package com.example.fyp_25_s4_23.presentation.ui.dashboard

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.fyp_25_s4_23.domain.entities.CallRecord
import com.example.fyp_25_s4_23.domain.entities.UserAccount
import com.example.fyp_25_s4_23.domain.entities.UserSettings
import com.example.fyp_25_s4_23.domain.valueobjects.UserRole
import com.example.fyp_25_s4_23.presentation.call.CallManager
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import com.example.fyp_25_s4_23.presentation.ui.debug.ModelTestScreen

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
    onToggleDetection: (Boolean) -> Unit,
    modelRunner: ModelRunner? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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

            if (modelRunner != null) {
                item {
                    ModelTestScreen(modelRunner = modelRunner)
                }
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
