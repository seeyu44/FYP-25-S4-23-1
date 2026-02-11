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
import com.example.fyp_25_s4_23.entity.domain.valueobjects.*
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import com.example.fyp_25_s4_23.util.mapUserRole
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.example.fyp_25_s4_23.control.usecases.SyncContactsUseCase
import com.example.fyp_25_s4_23.data.remote.firebase.PhoneLookupService
import com.example.fyp_25_s4_23.control.call.IncomingCallListener
import com.example.fyp_25_s4_23.boundary.dashboard.SummaryMetrics

import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseAuthManager
import com.example.fyp_25_s4_23.data.remote.firebase.UserProfileRepository
import com.example.fyp_25_s4_23.data.remote.firebase.UsernameService
import com.example.fyp_25_s4_23.data.remote.firebase.GlobalBlockRepository
import com.example.fyp_25_s4_23.data.remote.firebase.GlobalBlockRepository.GlobalBlockedUser

import com.example.fyp_25_s4_23.domain.entities.Contact
import com.example.fyp_25_s4_23.domain.entities.ContactLabel
import com.example.fyp_25_s4_23.util.VibratorUtil


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
    data object Dialer : AppScreen
    data object ContactList : AppScreen
    data object ManagedContacts : AppScreen
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
    val firebaseCalls: List<FirebaseCallRecord> = emptyList(),
    val globalBlockedUsers: List<GlobalBlockedUser> = emptyList(),

    val summaryMetrics: List<SummaryMetrics> = emptyList(),

    val contacts: List<Contact> = emptyList(),
    val message: String? = null,
    val isBusy: Boolean = false,
    val modelTest: ModelTestResult = ModelTestResult(),
    val reviews: List<com.example.fyp_25_s4_23.boundary.dashboard.ReviewWithUserInfo> = emptyList(),
    val auditLogs: List<com.example.fyp_25_s4_23.domain.entities.AuditLog> = emptyList()
)

/* =========================
   VIEW MODEL
   ========================= */

