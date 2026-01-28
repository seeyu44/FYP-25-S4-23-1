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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
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
    onNavigateToContactList: (() -> Unit)? = null,
    onRunModelTest: ((String) -> Unit)? = null,
    modelTestResult: ModelTestResult = ModelTestResult()
) {
    val ctx = LocalContext.current

    LaunchedEffect(user.role) {
        Toast.makeText(ctx, "Dashboard role: ${user.role}", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        var menuExpanded by remember { mutableStateOf(false) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "Welcome, ${user.displayName}", style = MaterialTheme.typography.titleLarge)
                Text(text = "Role: ${user.role}", style = MaterialTheme.typography.bodyMedium)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuExpanded = false; onRefresh() })
                    if (user.role.name == "REGISTERED") {
                        DropdownMenuItem(text = { Text("View Summary") }, onClick = { menuExpanded = false; onNavigateToSummary?.invoke() })
                    }
                    DropdownMenuItem(text = { Text("Logout") }, onClick = { menuExpanded = false; onLogout() })
                }
            }
        }

        val uptime = remember { mutableStateOf("00:00:00") }
        val isSystemHealthy = remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            while (true) {
                try {
                    uptime.value = systemController.fetchUptime()
                    isSystemHealthy.value = true
                } catch (e: Exception) { isSystemHealthy.value = false }
                delay(1000)
            }
        }

        Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(if (isSystemHealthy.value) Color.Green else Color.Red, CircleShape))
            Text(text = " System Uptime: ${uptime.value}", modifier = Modifier.padding(start = 8.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(top = 12.dp)) {
            item { InternalDialerCard() }

            if (userSettings != null && onToggleDetection != null) {
                item { InternalDetectionToggleCard(userSettings.realTimeDetectionEnabled, onToggleDetection) }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Contact List", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("Manage your trusted and blocked contacts.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                        Button(onClick = { onNavigateToContactList?.invoke() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("Manage Contacts")
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent Calls", style = MaterialTheme.typography.titleMedium)
                            if (user.role.name == "REGISTERED" && onNavigateToCallHistory != null) {
                                Button(onClick = { onNavigateToCallHistory.invoke() }) {
                                    Text("View Call History")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (callRecords.isEmpty()) {
                            Text("No call data yet. Use the dialer to start protected calls.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            callRecords.take(5).forEach { record ->
                                Text("${record.metadata.phoneNumber}", style = MaterialTheme.typography.bodyMedium)
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }

            modelRunner?.let { runner ->
                if (onRunModelTest != null) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Model Test", style = MaterialTheme.typography.titleMedium)
                                ModelTestScreen(
                                    modelRunner = runner,
                                    detectionEnabled = userSettings?.realTimeDetectionEnabled ?: true,
                                    onRunModelTest = { onRunModelTest.invoke(it) },
                                    modelTestResult = modelTestResult
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InternalDialerCard() {
    var targetUsername by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Secure Internal VOIP", style = MaterialTheme.typography.titleMedium)
            Text("Enter a username to start a protected call.", style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = targetUsername,
                onValueChange = { targetUsername = it },
                label = { Text("Target Username") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true
            )

            Button(
                onClick = { /* Call Logic */ },
                modifier = Modifier.fillMaxWidth(),
                enabled = targetUsername.isNotBlank()
            ) {
                Icon(Icons.Default.Call, null)
                Spacer(Modifier.width(8.dp))
                Text("Start Secure Call")
            }
        }
    }
}

@Composable
private fun InternalDetectionToggleCard(enabled: Boolean, onToggleDetection: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Real-time Deepfake Detection", style = MaterialTheme.typography.titleMedium)
                Text("Automatically monitors calls.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = onToggleDetection)
        }
    }
}

