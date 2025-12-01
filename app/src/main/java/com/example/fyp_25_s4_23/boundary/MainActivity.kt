package com.example.fyp_25_s4_23.boundary

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fyp_25_s4_23.boundary.auth.LoginScreen
import com.example.fyp_25_s4_23.boundary.auth.RegisterScreen
import com.example.fyp_25_s4_23.boundary.dashboard.DashboardScreen
import com.example.fyp_25_s4_23.control.viewmodel.AppMainViewModel
import com.example.fyp_25_s4_23.control.viewmodel.AppScreen
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import com.example.fyp_25_s4_23.ui.theme.FYP25S423Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FYP25S423Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
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
    val modelRunner = remember { ModelRunner(context) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setRealTimeDetection(true)
        } else {
            viewModel.setRealTimeDetection(false)
        }
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

        AppScreen.Dashboard -> {
            val user = uiState.currentUser
            if (user == null) {
                viewModel.navigateToLogin()
            } else {
                DashboardScreen(
                    user = user,
                    userSettings = uiState.userSettings,
                    callRecords = uiState.callRecords,
                    users = uiState.users,
                    message = uiState.message,
                    isBusy = uiState.isBusy,
                    onLogout = viewModel::logout,
                    onRefresh = viewModel::refreshDashboard,
                    onSeedData = viewModel::seedSampleData,
                    onToggleDetection = detectionToggleHandler,
                    modelRunner = modelRunner
                )
            }
        }
    }
}
