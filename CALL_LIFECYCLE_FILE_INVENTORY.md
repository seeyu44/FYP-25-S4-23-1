# Complete File Inventory - Call Lifecycle System

## 📦 All Files Organized by Feature & Role

### 1️⃣ CALL INITIATION & MANAGEMENT (9 Files)

#### Outgoing Call
- [boundary/call/VOIPCallManager.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/VOIPCallManager.kt)
  - `startOutgoingVoipCall()` - Entry point for user-initiated calls
  - Generates unique callId
  - Creates Firebase call document
  - Launches CallInProgressActivity

#### Incoming Call Detection
- [control/call/IncomingCallListener.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallListener.kt)
  - `start()` / `stop()` - Firestore listener lifecycle
  - Watches for ringing calls for current user
  - Triggers notification on new call
  - Filters by timestamp to avoid old calls

- [control/call/IncomingCallNotifier.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallNotifier.kt)
  - Show incoming call notification UI
  - Handle user actions from notification

- [control/call/IncomingCallIntent.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallIntent.kt)
  - Intent extra constants
  - EXTRA_CALL_ID, EXTRA_REMOTE_USER_ID, etc.

#### Call State Storage
- [control/call/ActiveCallStore.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/ActiveCallStore.kt)
  - In-memory call state (StateFlow)
  - `update()`, `setWebRtcActive()`, `clear()`
  - Tracks callId, remoteUserId, state

#### Services
- [boundary/handlers/CallMonitorService.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/handlers/CallMonitorService.kt)
  - Background service managing IncomingCallListener
  - Lifecycle management

- [boundary/handlers/AntiDeepfakeInCallService.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/handlers/AntiDeepfakeInCallService.kt)
  - In-call deepfake monitoring service
  - Alert handling

- [boundary/handlers/VOIPMessagingService.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/handlers/VOIPMessagingService.kt)
  - Firebase Cloud Messaging for call notifications

---

### 2️⃣ UI & VIEW MANAGEMENT (4 Files)

#### Activity
- [boundary/call/CallInProgressActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressActivity.kt) ⭐
  - Main activity for active call UI
  - Orchestrates WebRTC client initialization
  - Handles permissions (RECORD_AUDIO)
  - Manages lifecycle (onCreate, onDestroy)
  - Lines 1-235

#### ViewModel
- [boundary/call/CallInProgressViewModel.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressViewModel.kt)
  - UI state management
  - `CallUiState` sealed class (Connecting, Ringing, Active, Disconnected)
  - Callbacks: `onStartCallRequested`, `onCallEnded`
  - Audio controls: mute, speaker, audio state

#### Compose UI
- [boundary/call/CallInProgressScreen.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressScreen.kt)
  - Jetpack Compose UI for call screen
  - Call status display
  - Control buttons (Answer, Mute, Speaker, Hang Up)
  - Demo audio selection UI

#### Call History UI
- [boundary/callhistory/CallHistoryScreen.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/callhistory/CallHistoryScreen.kt)
  - Display past call records
  - Linked to CallHistoryRepository

---

### 3️⃣ WEBRTC CORE ENGINE (3 Files) ⭐ CRITICAL

#### WebRTC Client
- [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) ⭐⭐⭐
  - **1278 lines** - Core WebRTC engine
  - PeerConnection setup & management
  - Audio track creation & control
  - SDP offer/answer handling
  - ICE candidate handling
  - Deepfake detection initialization
  - Audio monitoring & stats
  - Call cleanup

**Key Methods**:
- `initialize()` (108) - PeerConnectionFactory setup
- `createAudioTrack()` (155) - Audio source + track
- `createPeerConnection()` (489) - Peer connection setup
- `createOffer()` (800) - SDP offer generation
- `createAnswer()` (930) - SDP answer generation
- `onRemoteOfferReceived()` (900) - Handle offer
- `onRemoteAnswerReceived()` (950) - Handle answer
- `answerIncomingCall()` (880) - User action
- `startDeepfakeDetection()` (850) - Initialize detection
- `attachIncomingDetectionSink()` (200) - Audio to detection
- `setLocalAudioEnabled()` (240) - Mute/unmute
- `setSpeakerEnabled()` (280) - Speaker toggle
- `setupAudioRouting()` (260) - Audio mode config
- `hangUp()` / `engineEnd()` (1100) - Cleanup

