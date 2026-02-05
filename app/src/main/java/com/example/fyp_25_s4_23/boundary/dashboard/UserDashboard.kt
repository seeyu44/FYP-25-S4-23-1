package com.example.fyp_25_s4_23.boundary.dashboard

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.TextAlign

import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import com.example.fyp_25_s4_23.entity.domain.entities.UserSettings
import com.example.fyp_25_s4_23.entity.domain.valueobjects.UserRole
import com.example.fyp_25_s4_23.control.controllers.SystemController
import com.example.fyp_25_s4_23.boundary.call.VoipCallManager
import com.example.fyp_25_s4_23.control.utils.getMemoryUsageGb
import com.example.fyp_25_s4_23.entity.domain.entities.FirebaseCallRecord
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.example.fyp_25_s4_23.ui.theme.* @OptIn(ExperimentalMaterial3Api::class)

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
    var showLogoutDialog by remember { mutableStateOf(false) }

    val latencyTrend = remember { mutableStateListOf<Int>() }
    val memoryTrend = remember { mutableStateListOf<Float>() }

    val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        ctx, android.Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(ctx, "Microphone permission is required for calls", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DEEPFAKE GUARD",
                            style = MaterialTheme.typography.titleLarge,
                            color = CyanPoint
                        )
                        Text(
                            text = "Welcome Back, ${user.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            )
        },
        bottomBar = {
            CompositionLocalProvider(
                LocalDensity provides Density(density = LocalDensity.current.density, fontScale = 1f)
            ) {
                BottomNavigationBar(
                    currentRoute = "home",
                    onNavigate = { route ->
                        when (route) {
                            "home" -> { }
                            "summary" -> onNavigateToSummary?.invoke()
                            "call_history" -> onNavigateToCallHistory?.invoke()
                            "dialer" -> onNavigateToDialer?.invoke()
                            "contacts" -> onNavigateToContactList?.invoke()
                            "logout" -> showLogoutDialog = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
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
                    } catch (e: Exception) { isSystemHealthy.value = false }
                    delay(1000)
                }
            }

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

            /* ================= MAIN CONTENT ================= */
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
            ) {
                if (user.role == UserRole.REGISTERED && onNavigateToSummary != null) {
                    item { AccountAnalysisCard(firebaseCalls = firebaseCalls, onClick = onNavigateToSummary) }
                }
                if (user.role == UserRole.REGISTERED && onNavigateToCallHistory != null) {
                    item { RecentCallHistoryCard(firebaseCalls = firebaseCalls, onViewFullHistory = onNavigateToCallHistory) }
                }

                if (onSubmitReview != null && user.role == UserRole.REGISTERED) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = NavyLight.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Enjoying the app?",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Help us improve",
                                        color = Color.White.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Button(
                                    onClick = { showReviewDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanPoint),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Write Review", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        /* ================= DIALOGS ================= */
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text(text = "Logout", color = CyanPoint, fontWeight = FontWeight.Bold) },
                text = { Text(text = "Are you sure you want to log out?", color = Color.White) },
                confirmButton = {
                    TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                        Text("Confirm", color = CyanPoint, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel", color = Color.LightGray)
                    }
                },
                containerColor = NavyLight,
                shape = RoundedCornerShape(16.dp)
            )
        }

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