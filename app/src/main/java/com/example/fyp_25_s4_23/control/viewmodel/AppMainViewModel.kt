package com.example.fyp_25_s4_23.control.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.control.controllers.DetectionController
import com.example.fyp_25_s4_23.entity.data.db.AppDatabase
import com.example.fyp_25_s4_23.entity.data.repositories.AlertRepository
import com.example.fyp_25_s4_23.entity.data.repositories.CallRepository
import com.example.fyp_25_s4_23.entity.data.repositories.SettingsRepository
import com.example.fyp_25_s4_23.entity.data.repositories.UserRepository
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import com.example.fyp_25_s4_23.entity.domain.entities.UserSettings
import com.example.fyp_25_s4_23.entity.domain.valueobjects.UserRole
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.fyp_25_s4_23.control.AlertHandlerHolder
import com.example.fyp_25_s4_23.boundary.handlers.InCallAlertHandler
import android.util.Log

import com.example.fyp_25_s4_23.data.remote.dto.FCMTokenStore
import com.example.fyp_25_s4_23.util.mapUserRole

import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseAuthManager
import com.example.fyp_25_s4_23.data.remote.firebase.UserProfileRepository
import com.example.fyp_25_s4_23.data.remote.firebase.UsernameService
import com.example.fyp_25_s4_23.data.remote.dto.PendingUsernameStore

import com.example.fyp_25_s4_23.domain.entities.Contact
import com.example.fyp_25_s4_23.domain.entities.ContactLabel
import com.example.fyp_25_s4_23.util.VibratorUtil

sealed interface AppScreen {
    data object Loading : AppScreen
    data object Login : AppScreen
    data object Register : AppScreen
    data object Summary : AppScreen
    data object CallHistory : AppScreen
    data object Dashboard : AppScreen
    data object ContactList : AppScreen
    data object ManagedContacts : AppScreen
}

data class ModelTestResult(
    val status: String = "Idle",
    val selectedFile: String? = null,
    val score: Float? = null,
    val spectrogramBitmap: android.graphics.Bitmap? = null,
    val spectrogramFrames: Int? = null
)

data class AppUiState(
    val screen: AppScreen = AppScreen.Loading,
    val currentUser: UserAccount? = null,
    val userSettings: UserSettings = UserSettings(),
    val users: List<UserAccount> = emptyList(),
    val callRecords: List<CallRecord> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val message: String? = null,
    val isBusy: Boolean = false,
    val modelTest: ModelTestResult = ModelTestResult()
)

class AppMainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val userRepository = UserRepository(db.userDao())
    private val callRepository = CallRepository(
        db.callDao(),
        db.callMetadataDao(),
        db.detectionResultDao()
    )
    private val alertRepository = AlertRepository(db.alertEventDao())
    private val settingsRepository = SettingsRepository(db.userSettingsDao())
    private val alertHandler = InCallAlertHandler(application)
    private val detectionController = DetectionController(application, ModelRunner(application))

    private val modelRunner = ModelRunner(application)
    private val _state = MutableStateFlow(AppUiState())

    private val usernameService = UsernameService()
    private val pendingUsernameStore = PendingUsernameStore(application)
    private val userProfileRepository = UserProfileRepository()

    private val tokenStore = FCMTokenStore(application)
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        AlertHandlerHolder.handler = alertHandler
        viewModelScope.launch {
            userRepository.ensureDefaultAdmin()
            _state.update { it.copy(screen = AppScreen.Login, contacts = emptyList()) }
        }
    }

    fun runModelTest(audioFile: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isBusy = true,
                            modelTest = ModelTestResult(status = "Running...", selectedFile = audioFile)
                        )
                    }
                }

                val modelRunResult = try {
                    modelRunner.inferFromAsset("demo_audio/$audioFile")
                } catch (e: Exception) {
                    Log.e("ModelError", "Inference failed: ${e.message}")
                    null
                }

                if (modelRunResult == null) throw Exception("Model output is null")

                val probability = modelRunResult.score ?: 0.0f
                val isDeepfake = probability >= 0.7f

                if (isDeepfake) {
                    withContext(Dispatchers.Main) {
                        try {
                            Log.d("VibratorUtil", "Deepfake detected! Triggering vibration & alert.")

                            VibratorUtil.vibrate(getApplication())

                            AlertHandlerHolder.handler?.displayCriticalAlert(probability)
                        } catch (e: Exception) {
                            Log.e("AlertError", "Failed to trigger vibration/alert: ${e.message}")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            modelTest = ModelTestResult(
                                status = "Done",
                                selectedFile = audioFile,
                                score = probability
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e("ViewModelAlert", "Critical failure in test: ${e.message}")
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            modelTest = ModelTestResult(status = "Failed", selectedFile = audioFile)
                        )
                    }
                }
            }
        }
    }


    fun updateContactLabel(contactId: String, newLabel: ContactLabel) {
        _state.update { currentState ->
            val updatedContacts = currentState.contacts.map { contact ->
                if (contact.id == contactId) {
                    contact.copy(label = newLabel)
                } else {
                    contact
                }
            }
            currentState.copy(contacts = updatedContacts)
        }
    }
    fun navigateToRegister() { _state.update { it.copy(screen = AppScreen.Register, message = null) } }
    fun navigateToLogin() { _state.update { it.copy(screen = AppScreen.Login, message = null) } }
    fun navigateToSummary() { _state.update { it.copy(screen = AppScreen.Summary, message = null) } }
    fun navigateToCallHistory() { _state.update { it.copy(screen = AppScreen.CallHistory, message = null) } }
    fun navigateToDashboard() { _state.update { it.copy(screen = AppScreen.Dashboard, message = null) } }
    fun navigateToContactList() = _state.update { it.copy(screen = AppScreen.ContactList, message = null) }
    fun navigateToManagedContacts() { _state.update { it.copy(screen = AppScreen.ManagedContacts, message = null) } }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching {
                val firebaseUser = FirebaseAuthManager.login(email = email.trim(), password = password)
                if (!firebaseUser.isEmailVerified) throw IllegalStateException("Please verify your email before logging in.")
                val pendingUsername = pendingUsernameStore.get()
                if (pendingUsername != null) {
                    usernameService.claimUsername(pendingUsername)
                    pendingUsernameStore.clear()
                }
                val profile = userProfileRepository.getUserProfile(firebaseUser.uid)
                val user = UserAccount(
                    id = firebaseUser.uid.hashCode().toLong(),
                    username = profile.username,
                    displayName = profile.displayName.ifBlank { profile.username },
                    role = mapUserRole(profile.role),
                    createdAtSeconds = profile.createdAtSeconds
                )
                val settings = settingsRepository.get(user.id)
                user to settings
            }.onSuccess { (user, settings) ->
                _state.update { it.copy(currentUser = user, userSettings = settings, screen = AppScreen.Dashboard, isBusy = false) }
                if (settings.realTimeDetectionEnabled && hasRecordAudioPermission()) {
                    detectionController.startMonitoring()
                }
                refreshDashboard()
            }.onFailure { throwable ->
                _state.update { it.copy(isBusy = false, message = throwable.message ?: "Login failed") }
            }
        }
    }

    fun register(email: String, username: String, displayName: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching {
                val cleanUsername = username.trim().lowercase()
                val available = usernameService.checkUsername(cleanUsername)
                if (!available) throw IllegalStateException("Username already taken")
                FirebaseAuthManager.register(email.trim(), password)
                FirebaseAuthManager.sendEmailVerification()
                pendingUsernameStore.save(cleanUsername)
            }.onSuccess {
                _state.update { it.copy(screen = AppScreen.Login, isBusy = false, message = "Account created. Please verify your email.") }
            }.onFailure { e ->
                _state.update { it.copy(isBusy = false, message = e.message ?: "Registration failed") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            FirebaseAuthManager.logout()
            detectionController.stopMonitoring()
            _state.update {
                it.copy(currentUser = null, userSettings = UserSettings(), screen = AppScreen.Login, message = "Logged out", callRecords = emptyList())
            }
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

    fun setRealTimeDetection(enabled: Boolean) {
        val user = _state.value.currentUser ?: return
        if (enabled && !hasRecordAudioPermission()) {
            _state.update { it.copy(userSettings = it.userSettings.copy(realTimeDetectionEnabled = false), message = "Microphone permission required") }
            viewModelScope.launch { settingsRepository.update(user.id, _state.value.userSettings) }
            return
        }
        _state.update { it.copy(userSettings = it.userSettings.copy(realTimeDetectionEnabled = enabled)) }
        if (enabled) detectionController.startMonitoring() else detectionController.stopMonitoring()
        viewModelScope.launch { settingsRepository.update(user.id, _state.value.userSettings) }
    }

    suspend fun aggregateSummary(startMillis: Long, endMillis: Long, periodDaily: Boolean): List<com.example.fyp_25_s4_23.boundary.dashboard.SummaryMetrics> {
        val threshold = _state.value.userSettings.detectionThreshold
        val rows = if (periodDaily) callRepository.dailyAggregates(startMillis, endMillis, threshold) else callRepository.weeklyAggregates(startMillis, endMillis, threshold)
        return rows.map { r ->
            com.example.fyp_25_s4_23.boundary.dashboard.SummaryMetrics(
                label = r.period, totalCalls = r.total, answered = r.answered, missed = r.missed, suspicious = r.suspicious, blocked = r.blocked, warned = (r.suspicious - r.blocked).coerceAtLeast(0), avgConfidence = r.avgConfidence ?: -1.0
            )
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}