#### Firebase Signaling
- [control/webrtc/FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt) ⭐⭐⭐
  - **280 lines** - Firestore coordination
  - Call document creation & updates
  - SDP offer/answer transmission
  - ICE candidate exchange
  - Call status synchronization

**Key Methods**:
- `createCall()` (20) - Create Firestore call doc
- `listenToCall()` (49) - Listen for offer/answer/status
- `sendOffer()` (120) - Send SDP offer
- `sendAnswer()` (135) - Send SDP answer
- `sendIceCandidate()` (185) - Send ICE candidate
- `listenForIceCandidates()` (200) - Listen for candidates
- `updateCallStatus()` (165) - Update call status
- `endCall()` (175) - Mark call ended
- `stopListening()` (250) - Cleanup listeners

#### ViewModel (Alternative)
- [control/webrtc/WebRtcViewModel.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRtcViewModel.kt)
  - Alternative ViewModel for WebRTC lifecycle
  - May be used in some call flows

---

### 4️⃣ DEEPFAKE DETECTION (2 Files) ⭐ CRITICAL

#### Detection Service
- [control/detection/DeepfakeDetectionService.kt](app/src/main/java/com/example/fyp_25_s4_23/control/detection/DeepfakeDetectionService.kt) ⭐⭐⭐
  - **376 lines** - Real-time detection engine
  - Audio buffer management
  - Inference scheduling
  - SQLite persistence
  - Detection callbacks

**Key Methods**:
- `startMonitoring()` (80) - Begin detection loop
- `feedAudioChunk()` (150) - Queue audio samples
- Detection loop (100+) - Inference + DB writes
- `stopMonitoring()` (250) - Stop & cleanup
- `pauseDetection()` / `resumeDetection()` - Demo audio control

**Callbacks**:
- `onDeepfakeDetected: (Float) -> Unit` - Detection alert
- `onDetectionUpdate: (DetectionResult) -> Unit` - Stats update

#### Model Runner
- [entity/ml/ModelRunner.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/ml/ModelRunner.kt)
  - PyTorch model inference
  - `warmUp()` - Model initialization
  - `predict(floatArray): FloatArray` - Run inference
  - Uses melcnn.pt model from assets

---

### 5️⃣ LOCAL DATABASE (Room/SQLite) (10 Files)

#### Database Core
- [entity/data/db/AppDatabase.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/db/AppDatabase.kt) ⭐
  - Room database singleton
  - **Version 7** with migrations
  - Entity definitions
  - DAO declarations

**Entities**:
- UserEntity
- UserSettingsEntity
- CallEntity
- CallMetadataEntity
- DetectionResultEntity
- AlertEventEntity
- ContactEntity

#### Data Access Objects (DAOs)
- [entity/data/dao/CallDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/CallDao.kt)
  - `insert()`, `update()`, `getById()`, `getAllCalls()`

- [entity/data/dao/CallRecordDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/CallRecordDao.kt)
  - `insert()`, `getByCallId()`, `getAllRecords()`

- [entity/data/dao/CallMetadataDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/CallMetadataDao.kt)
  - Call metadata operations

- [entity/data/dao/DetectionResultDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/DetectionResultDao.kt) ⭐
  - `insert()` - Save inference result
  - `getByCallId()` - Query by call
  - `getLatestByCallId()` - Get most recent
  - `getAllDeepfakes()` - Query detections

- [entity/data/dao/UserDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/UserDao.kt)
  - User data access

- [entity/data/dao/UserSettingsDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/UserSettingsDao.kt)
  - Settings persistence

- [entity/data/dao/ContactDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/ContactDao.kt)
  - Contact operations

- [entity/data/dao/AlertEventDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/AlertEventDao.kt)
  - Alert event tracking

#### Entities (Room Models)
- [entity/data/entities/CallEntity.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/entities/CallEntity.kt)
  - Table: calls
  - Columns: id, user_id, status, created_seconds, updated_seconds, notes

- [entity/data/entities/DetectionResultEntity.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/entities/DetectionResultEntity.kt) ⭐
  - Table: detection_results
  - Columns: id, call_id, probability, is_deepfake, timestamp_seconds, model_version, confidence_level
  - Foreign Key: call_id → calls.id

