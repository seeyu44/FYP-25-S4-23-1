package com.example.fyp_25_s4_23.boundary.dashboard

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import com.example.fyp_25_s4_23.boundary.call.VoipCallManager
import com.example.fyp_25_s4_23.control.utils.getMemoryUsageGb
import com.example.fyp_25_s4_23.entity.domain.entities.FirebaseCallRecord
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.ArrowForward

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
    systemController: SystemController = SystemController(),
    onNavigateToSummary: (() -> Unit)? = null,
    onNavigateToCallHistory: (() -> Unit)? = null,
    onNavigateToContactList: (() -> Unit)? = null,
    onSubmitReview: ((Int, String, Boolean) -> Unit)? = null,
    onNavigateToDialer: (() -> Unit)? = null,
    firebaseCalls: List<FirebaseCallRecord> = emptyList()
) {
    val ctx = LocalContext.current
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
                            text = "DEEPFAKE GUARD",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Welcome Back, ${user.displayName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                currentRoute = "home",
                onNavigate = { route ->
                    when (route) {
                        "home" -> { /* Already on home */ }
                        "summary" -> onNavigateToSummary?.invoke()
                        "call_history" -> onNavigateToCallHistory?.invoke()
                        "dialer" -> onNavigateToDialer?.invoke()
                        "contacts" -> onNavigateToContactList?.invoke()
                        "logout" -> onLogout()
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
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
            ) {

                // Account Analysis Section
                if (user.role == UserRole.REGISTERED && onNavigateToSummary != null) {
                    item {
                        AccountAnalysisCard(
                            firebaseCalls = firebaseCalls,
                            onClick = onNavigateToSummary
                        )
                    }
                }

                // Recent Call History Section
                if (user.role == UserRole.REGISTERED && onNavigateToCallHistory != null) {
                    item {
                        RecentCallHistoryCard(
                            firebaseCalls = firebaseCalls,
                            onViewFullHistory = onNavigateToCallHistory
                        )
                    }
                }

                // Leave a Review Section
                if (onSubmitReview != null && user.role == UserRole.REGISTERED) {
                    item {
                        LeaveReviewCard(
                            onReviewClick = { showReviewDialog = true }
                        )
                    }
                }

                // Admin panel
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
