package com.example.fyp_25_s4_23.control.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.boundary.dashboard.SummaryMetrics
import com.example.fyp_25_s4_23.entity.data.repositories.CallRepository
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AdminViewModel"

/**
 * State for admin operations and statistics
 */
data class AdminState(
    val isLoading: Boolean = false,
    val summaryMetrics: List<SummaryMetrics> = emptyList(),
    val users: List<UserAccount> = emptyList(),
    val error: String? = null
)

/**
 * AdminViewModel handles all admin-specific operations:
 * - Loading and displaying statistics
 * - User management
 * - System performance metrics
 * - Admin dashboard data
 *
 * This ViewModel was extracted from AppMainViewModel as part of Phase 1 refactoring
 * to separate admin concerns into a dedicated feature ViewModel.
 */
class AdminViewModel(
    private val callRepository: CallRepository
) : ViewModel() {

    private val _adminState = MutableStateFlow(AdminState())
    val adminState: StateFlow<AdminState> = _adminState.asStateFlow()

    /**
     * Loads admin statistics including call metrics and performance data
     */
    fun loadAdminStats() {
        viewModelScope.launch {
            _adminState.value = _adminState.value.copy(isLoading = true, error = null)
            try {
                // Load call statistics (daily aggregates)
                val now = System.currentTimeMillis()
                val thirtyDaysAgo = now - (30 * 24 * 60 * 60 * 1000)

                val dailyMetrics = callRepository.dailyAggregates(thirtyDaysAgo, now)

                // Convert DAO aggregates to SummaryMetrics
                val summaryMetrics = dailyMetrics.map { aggregate ->
                    SummaryMetrics(
                        label = aggregate.period,
                        totalCalls = aggregate.total,
                        answered = aggregate.answered,
                        missed = aggregate.total - aggregate.answered,
                        suspicious = aggregate.suspicious,
                        blocked = aggregate.blocked,
                        warned = 0,  // Not available in AggregateResult
                        avgConfidence = aggregate.avgConfidence ?: 0.0
                    )
                }

                _adminState.value = _adminState.value.copy(
                    isLoading = false,
                    summaryMetrics = summaryMetrics
                )

                Log.d(TAG, "Loaded ${summaryMetrics.size} days of metrics")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load admin stats", e)
                _adminState.value = _adminState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load statistics"
                )
            }
        }
    }

    /**
     * Loads all users in the system
     */
    fun loadAllUsers() {
        viewModelScope.launch {
            _adminState.value = _adminState.value.copy(isLoading = true, error = null)
            try {
                // TODO: Implement user loading when UserDirectory is available
                _adminState.value = _adminState.value.copy(
                    isLoading = false,
                    users = emptyList()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load users", e)
                _adminState.value = _adminState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load users"
                )
            }
        }
    }

    /**
     * Toggles user disabled status
     */
    fun toggleUserDisabled(firebaseUid: String, currentlyDisabled: Boolean) {
        viewModelScope.launch {
            try {
                // TODO: Implement when AdminManagementService is available
                Log.d(TAG, "User $firebaseUid disabled status toggled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle user disabled status", e)
                _adminState.value = _adminState.value.copy(
                    error = e.message ?: "Failed to update user"
                )
            }
        }
    }

    /**
     * Gets weekly aggregated statistics
     */
    fun getWeeklyStats() {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000)

                val weeklyMetrics = callRepository.weeklyAggregates(sevenDaysAgo, now)

                val summaryMetrics = weeklyMetrics.map { aggregate ->
                    SummaryMetrics(
                        label = aggregate.period,
                        totalCalls = aggregate.total,
                        answered = aggregate.answered,
                        missed = aggregate.total - aggregate.answered,
                        suspicious = aggregate.suspicious,
                        blocked = aggregate.blocked,
                        warned = 0,
                        avgConfidence = aggregate.avgConfidence ?: 0.0
                    )
                }

                _adminState.value = _adminState.value.copy(
                    summaryMetrics = summaryMetrics
                )

                Log.d(TAG, "Loaded weekly stats")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load weekly stats", e)
            }
        }
    }

    /**
     * Clears any error messages
     */
    fun clearError() {
        _adminState.value = _adminState.value.copy(error = null)
    }
}
