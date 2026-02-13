package com.example.fyp_25_s4_23.boundary.dashboard

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
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import com.example.fyp_25_s4_23.entity.domain.entities.UserSettings
import com.example.fyp_25_s4_23.entity.domain.entities.FirebaseCallRecord
import com.example.fyp_25_s4_23.entity.domain.valueobjects.UserRole
import com.example.fyp_25_s4_23.control.controllers.SystemController
import com.example.fyp_25_s4_23.data.remote.firebase.GlobalBlockRepository.GlobalBlockedUser

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
    onNavigateToSummary: () -> Unit,
    onNavigateToCallHistory: () -> Unit,
    onNavigateToContactList: () -> Unit,
    systemController: SystemController,
    userSettings: UserSettings? = null,
    onSubmitReview: ((Int, String, Boolean) -> Unit)? = null,
    onCreateAdmin: ((String, String, String, String) -> Unit)? = null,
    onNavigateToDialer: (() -> Unit)? = null,
    firebaseCalls: List<FirebaseCallRecord> = emptyList(),
    onToggleDisableUser: ((String, Boolean) -> Unit)? = null,
    onDeleteUser: ((String) -> Unit)? = null,
    reviews: List<com.example.fyp_25_s4_23.boundary.dashboard.ReviewWithUserInfo> = emptyList(),
    onDeleteReview: ((String) -> Unit)? = null,
    auditLogs: List<com.example.fyp_25_s4_23.entity.domain.entities.AuditLog> = emptyList(),
    globalBlockedUsers: List<GlobalBlockedUser> = emptyList(),
    onBlacklistGlobalUser: ((String) -> Unit)? = null,
    onRemoveGlobalBlockedUser: ((String) -> Unit)? = null
) {
    when (user.role) {
        UserRole.ADMIN -> {
            AdminDashboard(
                user = user,
                callRecords = callRecords,
                users = users,
                globalBlockedUsers = globalBlockedUsers,
                message = message,
                isBusy = isBusy,
                onLogout = onLogout,
                onRefresh = onRefresh,
                systemController = systemController,
                onCreateAdmin = onCreateAdmin ?: { _, _, _, _ -> },
                onToggleDisableUser = onToggleDisableUser ?: { _, _ -> },
                onDeleteUser = onDeleteUser ?: { },
                reviews = reviews,
                onDeleteReview = onDeleteReview ?: { },
                auditLogs = auditLogs,
                onNavigateToSummary = onNavigateToSummary,
                onNavigateToCallHistory = onNavigateToCallHistory,
                onNavigateToContactList = onNavigateToContactList,
                onNavigateToDialer = onNavigateToDialer,
                onBlacklistGlobalUser = onBlacklistGlobalUser ?: {},
                onRemoveGlobalBlockedUser = onRemoveGlobalBlockedUser ?: {}
            )
        }
        else -> {
            UserDashboard(
                user = user,
                callRecords = callRecords,
                users = users,
                message = message,
                isBusy = isBusy,
                onLogout = onLogout,
                onRefresh = onRefresh,
                onNavigateToSummary = onNavigateToSummary,
                onNavigateToCallHistory = onNavigateToCallHistory,
                onNavigateToContactList = onNavigateToContactList,
                systemController = systemController,
                userSettings = userSettings,
                onSubmitReview = onSubmitReview,
                onNavigateToDialer = onNavigateToDialer,
                firebaseCalls = firebaseCalls
            )
        }
    }
}