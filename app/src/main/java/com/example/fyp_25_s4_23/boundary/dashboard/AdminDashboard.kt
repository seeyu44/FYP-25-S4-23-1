package com.example.fyp_25_s4_23.boundary.dashboard

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import com.example.fyp_25_s4_23.control.controllers.SystemController
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.fyp_25_s4_23.data.remote.firebase.GlobalBlockRepository.GlobalBlockedUser

/**
 * Admin dashboard showing operational metrics and system management tools.
 */
@Composable
fun AdminDashboard(
    user: UserAccount,
    callRecords: List<CallRecord>,
    users: List<UserAccount>,
    globalBlockedUsers: List<GlobalBlockedUser>,
    message: String?,
    isBusy: Boolean,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    systemController: SystemController,
    onCreateAdmin: (String, String, String, String) -> Unit,
    onBlacklistGlobalUser: (String) -> Unit,
    onRemoveGlobalBlockedUser: (String) -> Unit
) {
    val ctx = LocalContext.current
    LaunchedEffect(user.role) {
        Toast.makeText(ctx, "Dashboard role: ${user.role}", Toast.LENGTH_SHORT).show()
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var showCreateAdminDialog by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = "Welcome, ${user.displayName}", style = MaterialTheme.typography.titleLarge)
                Text(text = "Admin Dashboard", style = MaterialTheme.typography.bodyMedium)
                // Debug: show resolved role for clarity
                Text(text = "Role: ${user.role}", style = MaterialTheme.typography.bodySmall)
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
                        text = { Text("Create Admin") },
                        onClick = {
                            menuExpanded = false
                            showCreateAdminDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        onClick = {
                            menuExpanded = false
                            onRefresh()
                        },
                        enabled = !isBusy
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

        // Monitor if uptime stops updating (system down)
        LaunchedEffect(Unit) {
            while (true) {
                delay(3000) // Check every 3 seconds
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
            // Status indicator circle
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
            // Call Analysis Section
            item {
                CallAnalysisCard(callRecords = callRecords)
            }

            // Global Blocked Review Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Flagged Users (Global)", style = MaterialTheme.typography.titleMedium)
                        if (globalBlockedUsers.isEmpty()) {
                            Text("No flagged users pending review")
                        } else {
                            globalBlockedUsers.forEach { entry ->
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    Text(
                                        text = entry.username ?: "UID: ${entry.userId}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    entry.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
                                        Text(
                                            text = phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TextButton(
                                            onClick = { onRemoveGlobalBlockedUser(entry.userId) },
                                            enabled = !isBusy
                                        ) {
                                            Text("Remove")
                                        }
                                        Button(
                                            onClick = { onBlacklistGlobalUser(entry.userId) },
                                            enabled = !isBusy
                                        ) {
                                            Text("Blacklist")
                                        }
                                    }
                                }
                                Divider(modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                }
            }

            // Registered Users Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors()
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
    
    if (showCreateAdminDialog) {
        CreateAdminDialog(
            onDismiss = { showCreateAdminDialog = false },
            onConfirm = { email, username, displayName, password ->
                onCreateAdmin(email, username, displayName, password)
                showCreateAdminDialog = false
            }
        )
}
}

@Composable
fun CreateAdminDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    
    fun validatePassword(pwd: String): String? {
        if (pwd.length < 8) return "At least 8 characters required"
        if (!pwd.any { it.isUpperCase() }) return "Needs one uppercase letter"
        if (!pwd.any { it.isLowerCase() }) return "Needs one lowercase letter"
        if (!pwd.any { it.isDigit() }) return "Needs one number"
        if (!pwd.any { !it.isLetterOrDigit() }) return "Needs one special character"
        return null
    }
    
    fun canSubmit(): Boolean {
        if (email.isBlank() || username.isBlank() || displayName.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            return false
        }
        if (password != confirmPassword) {
            return false
        }
        if (validatePassword(password) != null) {
            return false
        }
        return true
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Admin Account") },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = "" },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMessage = "" },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = password.isNotBlank() && validatePassword(password) != null,
                    supportingText = {
                        if (password.isNotBlank()) {
                            validatePassword(password)?.let { 
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = confirmPassword.isNotBlank() && password != confirmPassword,
                    supportingText = {
                        if (confirmPassword.isNotBlank() && password != confirmPassword) {
                            Text("Passwords do not match", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                Text(
                    text = "Role will be set to ADMIN automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(email, username, displayName, password) },
                enabled = canSubmit()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
