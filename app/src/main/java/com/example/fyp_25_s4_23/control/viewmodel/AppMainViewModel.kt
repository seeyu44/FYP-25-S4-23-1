package com.example.fyp_25_s4_23.control.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.boundary.handlers.InCallAlertHandler
import com.example.fyp_25_s4_23.control.AlertHandlerHolder
import com.example.fyp_25_s4_23.control.controllers.DetectionController
import com.example.fyp_25_s4_23.control.usecases.SaveDetectionAlertUseCase
import com.example.fyp_25_s4_23.data.remote.dto.FCMTokenStore
import com.example.fyp_25_s4_23.data.remote.dto.PendingUsernameStore
import com.example.fyp_25_s4_23.data.remote.firebase.*
import com.example.fyp_25_s4_23.entity.data.db.AppDatabase
import com.example.fyp_25_s4_23.entity.data.repositories.*
import com.example.fyp_25_s4_23.entity.domain.entities.*
import com.example.fyp_25_s4_23.entity.domain.valueobjects.*
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import com.example.fyp_25_s4_23.util.mapUserRole
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.example.fyp_25_s4_23.control.call.IncomingCallListener
import com.example.fyp_25_s4_23.boundary.dashboard.SummaryMetrics

/* =========================
   NAVIGATION
   ========================= */

sealed interface AppScreen {
    data object Loading : AppScreen
    data object Login : AppScreen
    data object Register : AppScreen
    data object Summary : AppScreen
    data object CallHistory : AppScreen
    data object Dashboard : AppScreen
}

/* =========================
   UI STATE
   ========================= */

data class ModelTestResult(
    val status: String = "Idle",
    val selectedFile: String? = null,
    val score: Float? = null
)

data class AppUiState(
    val screen: AppScreen = AppScreen.Loading,
    val currentUser: UserAccount? = null,
    val userSettings: UserSettings = UserSettings(),
    val users: List<UserAccount> = emptyList(),
    val callRecords: List<CallRecord> = emptyList(),

    val summaryMetrics: List<SummaryMetrics> = emptyList(),

    val message: String? = null,
    val isBusy: Boolean = false,
    val modelTest: ModelTestResult = ModelTestResult()
)

/* =========================
   VIEW MODEL
   ========================= */

class AppMainViewModel(application: Application) : AndroidViewModel(application) {

    /* ---------- Local DB ---------- */
    private val db = AppDatabase.getInstance(application)
    private val userRepository = UserRepository(db.userDao())
    private val callRepository = CallRepository(
        db.callDao(),
        db.callMetadataDao(),
        db.detectionResultDao()
    )
    private val alertRepository = AlertRepository(db.alertEventDao())
    private val settingsRepository = SettingsRepository(db.userSettingsDao())

    /* ---------- Firebase ---------- */
    private val firebaseUserDirectory = FirebaseUserDirectory()
    private val userProfileRepository = UserProfileRepository()
    private val usernameService = UsernameService()
    private val pendingUsernameStore = PendingUsernameStore(application)
    private val tokenStore = FCMTokenStore(application)
    private val reviewRepository = ReviewRepository()

    /* ---------- Detection ---------- */
    private val modelRunner = ModelRunner(application)
    private val detectionController = DetectionController(application, modelRunner)
    private val saveDetectionAlert = SaveDetectionAlertUseCase(alertRepository)

    /* ---------- UI ---------- */
    private val alertHandler = InCallAlertHandler(application)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    /* =========================
       INIT
       ========================= */

    init {
        AlertHandlerHolder.handler = alertHandler

        FirebaseAuth.getInstance()
            .addAuthStateListener { auth ->
                val user = auth.currentUser

                if (user != null) {
                    Log.i("AuthListener", "Firebase auth ready :${user.uid}")

                    refreshDashboard()
                }
                else{
                    IncomingCallListener.stop() // stop checking for incoming call on logout
                }
            }

        viewModelScope.launch {
            userRepository.ensureDefaultAdmin()
            _state.update { it.copy(screen = AppScreen.Login) }
        }
    }

    /* =========================
       NAVIGATION
       ========================= */

    fun navigateToRegister() = _state.update { it.copy(screen = AppScreen.Register) }
    fun navigateToLogin() = _state.update { it.copy(screen = AppScreen.Login) }
    fun navigateToDashboard() = _state.update { it.copy(screen = AppScreen.Dashboard) }
    fun navigateToSummary() = _state.update { it.copy(screen = AppScreen.Summary) }
    fun navigateToCallHistory() = _state.update { it.copy(screen = AppScreen.CallHistory) }

