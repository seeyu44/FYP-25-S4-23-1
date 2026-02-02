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
import com.example.fyp_25_s4_23.control.utils.getMemoryUsageGb

import androidx.compose.runtime.mutableStateListOf

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
    onNavigateToContactList: (() -> Unit)? = null,
    onRunModelTest: ((String) -> Unit)? = null,
    modelTestResult: ModelTestResult = ModelTestResult(),
    onSubmitReview: ((Int, String, Boolean) -> Unit)? = null,
    onNavigateToDialer: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }

    /* ================= TREND STATE ================= */

    val latencyTrend = remember { mutableStateListOf<Int>() }
    val memoryTrend = remember { mutableStateListOf<Float>() }

    /* ================= PERMISSIONS ================= */

    val hasMicPermission =
        androidx.core.content.ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val micPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                Toast.makeText(
                    ctx,
                    "Microphone permission is required for calls",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    /* ================= SCAFFOLD ================= */

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

                            if (onSubmitReview != null && user.role == UserRole.REGISTERED) {
                                DropdownMenuItem(
                                    text = { Text("Leave a Review") },
                                    onClick = {
                                        menuExpanded = false
                                        showReviewDialog = true
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Manage Contacts") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToContactList?.invoke()
                                }
                            )

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
    ) { paddingValues ->

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

            // uptime + health
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

            // health timeout
            LaunchedEffect(Unit) {
                while (true) {
                    delay(3000)
                    if (System.currentTimeMillis() - lastUpdateTime.value > 3000) {
                        isSystemHealthy.value = false
                    }
                }
            }

            // trend updates
            LaunchedEffect(Unit) {
                while (true) {
                    val latency = (12..35).random()
                    val memory = getMemoryUsageGb(ctx)

                    latencyTrend.add(latency)
                    memoryTrend.add(memory)

                    if (latencyTrend.size > 20) latencyTrend.removeAt(0)
                    if (memoryTrend.size > 20) memoryTrend.removeAt(0)

                    delay(2000)
                }
            }

            SystemStatusCard(
                uptime = uptime.value,
                isSystemHealthy = isSystemHealthy.value,
                latencyMs = latencyTrend.lastOrNull() ?: 0,
                memoryUsedGb = memoryTrend.lastOrNull() ?: 0f,
                latencyTrend = latencyTrend,
                memoryTrend = memoryTrend
            )

            if (message != null) {
                Text(text = message, modifier = Modifier.padding(top = 8.dp))
            }

            if (!hasMicPermission) {
                Card(
                    modifier = Modifier.padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "⚠️ Microphone permission required for calls.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            /* ================= MAIN CONTENT ================= */

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 12.dp)
            ) {

                if (onNavigateToDialer != null) {
                    item { DialerCard(onOpenDialer = onNavigateToDialer) }
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
                            .padding(top = 16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = "VoIP Calls (Test)",
                                style = MaterialTheme.typography.titleMedium
                            )

                            users
                                .filter { it.id != user.id }
                                .forEach { otherUser ->
                                    Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        onClick = {
                                            otherUser.firebaseUid?.let { uid ->
                                                VoipCallManager.startOutgoingVoipCall(
                                                    context = ctx,
                                                    calleeUserId = uid,
                                                    calleeDisplayName = otherUser.username
                                                )
                                            }
                                        }
                                    ) {
                                        Text("Call ${otherUser.username}")
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
                        Column(Modifier.padding(16.dp)) {
                            Text("Recent Calls", style = MaterialTheme.typography.titleMedium)

                            if (callRecords.isEmpty()) {
                                Text("No call data yet.")
                            } else {
                                callRecords.take(5).forEach { record ->
                                    Column(Modifier.padding(vertical = 6.dp)) {
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

                            if (user.role == UserRole.REGISTERED &&
                                onNavigateToCallHistory != null
                            ) {
                                Button(onClick = onNavigateToCallHistory) {
                                    Text("View Call History")
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
                                Column(Modifier.padding(12.dp)) {
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
                            Column(Modifier.padding(16.dp)) {
                                Text("Registered Users", style = MaterialTheme.typography.titleMedium)
                                users.forEach {
                                    Text("${it.username} (${it.role})")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Show Review Dialog
        if (showReviewDialog && onSubmitReview != null) {
            ReviewDialog(
                onDismiss = { showReviewDialog = false },
                onSubmit = { rating, description, anonymous ->
                    onSubmitReview(rating, description, anonymous)
                    showReviewDialog = false
                }
            )
        }
    }
}

/* ================= COMPONENT ================= */

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
                    "Automatically monitors calls for synthetic voices."
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggleDetection)
        }
    }
}
