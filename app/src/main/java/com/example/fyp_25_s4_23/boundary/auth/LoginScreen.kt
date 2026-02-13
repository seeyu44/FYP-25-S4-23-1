package com.example.fyp_25_s4_23.boundary.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fyp_25_s4_23.util.DeviceCompatibilityChecker
import com.example.fyp_25_s4_23.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    isBusy: Boolean,
    message: String?,
    onLogin: (String, String) -> Unit
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showCompatibilityDialog by remember { mutableStateOf(false) }

    val compatibilityChecker = remember { DeviceCompatibilityChecker(context) }
    val compatibilityResult = remember { compatibilityChecker.checkCompatibility() }
    val deviceSpecs = remember { compatibilityChecker.getDeviceSpecs() }

    FYP25S423Theme {
        Scaffold(containerColor = NavyDark) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Brush.verticalGradient(listOf(NavyDark, NavyLight)))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 30.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Deepfake Guard",
                        style = MaterialTheme.typography.headlineLarge,
                        color = CyanPoint,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "AI Voice Security System",
                        color = TextGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address", color = TextGray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = CyanPoint,
                            unfocusedBorderColor = NavyLight,
                            cursorColor = CyanPoint
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", color = TextGray) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = CyanPoint,
                            unfocusedBorderColor = NavyLight,
                            cursorColor = CyanPoint
                        )
                    )

                    if (message != null) {
                        Text(
                            text = message,
                            color = Color(0xFFFF5252),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { onLogin(email, password) },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPoint, contentColor = NavyDark)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(color = NavyDark, strokeWidth = 2.dp, modifier = Modifier.size(20.dp).padding(end = 8.dp))
                        }
                        Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "System Compatibility Status",
                        color = TextGray,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.Start).padding(start = 4.dp, bottom = 6.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (compatibilityResult.isCompatible)
                                Color(0xFF1E3A3A).copy(alpha = 0.6f) else Color(0xFF3D2323).copy(alpha = 0.6f)
                        )
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(if (compatibilityResult.isCompatible) Color.Green else Color.Red, CircleShape))
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(text = if (compatibilityResult.isCompatible) "Device is compatible" else "Compatibility Warning", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(text = deviceSpecs.deviceModel, color = TextWhite.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                            TextButton(onClick = { showCompatibilityDialog = true }) {
                                Text("Details", color = CyanPoint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        if (showCompatibilityDialog) {
            AlertDialog(
                onDismissRequest = { showCompatibilityDialog = false },
                title = { Text("Device Compatibility Report", color = CyanPoint) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Device Specifications", style = MaterialTheme.typography.titleMedium, color = CyanPoint)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = TextGray.copy(alpha = 0.3f))

                        Text("Model: ${deviceSpecs.deviceModel}", color = TextWhite)
                        Text("Android: ${deviceSpecs.androidVersion} (SDK ${deviceSpecs.sdkVersion})", color = TextWhite)
                        Text("RAM: ${deviceSpecs.availableRamMB}MB / ${deviceSpecs.totalRamMB}MB", color = TextWhite)
                        Text("Storage: ${deviceSpecs.availableStorageGB}GB available", color = TextWhite)
                        Text("CPU Cores: ${deviceSpecs.cpuCores}", color = TextWhite)

                        if (compatibilityResult.issues.isNotEmpty()) {
                            Text("\nCritical Issues", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFF5252), modifier = Modifier.padding(top = 16.dp))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFFF5252).copy(alpha = 0.3f))
                            compatibilityResult.issues.forEach { issue ->
                                Text("• $issue", color = Color(0xFFFF5252), fontSize = 13.sp)
                            }
                        }

                        if (compatibilityResult.warnings.isNotEmpty()) {
                            Text("\nWarnings", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFB74D), modifier = Modifier.padding(top = 16.dp))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFFFB74D).copy(alpha = 0.3f))
                            compatibilityResult.warnings.forEach { warning ->
                                Text("• $warning", color = Color(0xFFFFB74D), fontSize = 13.sp)
                            }
                        }

                        if (compatibilityResult.isCompatible && compatibilityResult.warnings.isEmpty()) {
                            Text("\nAll checks passed! ✓", style = MaterialTheme.typography.titleMedium, color = Color(0xFF81C784), modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCompatibilityDialog = false }) {
                        Text("Close", color = CyanPoint)
                    }
                },
                containerColor = NavyLight
            )
        }
    }
}