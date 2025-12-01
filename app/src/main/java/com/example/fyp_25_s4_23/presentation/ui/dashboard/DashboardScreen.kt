package com.example.fyp_25_s4_23.presentation.ui.dashboard

import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import com.example.fyp_25_s4_23.domain.entities.CallRecord
import com.example.fyp_25_s4_23.domain.entities.UserAccount
import com.example.fyp_25_s4_23.domain.valueobjects.UserRole
import com.example.fyp_25_s4_23.control.controllers.SystemController

/**
 * Main dashboard router that displays the appropriate dashboard based on user role.
 */
@Composable
fun DashboardScreen(
    user: UserAccount,
    callRecords: List<CallRecord>,
    users: List<UserAccount>,
    message: String?,
    isBusy: Boolean,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onSeedData: () -> Unit,
    onNavigateToSummary: () -> Unit,
    systemController: SystemController
) {
    when (user.role) {
        UserRole.ADMIN -> {
            AdminDashboard(
                user = user,
                callRecords = callRecords,
                users = users,
                message = message,
                isBusy = isBusy,
                onLogout = onLogout,
                onRefresh = onRefresh,
                onSeedData = onSeedData,
                systemController = systemController
            )
        }
        else -> {
            UserDashboard(
                user = user,
                callRecords = callRecords,
                message = message,
                isBusy = isBusy,
                onLogout = onLogout,
                onRefresh = onRefresh,
                onSeedData = onSeedData,
                onNavigateToSummary = onNavigateToSummary,
                systemController = systemController
            )
        }
    }
}

@Composable
fun TestingPanel(onSeedData: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Testing Lab", style = MaterialTheme.typography.titleMedium)
            Text(
                "Use these helpers to seed SQLite data so each teammate can work on their feature without touching git history."
            )
            Button(onClick = onSeedData, modifier = Modifier.padding(top = 8.dp)) {
                Text("Add sample call & alert")
            }
        }
    }
}