- [entity/data/entities/AlertEventEntity.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/entities/AlertEventEntity.kt)
  - Table: alert_events
  - Columns: id, type, timestamp, data

- [entity/data/entities/CallRecordEntity.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/entities/CallRecordEntity.kt)
  - Table: call_records
  - Extended call data

#### Repositories
- [entity/data/repositories/CallRepository.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/repositories/CallRepository.kt)
  - Repository pattern for calls
  - CRUD operations

- [entity/data/repositories/DetectionsRepo.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/repositories/DetectionsRepo.kt)
  - Repository pattern for detection results

---

### 6️⃣ FIREBASE & REMOTE DATA (6 Files)

#### Authentication & User
- [data/remote/firebase/FirebaseAuthManager.kt](app/src/main/java/com/example/fyp_25_s4_23/data/remote/firebase/FirebaseAuthManager.kt)
  - `currentUser()` - Get authenticated user
  - User UID retrieval

#### Call History
- [data/remote/firebase/CallHistoryRepository.kt](app/src/main/java/com/example/fyp_25_s4_23/data/remote/firebase/CallHistoryRepository.kt)
  - Fetch call history from Cloud Functions
  - `getCallHistory(limit)` - Paginated history
  - `endCall(callId, duration, status)` - End call in Firebase

#### Contact & Directory
- [data/remote/firebase/FirebaseContactRepository.kt](app/src/main/java/com/example/fyp_25_s4_23/data/remote/firebase/FirebaseContactRepository.kt)
  - Contact data from Firestore

- [data/remote/firebase/FirebaseUserDirectory.kt](app/src/main/java/com/example/fyp_25_s4_23/data/remote/firebase/FirebaseUserDirectory.kt)
  - User directory search/lookup

- [data/remote/firebase/UsernameService.kt](app/src/main/java/com/example/fyp_25_s4_23/data/remote/firebase/UsernameService.kt)
  - Username lookup by UID

- [data/remote/firebase/PhoneLookupService.kt](app/src/main/java/com/example/fyp_25_s4_23/data/remote/firebase/PhoneLookupService.kt)
  - Phone number to user mapping

---

### 7️⃣ DOMAIN ENTITIES & VALUE OBJECTS (12 Files)

#### Call Domain Objects
- [domain/entities/CallRecord.kt](app/src/main/java/com/example/fyp_25_s4_23/domain/entities/CallRecord.kt)
  - Domain model for call record

- [domain/entities/CallMetadata.kt](app/src/main/java/com/example/fyp_25_s4_23/domain/entities/CallMetadata.kt)
  - Extended call metadata

- [domain/valueobjects/CallStatus.kt](app/src/main/java/com/example/fyp_25_s4_23/domain/valueobjects/CallStatus.kt)
  - "ringing", "in_call", "ended", "accepted"

- [domain/valueobjects/CallDirection.kt](app/src/main/java/com/example/fyp_25_s4_23/domain/valueobjects/CallDirection.kt)
  - "INCOMING" or "OUTGOING"

#### Detection Domain Objects
- [domain/entities/DetectionSession.kt](app/src/main/java/com/example/fyp_25_s4_23/domain/entities/DetectionSession.kt)
  - Detection session info

- [domain/entities/DetectionResult.kt](app/src/main/java/com/example/fyp_25_s4_23/domain/entities/DetectionResult.kt)
  - Detection result domain model

- [domain/valueobjects/DetectionSessionStatus.kt](app/src/main/java/com/example/fyp_25_s4_23/domain/valueobjects/DetectionSessionStatus.kt)
  - Detection session states

#### Firebase Models
- [entity/domain/entities/FirebaseCallHistory.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/domain/entities/FirebaseCallHistory.kt)
  - Firebase call history response

- [entity/domain/entities/CallRecord.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/domain/entities/CallRecord.kt)
  - Domain call record

- [entity/domain/entities/DetectionSession.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/domain/entities/DetectionSession.kt)
  - Detection session entity

- [entity/domain/entities/DetectionResult.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/domain/entities/DetectionResult.kt)
  - Detection result entity

---

### 8️⃣ MAPPERS & CONVERTERS (3 Files)

- [data/mappers/CallRecordMappers.kt](app/src/main/java/com/example/fyp_25_s4_23/data/mappers/CallRecordMappers.kt)
  - Map between Room entities and domain models

