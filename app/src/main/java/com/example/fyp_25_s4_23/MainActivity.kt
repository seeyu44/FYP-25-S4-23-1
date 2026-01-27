package com.example.fyp_25_s4_23

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fyp_25_s4_23.boundary.auth.LoginScreen
import com.example.fyp_25_s4_23.boundary.auth.RegisterScreen
import com.example.fyp_25_s4_23.boundary.callhistory.CallHistoryScreen
import com.example.fyp_25_s4_23.boundary.dashboard.DashboardScreen
import com.example.fyp_25_s4_23.boundary.dashboard.SummaryScreen
import com.example.fyp_25_s4_23.boundary.dashboard.UserDashboard
import com.example.fyp_25_s4_23.control.controllers.SystemController
import com.example.fyp_25_s4_23.control.viewmodel.AppMainViewModel
import com.example.fyp_25_s4_23.control.viewmodel.AppScreen
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import com.example.fyp_25_s4_23.ui.theme.FYP25S423Theme
import androidx.compose.runtime.LaunchedEffect
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.fyp_25_s4_23.control.call.IncomingCallListener
import com.google.firebase.auth.FirebaseAuth



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.w("BOOT","MainActivity onCreate reached")
        enableEdgeToEdge()
        setContent {
            FYP25S423Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AntiDeepfakeApp()
                }
            }
        }
    }
}

@Composable
fun AntiDeepfakeApp(viewModel: AppMainViewModel = viewModel()) {
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()

        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser

            if (user != null) {
                Log.d("INCOMING_CALL", "Starting IncomingCallListener for uid=${user.uid}")
                IncomingCallListener.start(context.applicationContext)
            } else {
                Log.d("INCOMING_CALL", "Stopping IncomingCallListener (no user)")
                IncomingCallListener.stop()
            }
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }
    val modelRunner = remember { ModelRunner(context) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setRealTimeDetection(granted)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> //viewModel.setNotificationPermission(granted)
    }


    val detectionToggleHandler: (Boolean) -> Unit = { enabled ->
        if (enabled) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.setRealTimeDetection(true)
            } else {
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            viewModel.setRealTimeDetection(false)
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    when (uiState.screen) {
        AppScreen.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        AppScreen.Login -> LoginScreen(
            isBusy = uiState.isBusy,
            message = uiState.message,
            onLogin = viewModel::login,
            onNavigateToRegister = viewModel::navigateToRegister
        )

        AppScreen.Register -> RegisterScreen(
            isBusy = uiState.isBusy,
            message = uiState.message,
            onRegister = viewModel::register,
            onNavigateToLogin = viewModel::navigateToLogin
        )

        AppScreen.Summary -> {
            val user = uiState.currentUser
            if (user == null) {
                viewModel.navigateToLogin()
            } else {
                SummaryScreen(
                    user = user,
                    metrics = uiState.summaryMetrics,
                    isLoading = uiState.isBusy,
                    onRequestSummary = { start, end, daily ->
                        viewModel.aggregateSummary(start, end, daily)
                    },
                    onBack = viewModel::navigateToDashboard
                )
            }
        }

        AppScreen.CallHistory -> {
            val user = uiState.currentUser
            if (user == null) {
                viewModel.navigateToLogin()
            } else {
                CallHistoryScreen(
                    user = user,
                    callRecords = uiState.callRecords,
                    onBack = viewModel::navigateToDashboard
                )
            }
        }

        AppScreen.Dashboard -> {
            val user = uiState.currentUser
            if (user == null) {
                viewModel.navigateToLogin()
            } else {
                val systemController = remember { SystemController() }

                DashboardScreen(
                    user = user,
                    callRecords = uiState.callRecords,
                    userSettings = uiState.userSettings,
                    users = uiState.users,
                    message = uiState.message,
                    isBusy = uiState.isBusy,
                    onLogout = viewModel::logout,
                    onRefresh = viewModel::refreshDashboard,
                    onNavigateToSummary = viewModel::navigateToSummary,
                    onNavigateToCallHistory = viewModel::navigateToCallHistory,
                    onToggleDetection = detectionToggleHandler,
                    systemController = systemController,
                    modelRunner = modelRunner,
                    onRunModelTest = viewModel::runModelTest,
                    modelTestResult = uiState.modelTest,
                    onSubmitReview = viewModel::submitReview,
                    onCreateAdmin = viewModel::createAdminUser,
                    onNavigateToDialer = viewModel::navigateToDialer
                )
            }
        }

        AppScreen.Dialer -> {
            val user = uiState.currentUser
            if (user == null) {
                viewModel.navigateToLogin()
            } else {
                com.example.fyp_25_s4_23.boundary.call.DialerScreen(
                    onBack = viewModel::navigateToDashboard
                )
            }
        }
    }
}