# Phase 1 Refactoring: Sequence Diagram Mapping

## Overview
Phase 1 refactoring introduces Hilt DI and splits the 838-line AppMainViewModel into feature-specific ViewModels. This document maps the old call sequences to new ones for your 8 documented flows.

---

## Flow 1: Login/Logout Sequence

### BEFORE (Current State)
```
User Input → LoginScreen.login()
          ↓
       AppMainViewModel.login()
       ├→ FirebaseAuthManager.signInWithEmail()
       ├→ get User from Firestore
       ├→ UserRepository.ensureDefaultAdmin()
       └→ _state.update(currentUser = user)
          ↓
       AppScreen.Summary (navigation happens via state)
```

### AFTER (Phase 1 + Hilt)
```
User Input → LoginScreen.login()
          ↓
       LoginViewModel.login() [NEW - @HiltViewModel]
       ├→ FirebaseAuthManager (injected)
       ├→ UserRepository (injected)
       └→ emit _loginState: StateFlow<LoginState>
          ├→ LoginState.Loading
          ├→ LoginState.Success(user)
          └→ LoginState.Error(message)
          ↓
       MainActivity observes LoginState.Success
       └→ navigate to AppScreen.Summary
          
       logout() → LoginViewModel.logout()
       └→ emit _loginState.value = LoginState.Idle
```

### Code Changes
- **Created**: `LoginViewModel.kt` with login/logout methods
- **Removed**: `AppMainViewModel.login()`, `AppMainViewModel.logout()` methods
- **Updated**: `LoginScreen.kt` to call `loginViewModel.login()` instead of `appViewModel.login()`
- **Updated**: `MainActivity.kt` to subscribe to `loginViewModel.loginState` for navigation

---

## Flow 2: Call Signalling Sequence

### BEFORE (Current State)
```
Incoming Call Event
          ↓
CallInProgressActivity.onCreate()
├→ manually instantiate FirebaseSignalingManager()
├→ manually instantiate GlobalBlockRepository()
├→ lifecycleScope.launch { contact lookup }
├→ create WebRtcClient
├→ AppMainViewModel.onDeepfakeFlagged callback
└→ emit setRinging/setActive via AppMainViewModel
          ↓
Firebase receives offer/answer/ice-candidates
          ↓
WebRtcClient processes and updates state
```

### AFTER (Phase 1 + Hilt)
```
Incoming Call Event
          ↓
CallInProgressActivity.onCreate() [THIN]
├→ viewModel: CallDetectionViewModel (injected via @ViewModelInject)
└→ Compose UI observes viewModel.callState: StateFlow
          ↓
CallDetectionViewModel [NEW - @HiltViewModel]
├→ FirebaseSignalingManager (injected)
├→ GlobalBlockRepository (injected)
├→ ContactRepository (injected)
├→ fun resolveContactInfo()
│   ├→ contact lookup async
│   └→ emit _contactName.value
├→ fun attachWebRtcClient()
├→ fun onDeepfakeFlagged()
│   ├→ globalBlockRepository.flagUser() (injected)
│   └→ emit _signallingState.value = Blocked
└→ fun listenToFirebaseSignalling()
    ├→ offer/answer/ice-candidates
    └→ emit _signallingState.value = CallState
```

### Code Changes
- **Created**: `CallDetectionViewModel.kt` with @HiltViewModel
- **Removed**: Manual instantiation in CallInProgressActivity (lines 57, 120)
- **Removed**: Lines 73-165 business logic from Activity (contact lookup, deepfake flagging)
- **Removed**: `private var displayName`, `hasFlaggedGlobalBlock`, `isRemoteKnownContact` (move to ViewModel StateFlow)
- **Updated**: CallInProgressActivity to receive injected ViewModel and observe StateFlow
- **Updated**: WebRTC callback chain to update ViewModel StateFlow instead of direct state

---

## Flow 3: Call Monitoring Sequence

