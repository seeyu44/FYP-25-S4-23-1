# Deepfake Speech Detection (Android)

This repository contains an Android app (Kotlin + Jetpack Compose) and an ML training skeleton (Python) for detecting deepfake speech during calls. The code follows a **Boundary–Control–Entity (BCE) MVVM** architectural pattern with clear separation of concerns.

## Project Structure

```
app/src/main/java/com/example/fyp_25_s4_23/
├── boundary/
│   ├── auth/          – LoginScreen, authentication UI
│   ├── dashboard/     – DashboardScreen, SummaryScreen, admin panels
│   ├── call/          – CallInProgressScreen, in-call UI
│   ├── callhistory/   – CallHistoryScreen, call records and contacts
│   └── handlers/      – CallMonitorService, InCallAlertHandler
├── control/
│   ├── viewmodel/     – AppMainViewModel, LoginViewModel, AdminViewModel, CallHistoryViewModel
│   ├── controllers/   – DetectionController, SystemController, AlertHandlerHolder
│   ├── usecases/      – SyncContactsUseCase, SaveDetectionAlertUseCase
│   └── call/          – IncomingCallListener, call management
├── entity/
│   ├── data/          – SQLite database, DAOs, repositories, entity models
│   │   ├── db/        – AppDatabase with migrations
│   │   ├── dao/       – Data access objects
│   │   ├── entities/  – Database entity models
│   │   ├── repositories/ – Local repositories (User, Call, Contact, Alert, etc.)
│   │   └── mappers/   – Entity mappers
│   ├── domain/        – Domain models, value objects, FirebaseCallRecord
│   └── ml/            – ModelRunner, on-device inference
├── data/remote/
│   └── firebase/      – Firebase services, authentication, cloud functions
├── util/              – Utility functions and helpers
└── MainActivity.kt    – Entry point, system setup
```

Supporting directories:
- `app/src/main/assets` – Model artifacts (synced via scripts)
- `ml/training/` – Python training code and scripts
- `ml/model/` – Exported ML model artifacts
- `scripts/` – Helper scripts (e.g., `sync_model.ps1`)

## Getting Started (Android)

1. **Open and Build**: Open the root folder in Android Studio and build the app.

2. **Login**: The app launches on the Login screen (Firebase Auth required).
   - Admin accounts must be created directly via the Admin Dashboard.
   - Registered users log in with their email and password (email verification required).

3. **Admin Dashboard**: Admins can:
   - Create additional admin or registered user accounts
   - View all registered users and their activity
   - View call history and detection alerts
   - Manage user permissions and review user behavior

4. **Registered User Dashboard**: Registered users can:
   - View their own call summary and history
   - See incoming call statistics
   - Manage their contacts and call settings
   - Toggle "Real-time Deepfake Detection" to enable/disable background detection

5. **Enable Detection**: Toggle the "Real-time Deepfake Detection" switch on the dashboard to start/stop the foreground detection service. (Disabled by default to save battery.)

6. **Make Calls**: 
   - Ensure the app is set as the default dialer (Dashboard → Call Lab)
   - Grant `CALL_PHONE` and `ANSWER_PHONE_CALLS` permissions when prompted
   - Dial numbers and route calls through the Anti-Deepfake InCall UI
   - The in-call screen pops over the lock screen during active calls

**Required Permissions**:
- `RECORD_AUDIO` – for audio capture and analysis
- `READ_PHONE_STATE` – to monitor call state
- `POST_NOTIFICATIONS` – for alerts and status updates
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` – for background detection

**Note**: Access to in-call audio is restricted on Android 10+. Consider role-based call screening or user-initiated monitoring where lawful.

## Architecture Overview

The app follows **BCE (Boundary-Control-Entity) MVVM** pattern:

- **Boundary**: Composable UI screens (LoginScreen, DashboardScreen, SummaryScreen, etc.) and system handlers (CallMonitorService)
- **Control**: ViewModels (AppMainViewModel, LoginViewModel, AdminViewModel, CallHistoryViewModel) and business logic controllers
- **Entity**: Domain models, database entities, and repositories for both local SQLite and remote Firebase

**Key Features**:
- **ViewModels**: Centralized state management with `StateFlow` for reactive UI updates
- **Repositories**: Abstracted data access (local SQLite + remote Firebase)
- **Feature ViewModels**: Specialized VMs for each major feature (Login, Admin, Call History)
- **Coroutine-based**: Async/await for parallel data loading and responsive UI
- **Dependency Injection**: Manual singleton pattern for shared services

## Recent Improvements

### Performance Optimization (Feb 2026)
- **Caching**: CallHistoryRepository now caches results for 5 minutes to reduce network calls
- **Parallel Loading**: Dashboard and call history load concurrently on login using `async/await`
- **Eager Loading**: Firebase call history loads automatically on login (no more empty screens)
- **Result**: Significantly reduced latency for Summary and Call History screens

### Data Layer Consolidation
- Eliminated duplicate data layer files (12 files removed)
- Single source of truth: `entity/data/` contains all database, DAO, entity, and repository definitions
- Removed old mappings in `data/` folder (cleanup complete)

### Registration Removal
- App no longer supports user self-registration
- Admin accounts created via Admin Dashboard only
- Streamlined login flow (Firebase Auth with email verification)

## ML Artifacts

Train or export your model into `ml/model/`, then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync_model.ps1
```

This script copies artifacts to `app/src/main/assets/model/`.

## Call Monitoring & Dialer

- **Default Dialer**: Set the app as default dialer from the Dashboard (Call Lab → "Request default dialer role") so that `CallMonitorService` is launched for every call.
- **Incoming/Outgoing Calls**: Both automatically trigger `CallMonitorService`, which captures `AudioRecord` frames and feeds them into `ModelRunner` for on-device scoring.
- **In-Call UI**: A floating `CallInProgressScreen` pops over the lock screen during active calls, allowing users to answer, mute, and hang up while detection runs in the background.
- **Permissions**: Users must grant `CALL_PHONE` and `ANSWER_PHONE_CALLS` permissions for the dialer to function.

## Testing & Development

**Local Testing**:
- Use the Testing Lab (admin only) to seed sample calls/alerts into the local SQLite database
- Test detection logic without relying on live Firebase data
- Each teammate can work on their function locally

**Firebase Integration**:
- Requires Firebase project and `google-services.json` in `app/`
- Admin Dashboard syncs with Firestore for user management
- Call history cached from Cloud Functions (`getCallHistory`, `endCall`)
- Email verification required for login