class AppMainViewModel(application: Application) : AndroidViewModel(application) {
        fun toggleUserDisabled(firebaseUid: String, currentlyDisabled: Boolean) {
            viewModelScope.launch {
                _state.update { it.copy(isBusy = true, message = null) }
                runCatching {
                    adminManagementService.setUserDisabled(firebaseUid, !currentlyDisabled)
                }.onSuccess {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            message = if (!currentlyDisabled) "User has been disabled successfully" else "User has been enabled successfully"
                        )
                    }
                    Log.i("AdminManagement", "User $firebaseUid status toggled")
                    refreshDashboard()
                }.onFailure { e ->
                    Log.e("AdminManagement", "Error toggling user status: ${e.message}", e)
                    _state.update {
                        it.copy(
                            isBusy = false,
                            message = "Failed to update user status: ${e.message}"
                        )
                    }
                }
            }
        }
    // Audit log paging state
    private var auditLogPage = 0
    private var auditLogPageSize = 10
    private var auditLogSearch: String? = null

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
    private val auditLogRepository = AuditLogRepository()
    private val adminManagementService = AdminManagementService()
    private val globalBlockRepository = GlobalBlockRepository()

    /* ---------- Detection ---------- */
    private val modelRunner = ModelRunner(application)
    private val detectionController = DetectionController(application, modelRunner)
    private val saveDetectionAlert = SaveDetectionAlertUseCase(alertRepository)

    /* ---------- UI ---------- */
    private val alertHandler = InCallAlertHandler(application)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    /*--------Contacts---------*/
    private val contactRepository = ContactRepository(db.contactDao())
    private val callHistoryRepository = CallHistoryRepository(contactRepository)
    private val firebaseContactRepository = FirebaseContactRepository()
    private val phoneLookupService = PhoneLookupService()

    private val contactSyncUseCase = SyncContactsUseCase(
        firebaseRepo = firebaseContactRepository,
        localRepo = contactRepository,
        phoneLookupService = phoneLookupService
    )

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
                     // stop checking for incoming call on logout
                }
            }

        viewModelScope.launch {
            userRepository.ensureDefaultAdmin()
            _state.update { it.copy(screen = AppScreen.Login, contacts = emptyList()) }
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
    fun navigateToDialer() = _state.update { it.copy(screen = AppScreen.Dialer) }
    fun navigateToContactList() = _state.update { it.copy(screen = AppScreen.ContactList, message = null) }
    fun navigateToManagedContacts() { _state.update { it.copy(screen = AppScreen.ManagedContacts, message = null) } }


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

                // Sync contacts from Firebase; do not block login on failure
                runCatching { contactSyncUseCase.execute() }
                    .onFailure { e ->
                        Log.w("SyncContacts", "Contact sync failed: ${e.message}", e)
                    }

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
            _state.update {
                it.copy(
                    currentUser = null,
                    userSettings = UserSettings(),
                    screen = AppScreen.Login,
                    message = "Logged out",
                    callRecords = emptyList(),
                    users = emptyList(),
                    globalBlockedUsers = emptyList()
                )
            }
        }
    }

    fun createAdminUser(email: String, username: String, displayName: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }

            runCatching {
                val cleanEmail = email.trim()
                val cleanUsername = username.trim().lowercase()
                
                // Validate email format
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                    error("Invalid email format")
                }
                
                // Validate password strength
                if (password.length < 8) error("Password must be at least 8 characters")
                if (!password.any { it.isUpperCase() }) error("Password needs one uppercase letter")
                if (!password.any { it.isLowerCase() }) error("Password needs one lowercase letter")
                if (!password.any { it.isDigit() }) error("Password needs one number")
                if (!password.any { !it.isLetterOrDigit() }) error("Password needs one special character")

                // Check if username already exists
                if (!usernameService.checkUsername(cleanUsername)) {
                    error("Username already taken")
                }
                
                // Check if email already exists (will throw error if email already registered)
                try {
                    FirebaseAuthManager.checkEmailExists(cleanEmail)
                } catch (e: Exception) {
                    error("Email already registered")
                }

                // Create Firebase auth user
                val newUserUid = FirebaseAuthManager.createAdminUser(cleanEmail, password)
                Log.d("CreateAdmin", "Auth user created with UID: $newUserUid")
                
                // Finalize admin user setup via Cloud Function (creates profile with role)
                userProfileRepository.finalizeAdminUser(newUserUid, displayName.trim())
                Log.d("CreateAdmin", "Admin user finalized with role")
                
                // Claim the username (creates public_users entry)
                usernameService.claimUsername(cleanUsername, newUserUid)
                Log.d("CreateAdmin", "Username claimed: $cleanUsername for UID: $newUserUid")
            }.onSuccess {
                refreshDashboard()
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Admin account created successfully"
                    )
                }
            }.onFailure {
                _state.update {
                    it.copy(isBusy = false, message = it.message ?: "Failed to create admin")
                }
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

            Log.d("VOIP_DEBUG", "RefreshDashboard: Current Firebase UID: ${firebaseUser.uid}")
            val remoteUsers = firebaseUserDirectory.getAllUsers()
            Log.d("VOIP_DEBUG", "Remote users fetched: ${remoteUsers.size}")
            remoteUsers.forEach { user ->
                Log.d("VOIP_DEBUG", "Remote user: uid=${user.uid}, username=${user.username}")
            }

            val mappedUsers = remoteUsers
                .filter { it.uid != firebaseUser.uid }
                .map {
                    UserAccount(
                        id = it.uid.hashCode().toLong(),
                        firebaseUid = it.uid,
                        username = it.username,
                        displayName = it.displayName,
                        role = UserRole.REGISTERED,
                        createdAtSeconds = 0,
                        isDisabled = it.disabled
                    )
                }
            Log.d("VOIP_DEBUG", "Mapped users after filtering: ${mappedUsers.size}")
            mappedUsers.forEach { user ->
                Log.d("VOIP_DEBUG", "Mapped user: id=${user.id}, firebaseUid=${user.firebaseUid}, username=${user.username}")
            }

            val calls = callRepository.listRecent()

            val flaggedUsers = runCatching {
                globalBlockRepository.listFlaggedUsers()
            }.onFailure { e ->
                Log.e("GlobalBlock", "Failed to load flagged users: ${e.message}", e)
            }.getOrDefault(emptyList())

            _state.update {
                it.copy(
                    users = mappedUsers,
                    callRecords = calls,
                    globalBlockedUsers = flaggedUsers,
                    isBusy = false
                )
            }
            
            // Load Firebase call history for dashboard display
            loadFirebaseCallHistory()
            
            // Load reviews for admin
            loadReviews()
            
            // Load audit logs for admin
            loadAuditLogs()
        }
    }

    fun blacklistGlobalUser(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching {
                globalBlockRepository.updateLabel(userId, "blacklisted")
            }.onSuccess {
                _state.update {
                    it.copy(
                        globalBlockedUsers = it.globalBlockedUsers.filterNot { user -> user.userId == userId },
                        isBusy = false,
                        message = "User blacklisted"
                    )
                }
            }.onFailure { ex ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Failed to blacklist: ${ex.message}"
                    )
                }
            }
        }
    }

    fun removeGlobalBlockedUser(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching {
                globalBlockRepository.removeUser(userId)
            }.onSuccess {
                _state.update {
                    it.copy(
                        globalBlockedUsers = it.globalBlockedUsers.filterNot { user -> user.userId == userId },
                        isBusy = false,
                        message = "User removed from global block list"
                    )
                }
            }.onFailure { ex ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Failed to remove user: ${ex.message}"
                    )
                }
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
            // Bundled audio testing is just for model validation - don't trigger alerts
            // The UI already shows the result in ModelTestScreen

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

            Log.i("SummaryDebug", "Querying summary: start=$startMillis, end=$endMillis, daily=$daily, threshold=$threshold")
            Log.i("SummaryDebug", "Query in seconds: start=${startMillis/1000}, end=${endMillis/1000}")

            val rows = if (daily)
                callRepository.dailyAggregates(startMillis, endMillis, threshold)
            else
                callRepository.weeklyAggregates(startMillis, endMillis, threshold)

            Log.i("SummaryDebug", "Query returned ${rows.size} rows")

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

    fun submitReview(rating: Int, description: String, anonymous: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }

            runCatching {
                val user = _state.value.currentUser ?: error("User not logged in")
                
                val review = AppReview(
                    userId = user.firebaseUid ?: user.id.toString(),
                    rating = rating,
                    description = description,
                    anonymous = anonymous
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
       CALL HISTORY
       ========================= */

    /**
     * Fetch call history from Firebase Cloud Function
     * Queries calls where user is either caller or callee
     */
    fun loadFirebaseCallHistory(limit: Int = 50) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }

            runCatching {
                callHistoryRepository.getCallHistory(limit)
            }.onSuccess { response ->
                Log.d("CallHistory", "Loaded ${response.calls.size} calls from Firebase")
                _state.update {
                    it.copy(
                        isBusy = false,
                        firebaseCalls = response.calls,
                        message = if (response.calls.isEmpty()) "No calls in history" else null
                    )
                }
            }.onFailure { ex ->
                Log.e("CallHistory", "Error loading call history: ${ex.message}", ex)
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Failed to load call history: ${ex.message}"
                    )
                }
            }
        }
    }

    /**
     * End an active call and update its status in Firebase
     */
    fun endFirebaseCall(callId: String, durationSeconds: Long) {
        viewModelScope.launch {
            runCatching {
                callHistoryRepository.endCall(callId, durationSeconds, "completed")
            }.onSuccess {
                Log.d("CallHistory", "Call ended successfully: $callId")
                // Refresh call history after ending call
                loadFirebaseCallHistory()
            }.onFailure { ex ->
                Log.e("CallHistory", "Error ending call: ${ex.message}", ex)
                _state.update {
                    it.copy(message = "Failed to end call: ${ex.message}")
                }
            }
        }
    }

    /* =========================
       USER MANAGEMENT
       ========================= */

    fun disableUser(firebaseUid: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            
            runCatching {
                adminManagementService.setUserDisabled(firebaseUid, true)
            }.onSuccess {
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "User has been disabled successfully"
                    )
                }
                Log.i("AdminManagement", "User $firebaseUid disabled")
                // Refresh dashboard to update users list
                refreshDashboard()
            }.onFailure { e ->
                Log.e("AdminManagement", "Error disabling user: ${e.message}", e)
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Failed to disable user: ${e.message}"
                    )
                }
            }
        }
    }

    fun deleteUser(firebaseUid: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            
            runCatching {
                adminManagementService.deleteUser(firebaseUid)
            }.onSuccess {
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "User has been deleted permanently"
                    )
                }
                Log.i("AdminManagement", "User $firebaseUid deleted")
                // Refresh dashboard to update users list
                refreshDashboard()
            }.onFailure { e ->
                Log.e("AdminManagement", "Error deleting user: ${e.message}", e)
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Failed to delete user: ${e.message}"
                    )
                }
            }
        }
    }

    /* =========================
       REVIEW MANAGEMENT
       ========================= */

    fun loadReviews() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }

            runCatching {
                val allReviews = reviewRepository.getAllReviews()
                
                // Map reviews with user display names
                val reviewsWithUserInfo = allReviews.map { review ->
                    val user = _state.value.users.find { it.firebaseUid == review.userId }
                    val displayName = user?.displayName ?: "Anonymous User"
                    
                    com.example.fyp_25_s4_23.boundary.dashboard.ReviewWithUserInfo(
                        review = review,
                        userDisplayName = displayName
                    )
                }

                _state.update {
                    it.copy(
                        isBusy = false,
                        reviews = reviewsWithUserInfo
                    )
                }
            }.onFailure { ex ->
                Log.e("ReviewManagement", "Error loading reviews", ex)
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Failed to load reviews: ${ex.message}"
                    )
                }
            }
        }
    }

    fun deleteReview(reviewId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }

            runCatching {
                adminManagementService.deleteReview(reviewId)
            }.onSuccess {
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Review deleted successfully",
                        reviews = it.reviews.filter { item -> item.review.id != reviewId }
                    )
                }
                Log.i("ReviewManagement", "Review $reviewId deleted")
            }.onFailure { ex ->
                Log.e("ReviewManagement", "Error deleting review", ex)
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Failed to delete review: ${ex.message}"
                    )
                }
            }
        }
    }

    /* =========================
       AUDIT LOGS
       ========================= */

    fun loadAuditLogs(page: Int = 0, pageSize: Int = 10, search: String? = null) {
        auditLogPage = page
        auditLogPageSize = pageSize
        auditLogSearch = search
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching {
                auditLogRepository.getAuditLogsPaged(auditLogPage, auditLogPageSize, auditLogSearch)
            }.onSuccess { logs ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        auditLogs = logs
                    )
                }
            }.onFailure { ex ->
                Log.e("AuditLogManagement", "Error loading audit logs", ex)
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = "Failed to load audit logs: ${ex.message}"
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