### BEFORE (Current State)
```
Incoming Call Monitoring
          ↓
CallMonitoringService
├→ AppMainViewModel.recordCallWithDetection()
├→ ModelRunner.detectDeepfake()
├→ SaveDetectionAlertUseCase.execute()
├→ CallRepository.upsert()
└→ emit _state.update(callRecords = ...)
          ↓
Summary/CallHistory updates
```

### AFTER (Phase 1 + Hilt)
```
Incoming Call Monitoring
          ↓
CallMonitoringService (unchanged interface)
├→ CallDetectionViewModel.recordAndAnalyze() [MOVED]
│   ├→ ModelRunner (injected)
│   ├→ CallRepository (injected)
│   ├→ SaveDetectionAlertUseCase (injected)
│   ├→ AlertRepository (injected)
│   └→ emit _callMonitoringState.value
│       └→ callRecords: List<CallRecord>
├→ AppMainViewModel subscribes to CallDetectionViewModel.callMonitoringState
│   └→ updates _state.callRecords
          ↓
Summary/CallHistory subscribe to AppMainViewModel.state.callRecords
```

### Code Changes
- **Extracted**: Call monitoring logic from AppMainViewModel into CallDetectionViewModel
- **Created**: `callMonitoringState: StateFlow<CallMonitoringState>` in CallDetectionViewModel
- **Updated**: CallMonitoringService to call `callDetectionViewModel.recordAndAnalyze()`
- **Updated**: AppMainViewModel to subscribe to CallDetectionViewModel outputs (instead of doing work itself)
- **Removed**: ~100 lines of call recording logic from AppMainViewModel

---

## Flow 4: Deepfake Detection Sequence

### BEFORE (Current State)
```
Model Detection Complete
          ↓
AppMainViewModel.onDetectionResult()
├→ SaveDetectionAlertUseCase.execute(detectionResult)
├→ CallRepository.upsert()
├→ AlertRepository.upsert()
├→ GlobalBlockRepository.flagUser() [if threshold exceeded]
└→ emit AlertEvent via viewModel
          ↓
CallInProgressScreen receives vibrate event
          ↓
VibratorUtil.vibrate()
```

### AFTER (Phase 1 + Hilt)
```
Model Detection Complete
          ↓
CallDetectionViewModel.onDetectionResult() [MOVED]
├→ SaveDetectionAlertUseCase (injected)
├→ CallRepository (injected)
├→ AlertRepository (injected)
├→ if threshold exceeded:
│   └→ GlobalBlockRepository.flagUser() (injected)
├→ emit _detectionState.value = DetectionState
│   └→ Contains AlertEvent + isDeepfake + score
└→ emit _events: Flow<DetectionEvent>
              └→ DetectionEvent.ShowAlert(score)
          ↓
CallInProgressActivity observes _events
          ↓
VibratorUtil.vibrate()
```

### Code Changes
- **Extracted**: Deepfake detection logic from AppMainViewModel to CallDetectionViewModel
- **Created**: `detectionState: StateFlow<DetectionState>` 
- **Created**: `events: Flow<DetectionEvent>` for one-shot events (vibration)
- **Updated**: ModelRunner callback to call `callDetectionViewModel.onDetectionResult()`
- **Removed**: Global block logic from AppMainViewModel.onDeepfakeFlagged
- **Removed**: AlertEvent emission from AppMainViewModel

---

## Flow 5: Admin Statistics Sequence

### BEFORE (Current State)
```
AdminDashboard.LaunchedEffect
          ↓
AppMainViewModel.loadAdminStats()
├→ CallRepository.dailyAggregates()
├→ AdminManagementService.getStats()
├→ FirebaseUserDirectory.getAllUsers()
└→ emit _state.update(summaryMetrics = ...)
          ↓
AdminDashboard observes appViewModel.state.summaryMetrics
```

### AFTER (Phase 1 + Hilt)
```
AdminDashboard.LaunchedEffect
          ↓
AdminViewModel.loadAdminStats() [NEW - @HiltViewModel]
├→ CallRepository (injected)
├→ AdminManagementService (injected)
├→ FirebaseUserDirectory (injected)
└→ emit _adminState: StateFlow<AdminState>
    └→ Contains summaryMetrics, stats, usersList
              ↓
AdminDashboard observes adminViewModel.adminState
```

