package com.example.fyp_25_s4_23.boundary.dashboard

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.fyp_25_s4_23.boundary.call.VoipCallManager

@OptIn(ExperimentalMaterial3Api::class)
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
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(user.role) {
        Toast.makeText(ctx, "Dashboard role: ${user.role}", Toast.LENGTH_SHORT).show()
        Log.d("VOIP_DEBUG_UI", "Users received in UI: ${users.size}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Welcome, ${user.displayName}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Role: ${user.role}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu"
                            )
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

                            if (user.role == UserRole.REGISTERED) {
                                DropdownMenuItem(
                                    text = { Text("View Daily / Weekly Summary") },
                                    onClick = {
                                        menuExpanded = false
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
            )
        }
    ) { paddingValues: PaddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            /* ================= SYSTEM STATUS ================= */

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
                    if (System.currentTimeMillis() - lastUpdateTime.value > 3000) {
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
                    color = if (isSystemHealthy.value) Color.Green else Color.Red,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (message != null) {
                Text(text = message, modifier = Modifier.padding(top = 8.dp))
            }

            /* ================= MAIN CONTENT ================= */

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 12.dp)
            ) {

                item { DialerCard() }

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
                            .padding(top = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "VoIP Calls (Test)",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (users.isEmpty()) {
                                Text("No other users available")
                            } else {
                                users
                                    .filter { it.id != user.id } // don’t call yourself
                                    .forEach { otherUser ->
                                        Button(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            onClick = {
                                                VoipCallManager.startOutgoingVoipCall(
                                                    context = ctx,
                                                    calleeUserId = otherUser.firebaseUid!!
                                                )
                                            }
                                        ) {
                                            Text("Call ${otherUser.username}")
                                        }
                                    }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recent Calls", style = MaterialTheme.typography.titleMedium)

                                if (user.role == UserRole.REGISTERED && onNavigateToCallHistory != null) {
                                    Button(onClick = onNavigateToCallHistory) {
                                        Text("View Call History")
                                    }
                                }
                            }

                            if (callRecords.isEmpty()) {
                                Text("No call data yet. Use the dialer to start protected calls.")
                            } else {
                                callRecords.take(5).forEach { record ->
                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                        Text(
                                            "${record.metadata.displayName ?: "Unknown"} " +
                                                    "(${record.metadata.phoneNumber})"
                                        )
                                        Text(
                                            "Probability: ${
                                                (record.detections.lastOrNull()?.probability ?: 0f) * 100f
                                            }%"
                                        )
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
}

/* ================= COMPONENTS ================= */

@Composable
private fun DetectionToggleCard(
    enabled: Boolean,
    onToggleDetection: (Boolean) -> Unit
) {
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
                Text(
                    "Real-time Deepfake Detection",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Automatically monitors calls for synthetic voices. Disable to save battery."
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggleDetection)
        }
    }
}
