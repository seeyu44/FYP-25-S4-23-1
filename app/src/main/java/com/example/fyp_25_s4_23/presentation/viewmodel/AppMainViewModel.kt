package com.example.fyp_25_s4_23.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.control.usecases.LoginUser
import com.example.fyp_25_s4_23.control.usecases.LogoutUser
import com.example.fyp_25_s4_23.control.usecases.RegisterUser
import com.example.fyp_25_s4_23.control.usecases.SeedSampleData
import com.example.fyp_25_s4_23.data.db.AppDatabase
import com.example.fyp_25_s4_23.data.repositories.AlertRepository
import com.example.fyp_25_s4_23.data.repositories.CallRepository
import com.example.fyp_25_s4_23.data.repositories.UserRepository
import com.example.fyp_25_s4_23.domain.entities.CallRecord
import com.example.fyp_25_s4_23.domain.entities.UserAccount
import com.example.fyp_25_s4_23.domain.valueobjects.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AppScreen {
    data object Loading : AppScreen
    data object Login : AppScreen
    data object Register : AppScreen
    data object Summary : AppScreen
    data object Dashboard : AppScreen
}

data class AppUiState(
    val screen: AppScreen = AppScreen.Loading,
    val currentUser: UserAccount? = null,
    val users: List<UserAccount> = emptyList(),
    val callRecords: List<CallRecord> = emptyList(),
    val message: String? = null,
    val isBusy: Boolean = false
)

class AppMainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val userRepository = UserRepository(db.userDao())
    private val callRepository = CallRepository(db.callRecordDao())
    private val alertRepository = AlertRepository(db.alertEventDao())

    private val registerUser = RegisterUser(userRepository)
    private val loginUser = LoginUser(userRepository)
    private val logoutUser = LogoutUser()
    private val seedSampleData = SeedSampleData(callRepository, alertRepository)

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.ensureDefaultAdmin()
            _state.update { it.copy(screen = AppScreen.Login) }
        }
    }

    fun navigateToRegister() {
        _state.update { it.copy(screen = AppScreen.Register, message = null) }
    }

    fun navigateToLogin() {
        _state.update { it.copy(screen = AppScreen.Login, message = null) }
    }

    fun navigateToSummary() {
        _state.update { it.copy(screen = AppScreen.Summary, message = null) }
    }

    fun navigateToDashboard() {
        _state.update { it.copy(screen = AppScreen.Dashboard, message = null) }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching { loginUser(username.trim(), password) }
                .onSuccess { user ->
                    _state.update {
                        it.copy(
                            currentUser = user,
                            screen = AppScreen.Dashboard,
                            message = null,
                            isBusy = false
                        )
                    }
                    refreshDashboard()
                }
                .onFailure { throwable ->
                    _state.update { it.copy(isBusy = false, message = throwable.message) }
                }
        }
    }

    fun register(username: String, password: String, displayName: String, role: UserRole) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching { registerUser(username.trim(), password, displayName.trim(), role) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            screen = AppScreen.Login,
                            message = "Registration successful. Please log in.",
                            isBusy = false
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(isBusy = false, message = throwable.message) }
                }
        }
    }

    fun logout() {
        _state.update {
            it.copy(
                currentUser = logoutUser(it.currentUser),
                screen = AppScreen.Login,
                message = "Logged out",
                callRecords = emptyList()
            )
        }
    }

    fun refreshDashboard() {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val users = if (user.role == UserRole.ADMIN) userRepository.listUsers() else emptyList()
            val calls = callRepository.listRecent()
            _state.update { it.copy(users = users, callRecords = calls, isBusy = false) }
        }
    }

    // Fetch aggregated summaries from repository and map to SummaryMetrics for UI
    suspend fun aggregateSummary(startMillis: Long, endMillis: Long, periodDaily: Boolean): List<com.example.fyp_25_s4_23.presentation.ui.dashboard.SummaryMetrics> {
        val threshold = 0.5
        val rows = if (periodDaily) callRepository.dailyAggregates(startMillis, endMillis, threshold) else callRepository.weeklyAggregates(startMillis, endMillis, threshold)
        return rows.map { r ->
            com.example.fyp_25_s4_23.presentation.ui.dashboard.SummaryMetrics(
                label = r.period,
                totalCalls = r.total,
                answered = r.answered,
                missed = r.missed,
                suspicious = r.suspicious,
                blocked = r.blocked,
                warned = (r.suspicious - r.blocked).coerceAtLeast(0),
                avgConfidence = r.avg_confidence ?: -1.0
            )
        }
    }

    fun seedSampleData() {
        val user = _state.value.currentUser ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching { seedSampleData(user) }
                .onSuccess {
                    refreshDashboard()
                    _state.update { it.copy(message = "Sample data added", isBusy = false) }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(message = throwable.message, isBusy = false) }
                }
        }
    }
}