### Code Changes
- **Created**: `AdminViewModel.kt` with @HiltViewModel
- **Removed**: Admin operations from AppMainViewModel (~80 lines)
- **Updated**: AdminDashboard to use `AdminViewModel` instead of AppMainViewModel
- **Updated**: All admin callbacks to call `adminViewModel.method()` instead of `appViewModel.method()`
- **Removed**: `summaryMetrics`, admin-related methods from AppMainViewModel

---

## Hilt DI Setup

### New Dependencies Module
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    
    @Provides
    @Singleton
    fun provideDatabase(context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }
    
    @Provides
    fun provideUserRepository(db: AppDatabase): UserRepository {
        return UserRepository(db.userDao())
    }
    
    @Provides
    fun provideCallRepository(db: AppDatabase): CallRepository {
        return CallRepository(db.callDao(), db.callMetadataDao(), db.detectionResultDao())
    }
    
    @Provides
    fun provideContactRepository(db: AppDatabase): ContactRepository {
        return ContactRepository(db.contactDao())
    }
    
    @Provides
    fun provideSettingsRepository(db: AppDatabase): SettingsRepository {
        return SettingsRepository(db.userSettingsDao())
    }
    
    @Provides
    fun provideAlertRepository(db: AppDatabase): AlertRepository {
        return AlertRepository(db.alertEventDao())
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    @Provides
    @Singleton
    fun provideFirebaseAuthManager(): FirebaseAuthManager {
        return FirebaseAuthManager()
    }
    
    @Provides
    @Singleton
    fun provideGlobalBlockRepository(): GlobalBlockRepository {
        return GlobalBlockRepository()
    }
    
    @Provides
    @Singleton
    fun provideFirebaseSignalingManager(): FirebaseSignalingManager {
        return FirebaseSignalingManager()
    }
    
    @Provides
    @Singleton
    fun provideModelRunner(context: Context): ModelRunner {
        return ModelRunner(context)
    }
}
```

### ViewModel Annotations
```kotlin
// LoginViewModel
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val firebaseAuthManager: FirebaseAuthManager,
    private val userRepository: UserRepository
) : ViewModel() { ... }

// CallDetectionViewModel
@HiltViewModel
class CallDetectionViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val globalBlockRepository: GlobalBlockRepository,
    private val firebaseSignalingManager: FirebaseSignalingManager,
    private val callRepository: CallRepository,
    private val alertRepository: AlertRepository,
    private val saveDetectionAlertUseCase: SaveDetectionAlertUseCase,
    private val modelRunner: ModelRunner
) : ViewModel() { ... }

// AdminViewModel
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val callRepository: CallRepository,
    private val adminManagementService: AdminManagementService,
    private val firebaseUserDirectory: FirebaseUserDirectory
) : ViewModel() { ... }