- [entity/data/mappers/CallRecordMappers.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/mappers/CallRecordMappers.kt)
  - Alternative mapper implementation

- (Other mappers as needed)

---

### 9️⃣ UTILITIES (2 Files)

- [util/VibratorUtil.kt](app/src/main/java/com/example/fyp_25_s4_23/util/VibratorUtil.kt)
  - `vibrate()` - Haptic feedback on detection alert

- (Other utilities)

---

### 🔟 SUPPORTING FILES (3+ Files)

#### Machine Learning Assets
- `ml/model/melcnn.pt` - PyTorch deepfake detection model

#### Demo Audio
- `assets/demo_audio/*.wav` - Demo audio files for testing

---

## 📊 FILE COUNT SUMMARY

```
Category                    Count   Status
─────────────────────────────────────────────
Call Initiation             9      Core
UI & View Management        4      Core
WebRTC Core                 3      ⭐ Critical
Deepfake Detection          2      ⭐ Critical
Database (DAOs/Entities)    10     Core
Firebase & Remote           6      Core
Domain Models              12     Support
Mappers & Converters        3      Support
Utilities                   2      Core
ML Assets                   1      Core
Demo Audio                  ?      Test
─────────────────────────────────────────────
TOTAL                      52+     Complete
```

---

## 🎯 File Selection by Use Case

### To understand call creation:
1. [VOIPCallManager.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/VOIPCallManager.kt)
2. [FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt)
3. [CallInProgressActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressActivity.kt)

### To understand incoming calls:
1. [IncomingCallListener.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallListener.kt)
2. [IncomingCallNotifier.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallNotifier.kt)
3. [CallInProgressActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressActivity.kt)

### To understand WebRTC:
1. [WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) ⭐
2. [FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt)

### To understand detection:
1. [DeepfakeDetectionService.kt](app/src/main/java/com/example/fyp_25_s4_23/control/detection/DeepfakeDetectionService.kt) ⭐
2. [WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) - attachIncomingDetectionSink()
3. [ModelRunner.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/ml/ModelRunner.kt)

### To understand database:
1. [AppDatabase.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/db/AppDatabase.kt)
2. [DetectionResultDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/DetectionResultDao.kt)
3. [DetectionResultEntity.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/entities/DetectionResultEntity.kt)

### To understand termination:
1. [WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) - engineEnd()
2. [CallInProgressActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressActivity.kt) - onDestroy()
3. [FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt) - stopListening()

---

## 🔍 Cross-File Dependencies

```
VOIPCallManager
  → FirebaseSignalingManager.createCall()
  → CallInProgressActivity (Intent)

CallInProgressActivity
  → CallInProgressViewModel (ViewModel)
  → WebRTCClient (init)
  → FirebaseSignalingManager (signaling)
  → AppDatabase (DAO access)
  → ActiveCallStore (state)

WebRTCClient
  → FirebaseSignalingManager (SDP/ICE)
  → DeepfakeDetectionService (detection)
  → ActiveCallStore (state)
  → ModelRunner (inference)

DeepfakeDetectionService
  → AppDatabase.detectionResultDao (persist)
  → ModelRunner (inference)
  → VibratorUtil (alert)

FirebaseSignalingManager
  → FirebaseFirestore (Firestore access)

IncomingCallListener
  → FirebaseFirestore (listener)
  → AppDatabase.contactDao (lookup)
  → CallInProgressActivity (launch)

FirebaseAuthManager
  → FirebaseAuth (get current user)
```

---

## ✅ Completeness Checklist

- [x] Call creation entry point (VOIPCallManager)
- [x] Call acceptance/connection setup (WebRTCClient)
- [x] Detection service initialization (DeepfakeDetectionService)
- [x] Detection result generation (Inference loop)
- [x] Firebase sync of detection results (SQLite writes)
- [x] Call termination/cleanup (engineEnd, onDestroy)
- [x] Database operations (AppDatabase, DAOs)
- [x] Incoming call detection (IncomingCallListener)
- [x] WebRTC client (WebRTCClient)
- [x] Firebase signaling (FirebaseSignalingManager)
- [x] Audio handling (attachIncomingDetectionSink)
- [x] UI state management (CallInProgressViewModel)
- [x] Service management (CallMonitorService)

**All 52+ files mapped and documented ✓**

