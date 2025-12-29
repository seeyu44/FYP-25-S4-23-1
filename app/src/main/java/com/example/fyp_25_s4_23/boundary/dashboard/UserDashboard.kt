package com.example.fyp_25_s4_23.boundary.dashboard

import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import com.example.fyp_25_s4_23.entity.domain.entities.UserSettings
import com.example.fyp_25_s4_23.entity.domain.valueobjects.UserRole
import com.example.fyp_25_s4_23.control.controllers.SystemController
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import com.example.fyp_25_s4_23.boundary.debug.ModelTestScreen
import com.example.fyp_25_s4_23.control.viewmodel.ModelTestResult

@Composable
fun UserDashboard(
    user: UserAccount,
    callRecords: List<CallRecord> = emptyList(),
    users: List<UserAccount> = emptyList(),
    message: String? = null,
    isBusy: Boolean = false,
    userSettings: UserSettings? = null,
    onLogout: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onToggleDetection: ((Boolean) -> Unit)? = null,
    modelRunner: ModelRunner? = null,
    systemController: SystemController = SystemController(),
    onNavigateToSummary: (() -> Unit)? = null,
    onNavigateToCallHistory: (() -> Unit)? = null,
    onRunModelTest: ((String) -> Unit)? = null,
    modelTestResult: ModelTestResult = ModelTestResult()
) {
    val ctx = LocalContext.current
    LaunchedEffect(user.role) {
        Toast.makeText(ctx, "Dashboard role: ${user.role}", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        var menuExpanded by remember { mutableStateOf(false) }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "Welcome, ${user.displayName}", style = MaterialTheme.typography.titleLarge)
                Text(text = "Role: ${user.role}")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        onClick = {
                            menuExpanded = false
                            onRefresh()
                        },
                        enabled = !isBusy
                    )
                    if (user.role.name == "REGISTERED") {
                        DropdownMenuItem(
                            text = { Text("View Daily/Weekly Summary") },
                            onClick = {
                                menuExpanded = false
                                Log.d("UserDashboard", "Summary menu item clicked by user=${user.username}, role=${user.role}")
                                onNavigateToSummary?.invoke()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Logout") },
                        onClick = {
                            menuExpanded = false
                            onLogout()
                        }
                    )
                }
            }
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

        LaunchedEffect(Unit) {
            while (true) {
                delay(3000)
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
            item {
                DialerCard()
            }

            if (userSettings != null && onToggleDetection != null) {
                item {
                    DetectionToggleCard(
                        enabled = userSettings.realTimeDetectionEnabled,
                        onToggleDetection = onToggleDetection
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent Calls", style = MaterialTheme.typography.titleMedium)
                            if (user.role.name == "REGISTERED" && onNavigateToCallHistory != null) {
                                Button(
                                    onClick = {
                                        Log.d("UserDashboard", "Call History button clicked by user=${user.username}, role=${user.role}")
                                        onNavigateToCallHistory.invoke()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text("View Call History")
                                }
                            }
                        }
                        if (callRecords.isEmpty()) {
                            Text("No call data yet. Use the dialer to start protected calls.")
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

            modelRunner?.let { runner ->
                if (onRunModelTest != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Model Test", style = MaterialTheme.typography.titleMedium)
                                ModelTestScreen(
                                    modelRunner = runner,
                                    detectionEnabled = userSettings?.realTimeDetectionEnabled ?: true,
                                    onRunModelTest = onRunModelTest,
                                    modelTestResult = modelTestResult
                                )
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
        }
    }
}

@Composable
private fun DetectionToggleCard(enabled: Boolean, onToggleDetection: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
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