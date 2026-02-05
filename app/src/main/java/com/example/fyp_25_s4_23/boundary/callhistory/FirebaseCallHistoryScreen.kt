package com.example.fyp_25_s4_23.boundary.callhistory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.domain.entities.FirebaseCallRecord
import com.example.fyp_25_s4_23.boundary.dashboard.BottomNavigationBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays call history fetched from Firebase Cloud Functions
 * Shows calls where the user is either caller or callee
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseCallHistoryScreen(
    calls: List<FirebaseCallRecord>,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onNavigateToSummary: (() -> Unit)? = null,
    onNavigateToDialer: (() -> Unit)? = null,
    onNavigateToContacts: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Call History",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
                    Button(
                        onClick = onRefresh,
                        enabled = !isLoading
                    ) {
                        Text("Refresh")
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                currentRoute = "call_history",
                onNavigate = { route ->
                    when (route) {
                        "home" -> onBack()
                        "summary" -> onNavigateToSummary?.invoke()
                        "call_history" -> { /* Already here */ }
                        "dialer" -> onNavigateToDialer?.invoke()
                        "contacts" -> onNavigateToContacts?.invoke()
                        "logout" -> onLogout?.invoke()
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
            // Loading state
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading call history...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else if (calls.isEmpty()) {
                // Error message if present
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        )
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "No calls",
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .align(Alignment.CenterHorizontally),
                        tint = Color.Gray
                    )
                    Text(
                        text = "No call history",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Your calls will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                // Error message if present
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        )
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Call count summary
                Text(
                    text = "${calls.size} call${if (calls.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Call list
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(calls) { call ->
                        FirebaseCallHistoryCard(call = call)
                    }
                }
            }
        }
    }
}

/**
 * Individual call card for Firebase call history
 */
@Composable
fun FirebaseCallHistoryCard(call: FirebaseCallRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Contact info with call direction icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = if (call.isCaller) "Outgoing call" else "Incoming call",
                    tint = if (call.isCaller) Color(0xFF2196F3) else Color(0xFF4CAF50),
                    modifier = Modifier
                        .padding(end = 8.dp)
                )
                Text(
                    text = call.getContactName(),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Status badge
            val statusColor = when (call.status) {
                "completed" -> Color(0xFF4CAF50)
                "missed" -> Color(0xFFF44336)
                "declined" -> Color(0xFFFFA726)
                else -> Color.Gray
            }
            Text(
                text = call.status.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Timestamp - extract date using Calendar like summary page does
            val firebaseDate = call.createdAt?.toDate()
            if (firebaseDate != null) {
                val cal = java.util.Calendar.getInstance()
                cal.time = firebaseDate
                val year = cal.get(java.util.Calendar.YEAR)
                val month = cal.get(java.util.Calendar.MONTH) // 0-indexed
                val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
                val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                val minute = cal.get(java.util.Calendar.MINUTE)
                
                // Reconstruct date from calendar
                cal.set(year, month, day, hour, minute, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val date = cal.time
                
                val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                Text(
                    text = dateFormat.format(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            } else {
                Text(
                    text = "Unknown time",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // Duration (only show if call is completed)
            if (call.isCompleted() && call.duration > 0) {
                Text(
                    text = "Duration: ${call.getDurationString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Other user info
            if (call.otherUser.phoneNumber != null) {
                Text(
                    text = call.otherUser.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
