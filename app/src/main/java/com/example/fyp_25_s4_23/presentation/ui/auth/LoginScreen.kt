package com.example.fyp_25_s4_23.presentation.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.util.DeviceCompatibilityChecker

@Composable
fun LoginScreen(
    isBusy: Boolean,
    message: String?,
    onLogin: (String, String) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showCompatibilityDialog by remember { mutableStateOf(false) }

    // Optional: Device compatibility check (doesn't block login)
    val compatibilityChecker = remember { DeviceCompatibilityChecker(context) }
    val compatibilityResult = remember { compatibilityChecker.checkCompatibility() }
    val deviceSpecs = remember { compatibilityChecker.getDeviceSpecs() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Deepfake Guard Login")

        // Optional Compatibility Status Card (can be removed if not wanted)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (compatibilityResult.isCompatible)
                    Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (compatibilityResult.isCompatible) Color.Green else Color.Red,
                            shape = CircleShape
                        )
                )

                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        text = if (compatibilityResult.isCompatible)
                            "Device Compatible" else "Compatibility Warning",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (compatibilityResult.isCompatible)
                            Color(0xFF2E7D32) else Color(0xFFF57C00)
                    )
                    Text(
                        text = deviceSpecs.deviceModel,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                TextButton(onClick = { showCompatibilityDialog = true }) {
                    Text("Details", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        // End of optional compatibility card

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        if (message != null) {
            Text(
                text = message,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        // Login button - UNCHANGED, works exactly like before
        Button(
            onClick = { onLogin(username, password) },
            enabled = !isBusy,  // Only checks isBusy, NOT compatibility
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp)
                )
            }
            Text("Sign In")
        }

        TextButton(onClick = onNavigateToRegister, modifier = Modifier.padding(top = 8.dp)) {
            Text("Need an account? Register")
        }
    }

    // Compatibility Details Dialog
    if (showCompatibilityDialog) {
        AlertDialog(
            onDismissRequest = { showCompatibilityDialog = false },
            title = { Text("Device Compatibility Report") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Device Specifications", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text("Model: ${deviceSpecs.deviceModel}")
                    Text("Android: ${deviceSpecs.androidVersion} (SDK ${deviceSpecs.sdkVersion})")
                    Text("RAM: ${deviceSpecs.availableRamMB}MB / ${deviceSpecs.totalRamMB}MB")
                    Text("Storage: ${deviceSpecs.availableStorageGB}GB available")
                    Text("CPU Cores: ${deviceSpecs.cpuCores}")

                    if (compatibilityResult.issues.isNotEmpty()) {
                        Text(
                            "\nCritical Issues",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        compatibilityResult.issues.forEach { issue ->
                            Text("• $issue", color = Color(0xFFC62828))
                        }
                    }

                    if (compatibilityResult.warnings.isNotEmpty()) {
                        Text(
                            "\nWarnings",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFF57C00),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        compatibilityResult.warnings.forEach { warning ->
                            Text("• $warning", color = Color(0xFFF57C00))
                        }
                    }

                    if (compatibilityResult.isCompatible && compatibilityResult.warnings.isEmpty()) {
                        Text(
                            "\nAll checks passed! ✓",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCompatibilityDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}