package com.example.fyp_25_s4_23.control.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.control.controllers.DetectionController
import com.example.fyp_25_s4_23.control.usecases.LoginUser
import com.example.fyp_25_s4_23.control.usecases.LogoutUser
import com.example.fyp_25_s4_23.control.usecases.RegisterUser
import com.example.fyp_25_s4_23.control.usecases.SaveDetectionAlertUseCase
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

import com.example.fyp_25_s4_23.data.remote.ApiClient
import com.example.fyp_25_s4_23.data.remote.dto.LoginRequest
import com.example.fyp_25_s4_23.data.remote.dto.LoginResponse
import com.example.fyp_25_s4_23.data.remote.dto.TokenStore
import com.example.fyp_25_s4_23.util.mapUserRole



sealed interface AppScreen {
    data object Loading : AppScreen
    data object Login : AppScreen
    data object Register : AppScreen
    data object Summary : AppScreen
    data object CallHistory : AppScreen
    data object Dashboard : AppScreen
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
    val message: String? = null,
    val isBusy: Boolean = false,
//    val modelTestResult: String? = null
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
    private val registerUser = RegisterUser(userRepository)
    private val loginUser = LoginUser(userRepository)
    private val logoutUser = LogoutUser()
    private val saveDetectionAlert = SaveDetectionAlertUseCase(alertRepository)

    private val _state = MutableStateFlow(AppUiState())