// AppMainViewModel (after Phase 1)
@HiltViewModel
class AppMainViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val contactRepository: ContactRepository,
    private val loginViewModel: LoginViewModel,
    private val callDetectionViewModel: CallDetectionViewModel,
    private val adminViewModel: AdminViewModel
) : ViewModel() {
    // ONLY handles: navigation state, observes feature ViewModels
}
```

---

## AppMainViewModel After Phase 1

### BEFORE: 838 lines
- Authentication logic
- Call monitoring logic
- Deepfake detection logic
- Admin operations logic
- 20+ dependencies manually instantiated
- All app state in single _state StateFlow

### AFTER: ~150 lines
```kotlin
@HiltViewModel
class AppMainViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val contactRepository: ContactRepository,
    // Feature ViewModels (dependencies injected into them separately)
) : ViewModel() {
    
    // Navigation state only
    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()
    
    private val _currentUser = MutableStateFlow<UserAccount?>(null)
    val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()
    
    // Subscribe to feature ViewModels and aggregate
    fun initializeApp() {
        viewModelScope.launch {
            userProfileRepository.getCurrentUser().collect { user ->
                _currentUser.value = user
                if (user != null) {
                    _screen.value = AppScreen.Summary
                } else {
                    _screen.value = AppScreen.Login
                }
            }
        }
    }
}
```

**Benefits:**
- ✅ 838 → 150 lines (~82% reduction)
- ✅ Single responsibility: navigation state only
- ✅ All dependencies injected by Hilt (no manual instantiation)
- ✅ Each feature isolated in its own ViewModel
- ✅ Testable: each ViewModel can be tested independently
- ✅ Reusable: features can be moved between screens without coupling

---

## File Changes Summary

| File | Action | Lines | Reason |
|------|--------|-------|--------|
| AppMainViewModel.kt | **Refactor** | 838 → 150 | Extract features to dedicated ViewModels |
| LoginViewModel.kt | **Create** | ~120 | New authentication ViewModel |
| CallDetectionViewModel.kt | **Create** | ~300 | New call detecting & signaling ViewModel |
| AdminViewModel.kt | **Create** | ~180 | New admin operations ViewModel |
| CallInProgressActivity.kt | **Refactor** | 301 → 180 | Remove business logic, inject ViewModel |
| AdminDashboard.kt | **Refactor** | 532 → 400 | Use AdminViewModel instead of AppMainViewModel |
| LoginScreen.kt | **Refactor** | ? → ? | Call LoginViewModel instead of AppMainViewModel |
| MainActivity.kt | **Refactor** | ? → ? | Inject feature ViewModels, observe states |
| DataModule.kt | **Create** | ~60 | Hilt dependency injection configuration |
| ServiceModule.kt | **Create** | ~40 | Hilt service dependency injection |
| build.gradle.kts | **Update** | +3 lines | Add Hilt dependency & KSP plugin |

**Total code added**: ~700 lines
**Total code removed**: ~500 lines  
**Net change**: +200 lines (but much more organized)

---

## Implementation Order

1. ✅ Add Hilt dependencies to `build.gradle.kts`
2. ✅ Create Hilt modules (DataModule, ServiceModule)
3. ✅ Create LoginViewModel with @HiltViewModel
4. ✅ Create CallDetectionViewModel with @HiltViewModel
5. ✅ Create AdminViewModel with @HiltViewModel
6. ✅ Update AppMainViewModel to aggregate feature ViewModels
7. ✅ Update LoginScreen to use LoginViewModel
8. ✅ Update CallInProgressActivity to use CallDetectionViewModel
9. ✅ Update AdminDashboard to use AdminViewModel
10. ✅ Update MainActivity to handle Hilt @HiltAndroidApp
11. ✅ Test all 5 flows for proper state emission
12. ✅ Update sequence diagrams with new flows

---

## Breaking Changes

| Component | Old API | New API | Impact |
|-----------|---------|---------|--------|
| AppMainViewModel.login() | Exists | **Removed** | LoginScreen must use LoginViewModel |
| AppMainViewModel.recordCallWithDetection() | Exists | **Removed** | CallMonitoringService uses CallDetectionViewModel |
| AppMainViewModel.onDetectionResult() | Exists | **Removed** | ModelRunner callback uses CallDetectionViewModel |
| AppMainViewModel.loadAdminStats() | Exists | **Removed** | AdminDashboard uses AdminViewModel |
| CallInProgressActivity | Instantiates dependencies | Injects viewModel | Slightly fewer constructor params |

---

## Testing Strategy

### Unit Tests
- LoginViewModel.login() with mocked FirebaseAuthManager
- CallDetectionViewModel.resolveContactInfo() with mocked ContactRepository
- AdminViewModel.loadAdminStats() with mocked services

### Integration Tests
- Flow: User login → screen navigation
- Flow: Incoming call → contact resolution → deepfake flag
- Flow: Admin loads statistics → metrics displayed

---

## Success Criteria

After Phase 1 is complete:
- ✅ AppMainViewModel < 200 lines (was 838)
- ✅ Zero manual dependency instantiation via `getInstance()` calls
- ✅ All feature logic in dedicated ViewModels with @HiltViewModel
- ✅ Zero compilation errors
- ✅ All 5 documented flows working per new sequence diagrams
- ✅ App architecture score improves from 6/10 to 7.5/10