    /* =========================
       AUTH
       ========================= */

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }

            runCatching {
                val firebaseUser = FirebaseAuthManager.login(email.trim(), password)

                if (!firebaseUser.isEmailVerified) {
                    error("Please verify your email before logging in.")
                }

                pendingUsernameStore.get()?.let {
                    usernameService.claimUsername(it)
                    pendingUsernameStore.clear()
                }

                val profile = userProfileRepository.getUserProfile(firebaseUser.uid)

                val user = UserAccount(
                    id = firebaseUser.uid.hashCode().toLong(),
                    firebaseUid = firebaseUser.uid,
                    username = profile.username,
                    displayName = profile.displayName.ifBlank { profile.username },
                    role = mapUserRole(profile.role),
                    createdAtSeconds = profile.createdAtSeconds
                )

                user to settingsRepository.get(user.id)
            }.onSuccess { (user, settings) ->
                _state.update {
                    it.copy(
                        currentUser = user,
                        userSettings = settings,
                        screen = AppScreen.Dashboard,
                        isBusy = false
                    )
                }

                if (settings.realTimeDetectionEnabled && hasRecordAudioPermission()) {
                    detectionController.startMonitoring()
                }

                IncomingCallListener.start(getApplication())

                refreshDashboard()
            }.onFailure {
                _state.update {
                    it.copy(isBusy = false, message = it.message ?: "Login failed")
                }
            }
        }
    }

    fun register(email: String, username: String, displayName: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }

            runCatching {
                val cleanUsername = username.trim().lowercase()

                if (!usernameService.checkUsername(cleanUsername)) {
                    error("Username already taken")
                }

                FirebaseAuthManager.register(email.trim(), password)
                FirebaseAuthManager.sendEmailVerification()

                pendingUsernameStore.save(cleanUsername)
            }.onSuccess {
                _state.update {
                    it.copy(
                        screen = AppScreen.Login,
                        isBusy = false,
                        message = "Account created. Verify email, then log in."
                    )
                }
            }.onFailure {
                _state.update {
                    it.copy(isBusy = false, message = it.message ?: "Registration failed")
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            FirebaseAuthManager.logout()
            detectionController.stopMonitoring()
            IncomingCallListener.stop()

            _state.update {
                it.copy(
                    currentUser = null,
                    userSettings = UserSettings(),
                    screen = AppScreen.Login,
                    message = "Logged out",
                    callRecords = emptyList(),
                    users = emptyList()
                )
            }
        }
    }


    /* =========================
       DASHBOARD
       ========================= */

    fun refreshDashboard() {
        val firebaseUser = FirebaseAuthManager.currentUser() ?: return

        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }

            val remoteUsers = firebaseUserDirectory.getAllUsers()

            val mappedUsers = remoteUsers
                .filter { it.uid != firebaseUser.uid }
                .map {
                    UserAccount(
                        id = it.uid.hashCode().toLong(),
                        firebaseUid = it.uid,
                        username = it.username,
                        displayName = it.username,
                        role = UserRole.REGISTERED,
                        createdAtSeconds = 0
                    )
                }
            Log.d("VOIP_DEBUG", "Remote users fetched: ${remoteUsers.size}")

            val calls = callRepository.listRecent()

            _state.update {
                it.copy(
                    users = mappedUsers,
                    callRecords = calls,
                    isBusy = false
                )
            }
        }
    }

    /* =========================
       DETECTION
       ========================= */

    fun setRealTimeDetection(enabled: Boolean) {
        val user = _state.value.currentUser ?: return

        if (enabled && !hasRecordAudioPermission()) {
            _state.update {
                it.copy(
                    message = "Microphone permission required",
                    userSettings = it.userSettings.copy(realTimeDetectionEnabled = false)
                )
            }
            return
        }

        _state.update {
            it.copy(userSettings = it.userSettings.copy(realTimeDetectionEnabled = enabled))
        }

        if (enabled) detectionController.startMonitoring()
        else detectionController.stopMonitoring()

        viewModelScope.launch {
            settingsRepository.update(user.id, _state.value.userSettings)
        }
    }

    /* =========================
       MODEL TEST
       ========================= */

    fun runModelTest(audioFile: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _state.update {
                it.copy(
                    isBusy = true,
                    modelTest = ModelTestResult(status = "Running...", selectedFile = audioFile)
                )
            }

            val result = runCatching {
                modelRunner.inferFromAsset("demo_audio/$audioFile")
            }.getOrNull()

            val probability = result?.score ?: 0f
            val isDeepfake = probability >= _state.value.userSettings.detectionThreshold

            if (isDeepfake) {
                saveDetectionAlert(
                    java.util.UUID.randomUUID().toString(),
                    probability
                )
                AlertHandlerHolder.handler?.displayCriticalAlert(probability)
            }

            _state.update {
                it.copy(
                    isBusy = false,
                    modelTest = ModelTestResult(
                        status = if (result != null) "Done" else "Failed",
                        selectedFile = audioFile,
                        score = probability
                    )
                )
            }

            refreshDashboard()
        }
    }

    /* =========================
       SUMMARY
       ========================= */

    fun aggregateSummary(
        startMillis: Long,
        endMillis: Long,
        daily: Boolean
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }

            val threshold = _state.value.userSettings.detectionThreshold

            val rows = if (daily)
                callRepository.dailyAggregates(startMillis, endMillis, threshold)
            else
                callRepository.weeklyAggregates(startMillis, endMillis, threshold)

            val metrics = rows.map {
                SummaryMetrics(
                    label = it.period,
                    totalCalls = it.total,
                    answered = it.answered,
                    missed = it.missed,
                    suspicious = it.suspicious,
                    blocked = it.blocked,
                    warned = (it.suspicious - it.blocked).coerceAtLeast(0),
                    avgConfidence = it.avgConfidence ?: -1.0
                )
            }

            _state.update {
                it.copy(
                    isBusy = false,
                    summaryMetrics = metrics
                )
            }
        }
    }

    /* =========================
       REVIEWS
       ========================= */

    fun submitReview(rating: Int, description: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }

            runCatching {
                val user = _state.value.currentUser ?: error("User not logged in")
                
                val review = AppReview(
                    userId = user.firebaseUid ?: user.id.toString(),
                    rating = rating,
                    description = description
                )

                reviewRepository.submitReview(review)
            }.onSuccess {
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Thank you for your review!"
                    )
                }
            }.onFailure { ex ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Failed to submit review: ${ex.message}"
                    )
                }
            }
        }
    }

    /* =========================
       PERMISSIONS
       ========================= */

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