    private val tokenStore = TokenStore(application)
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        AlertHandlerHolder.handler = alertHandler
        viewModelScope.launch {
            userRepository.ensureDefaultAdmin()
            _state.update { it.copy(screen = AppScreen.Login) }
        }
    }

    // AppMainViewModel.kt (runModelTest 함수)
    fun runModelTest(audioFile: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // 1. UI 상태를 Running으로 업데이트
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isBusy = true,
                            message = null,
                            modelTest = ModelTestResult(
                                status = "Running...",
                                selectedFile = audioFile
                            )
                        )
                    }
                }

                val modelRunResult = runCatching {
                    modelRunner.inferFromAsset("demo_audio/$audioFile")
                }.getOrNull()

                val probability = modelRunResult?.score ?: 0.0f
                val threshold = _state.value.userSettings.detectionThreshold
                val isDeepfake = probability >= threshold

                Log.d("ViewModelAlert", "Model Test Probability: $probability (Threshold: $threshold) for file: $audioFile")

                // Generate a random phone number
                val randomAreaCode = (200..999).random()
                val randomPrefix = (200..999).random()
                val randomLineNumber = (1000..9999).random()
                val randomPhoneNumber = "+1 $randomAreaCode $randomPrefix $randomLineNumber"
                
                // Create a call record
                val callId = java.util.UUID.randomUUID().toString()
                val currentTimeSeconds = System.currentTimeMillis() / 1000
                
                val callRecord = com.example.fyp_25_s4_23.entity.domain.entities.CallRecord(
                    id = callId,
                    metadata = com.example.fyp_25_s4_23.entity.domain.entities.CallMetadata(
                        phoneNumber = randomPhoneNumber,
                        displayName = "Test Audio Call",
                        startTimeSeconds = currentTimeSeconds - 60,
                        endTimeSeconds = currentTimeSeconds - 30,
                        direction = com.example.fyp_25_s4_23.entity.domain.valueobjects.CallDirection.INCOMING
                    ),
                    status = com.example.fyp_25_s4_23.entity.domain.valueobjects.CallStatus.COMPLETED,
                    detections = listOf(
                        com.example.fyp_25_s4_23.entity.domain.entities.DetectionResult(
                            probability = probability,
                            isDeepfake = isDeepfake,
                            modelVersion = "0.0.1"
                        )
                    )
                )
                
                // Save call record to database (must complete before refreshing)
                val saveSuccess = runCatching {
                    val currentUser = _state.value.currentUser
                    callRepository.upsert(callRecord, currentUser?.id)
                    Log.i("ViewModelAlert", "Call record saved from model test with phone: $randomPhoneNumber")
                    true
                }.getOrElse { e ->
                    Log.e("ViewModelAlert", "Failed to save call record", e)
                    false
                }
                
                if (isDeepfake && saveSuccess) {
                    // Save alert to database
                    runCatching {
                        saveDetectionAlert(callId, probability)
                        Log.i("ViewModelAlert", "Alert saved to database for Model Test.")
                    }.onFailure { e ->
                        Log.e("ViewModelAlert", "Failed to save alert to database", e)
                    }
                    
                    // Display UI alert (toast + vibration) on Main thread
                    withContext(Dispatchers.Main) {
                        AlertHandlerHolder.handler?.displayCriticalAlert(probability)
                        Log.i("ViewModelAlert", "Alert displayed for Model Test.")
                    }
                }
                
                // Refresh dashboard to show new call (must be after save completes)
                if (saveSuccess) {
                    refreshDashboard()
                }

                val statusText = if (modelRunResult != null) "Done" else "Failed (see logcat)"

                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            message = if (isDeepfake) "Alert triggered by model test." else null,
                            modelTest = ModelTestResult(
                                status = statusText,
                                selectedFile = audioFile,
                                score = probability
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewModelAlert", "Error during model test", e)
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            message = "Error: ${e.message}",
                            modelTest = ModelTestResult(
                                status = "Failed",
                                selectedFile = audioFile
                            )
                        )
                    }
                }
            }
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

    fun navigateToCallHistory() {
        _state.update { it.copy(screen = AppScreen.CallHistory, message = null) }
    }

    fun navigateToDashboard() {
        _state.update { it.copy(screen = AppScreen.Dashboard, message = null) }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }

            // edit1
            runCatching {
                val LoginResponse = ApiClient.authApi.login(
                    LoginRequest(username= username.trim(),password = password)
                )
            // Store JWT
            tokenStore.save(LoginResponse.accessToken)

             val authHeader = "Bearer ${tokenStore.get_token()}"

            //Fetch user profile
            val profile = ApiClient.userApi.getCurrentUser(authHeader)

                val user = UserAccount(
                    id = profile.id.hashCode().toLong(),
                    username = profile.username,
                    displayName = profile.display_name,
                    role = mapUserRole(profile.role),
                    createdAtSeconds = profile.created_at_seconds
                )

            // Fetch local user settings
            val settings = settingsRepository.get(user.id)

            user to settings
            }
                .onSuccess { (user, settings) ->
                    _state.update {
                        it.copy(
                            currentUser = user,
                            userSettings = settings,
                            screen = AppScreen.Dashboard,
                            message = null,
                            isBusy = false
                        )
                    }
                    if (settings.realTimeDetectionEnabled) {
                        if (hasRecordAudioPermission()) {
                            detectionController.startMonitoring()
                        } else {
                            // Only show microphone permission message for non-admin users
                            if (user.role != UserRole.ADMIN) {
                                _state.update {
                                    it.copy(
                                        message = "Enable microphone permission to resume detection"
                                    )
                                }
                            }
                        }
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
        detectionController.stopMonitoring()
        _state.update {
            it.copy(
                currentUser = logoutUser(it.currentUser),
                userSettings = UserSettings(),
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

    fun setRealTimeDetection(enabled: Boolean) {
        val user = _state.value.currentUser ?: return
        if (enabled && !hasRecordAudioPermission()) {
            _state.update {
                it.copy(
                    userSettings = it.userSettings.copy(realTimeDetectionEnabled = false),
                    message = "Microphone permission required to enable detection"
                )
            }
            viewModelScope.launch {
                settingsRepository.update(user.id, _state.value.userSettings)
            }
            return
        }
        _state.update { it.copy(userSettings = it.userSettings.copy(realTimeDetectionEnabled = enabled)) }
        if (enabled) {
            detectionController.startMonitoring()
        } else {
            detectionController.stopMonitoring()
        }
        viewModelScope.launch {
            settingsRepository.update(user.id, _state.value.userSettings)
        }
    }



    suspend fun aggregateSummary(startMillis: Long, endMillis: Long, periodDaily: Boolean): List<com.example.fyp_25_s4_23.boundary.dashboard.SummaryMetrics> {
        val threshold = _state.value.userSettings.detectionThreshold
        val rows = if (periodDaily) callRepository.dailyAggregates(startMillis, endMillis, threshold) else callRepository.weeklyAggregates(startMillis, endMillis, threshold)
        return rows.map { r ->
            com.example.fyp_25_s4_23.boundary.dashboard.SummaryMetrics(
                label = r.period,
                totalCalls = r.total,
                answered = r.answered,
                missed = r.missed,
                suspicious = r.suspicious,
                blocked = r.blocked,
                warned = (r.suspicious - r.blocked).coerceAtLeast(0),
                avgConfidence = r.avgConfidence ?: -1.0
            )
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
