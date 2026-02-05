# Call Lifecycle - Quick Reference Index

## 🎯 Quick Navigation by Use Case

### "Where does the call start?"
→ [boundary/call/VOIPCallManager.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/VOIPCallManager.kt) - `startOutgoingVoipCall()`
→ [control/webrtc/FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt) - `createCall()` (line 20)

### "How does the incoming call notification work?"
→ [control/call/IncomingCallListener.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallListener.kt) - `start()` (line 25)
→ [boundary/handlers/CallMonitorService.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/handlers/CallMonitorService.kt) - Background listener service

### "Where does WebRTC setup happen?"
→ [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) (1278 lines)
  - `initialize()` - Line 108
  - `createAudioTrack()` - Line 155
  - `createPeerConnection()` - Line 489

### "How are audio offers/answers exchanged?"
→ [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt)
  - `createOffer()` - Line 800
  - `createAnswer()` - Line 930
→ [control/webrtc/FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt)
  - `sendOffer()` - Line 120
  - `sendAnswer()` - Line 135

### "Where is deepfake detection initialized?"
→ [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) - `startDeepfakeDetection()` (line 850)
→ [control/detection/DeepfakeDetectionService.kt](app/src/main/java/com/example/fyp_25_s4_23/control/detection/DeepfakeDetectionService.kt) - `startMonitoring()` (line 80)

### "How is incoming audio routed to detection?"
→ [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) - `attachIncomingDetectionSink()` (line 200)

### "Where are detection results saved?"
→ [control/detection/DeepfakeDetectionService.kt](app/src/main/java/com/example/fyp_25_s4_23/control/detection/DeepfakeDetectionService.kt) - Processing loop (line 100+)
→ [entity/data/dao/DetectionResultDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/DetectionResultDao.kt) - `insert()` method

### "How is the call terminated?"
→ [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) - `hangUp()` / `engineEnd()` (line 1100+)
→ [boundary/call/CallInProgressActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressActivity.kt) - `onDestroy()` (line 180)

### "What's in the database?"
→ [entity/data/db/AppDatabase.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/db/AppDatabase.kt) - Schema definition (line 20+)
  - **tables**: CallEntity, DetectionResultEntity, AlertEventEntity, etc.
  - **DAOs**: 
    - [entity/data/dao/CallDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/CallDao.kt)
    - [entity/data/dao/DetectionResultDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/DetectionResultDao.kt)

---

## 📊 File Count by Category

| Category | Count | Files |
|----------|-------|-------|
| **Call Management** | 6 | VOIPCallManager, CallInProgressActivity, CallInProgressViewModel, IncomingCallListener, IncomingCallNotifier, ActiveCallStore |
| **WebRTC Core** | 2 | WebRTCClient, FirebaseSignalingManager |
| **Detection** | 2 | DeepfakeDetectionService, ModelRunner |
| **Database** | 2 | AppDatabase, DAOs (DetectionResultDao, CallDao, etc.) |
| **Services** | 3 | CallMonitorService, AntiDeepfakeInCallService, VOIPMessagingService |
| **Firebase** | 6 | FirebaseAuthManager, CallHistoryRepository, FirebaseContactRepository, etc. |
| **UI/Compose** | 2 | CallInProgressScreen, CallHistoryScreen |
| **Repositories** | 8 | CallRepository, DetectionsRepo, ContactRepository, UserRepository, etc. |
| **Entities & DAOs** | 20+ | Call entities, Detection entities, Mappers, Converters |
| **TOTAL CRITICAL** | **15** | See list below |

---

## ⭐ Critical Files (Must Read First)

| Priority | File | Lines | Key Functions |
|----------|------|-------|-----------------|
| 🔴 P0 | [WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) | 1278 | initialize(), createAudioTrack(), createPeerConnection(), createOffer(), answerIncomingCall(), startDeepfakeDetection(), hangUp() |
| 🔴 P0 | [FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt) | 280 | createCall(), listenToCall(), sendOffer(), sendAnswer(), sendIceCandidate() |
| 🔴 P0 | [DeepfakeDetectionService.kt](app/src/main/java/com/example/fyp_25_s4_23/control/detection/DeepfakeDetectionService.kt) | 376 | startMonitoring(), feedAudioChunk(), Detection loop, stopMonitoring() |
| 🔴 P0 | [CallInProgressActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressActivity.kt) | 235 | onCreate(), startWebRtc(), onDestroy() |
| 🟠 P1 | [IncomingCallListener.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallListener.kt) | 161 | start(), Firestore listener, incoming call detection |
| 🟠 P1 | [AppDatabase.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/db/AppDatabase.kt) | 77 | getInstance(), Entity definitions, DAO declarations |
| 🟠 P1 | [DetectionResultDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/DetectionResultDao.kt) | 21 | insert(), getByCallId(), getLatestByCallId() |
| 🟡 P2 | [CallInProgressViewModel.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressViewModel.kt) | 150+ | CallUiState, answer(), hangUp(), setActive() |
| 🟡 P2 | [VOIPCallManager.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/VOIPCallManager.kt) | 40 | startOutgoingVoipCall() |
| 🟡 P2 | [ActiveCallStore.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/ActiveCallStore.kt) | 45 | update(), setWebRtcActive(), clear() |

---

## 🔍 Search by Feature

### Audio Capture & Processing
- [WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) - `createAudioTrack()` (line 155)
- [WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) - `attachIncomingDetectionSink()` (line 200)

### Deepfake Inference
- [DeepfakeDetectionService.kt](app/src/main/java/com/example/fyp_25_s4_23/control/detection/DeepfakeDetectionService.kt) - `startMonitoring()` (line 80)
- [ModelRunner.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/ml/ModelRunner.kt) - `predict()` method

### Detection Database Writes
- [DeepfakeDetectionService.kt](app/src/main/java/com/example/fyp_25_s4_23/control/detection/DeepfakeDetectionService.kt) - Line ~120-150 (inference + DB insert)
- [DetectionResultDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/DetectionResultDao.kt) - `insert()` (suspend function)

### Firebase Call Document Creation
- [FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt) - `createCall()` (line 20)

### Firebase Listeners (Continuous Sync)
- [FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt) - `listenToCall()` (line 49)
- [IncomingCallListener.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallListener.kt) - `start()` (line 25)

### ICE Candidate Exchange
- [WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) - `onIceCandidate()` callback (line 680+)
- [FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt) - `sendIceCandidate()` (line 185)

### Call State Transitions
- [CallInProgressViewModel.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressViewModel.kt) - `CallUiState` sealed class
- [WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) - `onIceConnectionChange()` (line 710+)

### Cleanup & Termination
- [WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) - `engineEnd()` (line 1100+)
- [CallInProgressActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressActivity.kt) - `onDestroy()` (line 180)

---

## 🔗 Data Flow Paths

### Outgoing Call Path
```
VOIPCallManager.startOutgoingVoipCall()
  ↓ (Creates Firebase doc + Intent)
CallInProgressActivity.onCreate()
  ↓ (Sets up UI + WebRTC client)
WebRTCClient.initialize() → createAudioTrack() → createPeerConnection()
  ↓ (Setup complete, start signaling)
FirebaseSignalingManager.listenToCall()
  ↓ (Wait for answer)
WebRTCClient.onRemoteAnswerReceived()
  ↓ (Answer received)
ICE Candidate Exchange (both directions)
  ↓ (Candidates gathered)
WebRTCClient.onIceConnectionChange(CONNECTED)
  ↓ (Connection established)
DeepfakeDetectionService.startMonitoring()
  ↓ (Detection loop begins)
Call Active (until hang up)
```

### Incoming Call Path
```
IncomingCallListener.start()
  ↓ (Watch Firestore)
Firestore listener detects new ringing call
  ↓ (status="ringing" for current user)
IncomingCallNotifier.show()
  ↓ (Show notification)
User taps notification
  ↓
CallInProgressActivity.onCreate()
  ↓ (isIncoming=true)
WebRTCClient.initialize() → createAudioTrack() → createPeerConnection()
  ↓
FirebaseSignalingManager.listenToCall()
  ↓ (Wait for offer)
WebRTCClient.onRemoteOfferReceived()
  ↓ (Offer received, show Answer button)
User taps Answer
  ↓
WebRTCClient.answerIncomingCall() → createAnswer()
  ↓ (Send answer to Firebase)
Caller receives answer via listener
  ↓
ICE Candidate Exchange
  ↓
WebRTCClient.onIceConnectionChange(CONNECTED)
  ↓
DeepfakeDetectionService.startMonitoring()
  ↓
Call Active
```

### Detection & Inference Path
```
WebRTCClient.onTrack(audioTrack)
  ↓ (Remote audio received)
attachIncomingDetectionSink(audioTrack)
  ↓ (Create AudioSink interceptor)
Audio samples → AudioSink.onFrame()
  ↓ (Continuous flow)
detectionService?.feedAudioChunk(shortArray)
  ↓ (Queue 16-bit PCM)
DeepfakeDetectionService.startMonitoring()
  ├─ Buffer accumulates samples (3 seconds = 48,000 samples at 16kHz)
  │
  ├─ Inference thread runs every 500ms
  │  ├─ Check if buffer >= targetSamples
  │  ├─ Convert ShortArray → FloatArray
  │  ├─ modelRunner.predict(floatArray)
  │  └─ Get score (0.0-1.0)
  │
  ├─ Save to SQLite
  │  └─ detectionDao.insert(DetectionResultEntity)
  │
  ├─ Update StateFlow
  │
  └─ If score > threshold:
     └─ onDeepfakeDetected(score) callback
        └─ Vibrate UI
```

---

## 📍 Entity Relationships

```
Database Schema (Room/SQLite):

users
  ├─ id (PK)
  └─ (linked by user_id)
      ↓
    calls
      ├─ id (PK)
      ├─ user_id (FK → users)
      └─ status
          ├─ "ringing"
          ├─ "in_call"
          └─ "ended"
              ↓
            detection_results
              ├─ id (PK)
              ├─ call_id (FK → calls) [Nullable]
              ├─ probability (0.0-1.0)
              ├─ is_deepfake (boolean)
              └─ timestamp_seconds
                  ↓
                alert_events
                  ├─ id (PK)
                  ├─ detection_id (FK)
                  └─ timestamp
```

```
Firebase Structure:

calls/{callId} (Document)
  ├─ caller_user_id
  ├─ callee_user_id
  ├─ caller_username
  ├─ callee_username
  ├─ status
  ├─ offer_sdp
  ├─ answer_sdp
  ├─ created_at
  │
  └─ ice_candidates/{userId} (Collection)
      └─ candidates/{id} (Collection)
          ├─ candidate
          ├─ sdpMid
          └─ sdpMLineIndex
```

---

## 🚨 Critical Line Numbers Reference

| File | Function | Lines | Description |
|------|----------|-------|-------------|
| WebRTCClient.kt | initialize() | 108-129 | PeerConnectionFactory + JavaAudioDeviceModule |
| WebRTCClient.kt | createAudioTrack() | 155-169 | Audio source + track creation |
| WebRTCClient.kt | attachIncomingDetectionSink() | 200-260 | Remote audio → detection routing |
| WebRTCClient.kt | createPeerConnection() | 489-750 | PeerConnection setup + observers |
| WebRTCClient.kt | onIceCandidate() callback | 680-695 | ICE candidate handling |
| WebRTCClient.kt | onIceConnectionChange() callback | 710-770 | Call connected → start detection |
| WebRTCClient.kt | createOffer() | 800-830 | SDP offer generation |
| WebRTCClient.kt | createAnswer() | 930-960 | SDP answer generation |
| WebRTCClient.kt | startDeepfakeDetection() | 850-890 | Detection initialization |
| WebRTCClient.kt | engineEnd() | 1100-1200 | Call cleanup |
| FirebaseSignalingManager.kt | createCall() | 20-48 | Firebase call doc creation |
| FirebaseSignalingManager.kt | listenToCall() | 49-98 | Firestore listener setup |
| FirebaseSignalingManager.kt | sendOffer() | 120-127 | Send SDP offer to Firebase |
| FirebaseSignalingManager.kt | sendAnswer() | 135-142 | Send SDP answer to Firebase |
| DeepfakeDetectionService.kt | startMonitoring() | 80-120 | Detection loop start |
| DeepfakeDetectionService.kt | Processing loop | 100+ | Inference + SQLite writes |
| CallInProgressActivity.kt | onCreate() | 30-90 | Activity initialization |
| CallInProgressActivity.kt | startWebRtc() | 125-160 | WebRTC setup orchestration |
| IncomingCallListener.kt | start() | 25-80 | Firestore listener for incoming |

---

## 🔐 Singleton & Global State Management

| Component | Type | Access | Purpose |
|-----------|------|--------|---------|
| AppDatabase | Room Singleton | getInstance(context) | Local SQLite database |
| ActiveCallStore | Object Singleton | state: StateFlow | In-memory active call tracking |
| IncomingCallListener | Object Singleton | start()/stop() | Background call listener |
| FirebaseSignalingManager | Per-Activity Instance | new FirebaseSignalingManager() | Call signaling (per-call) |
| WebRTCClient | Per-Activity Instance | new WebRTCClient() | WebRTC peer connection (per-call) |
| DeepfakeDetectionService | Per-Call Instance | created by WebRTCClient | Detection loop (per-call) |

---

## ⏱️ Key Timeouts & Intervals

| Event | Timeout/Interval | File | Line |
|-------|------------------|------|------|
| Ring timeout (caller waits for answer) | 60 seconds | WebRTCClient.kt | 642 |
| ICE extended timeout | 30 seconds additional | WebRTCClient.kt | 645 |
| Audio monitoring poll interval | 300ms | WebRTCClient.kt | 900 |
| Detection buffer accumulation | 3 seconds | DeepfakeDetectionService.kt | 45 |
| Detection inference loop | Every 500ms | DeepfakeDetectionService.kt | 100 |
| Incoming call age filter | 5 minutes max | IncomingCallListener.kt | 65 |
| ICE failure recovery wait | 15 seconds | WebRTCClient.kt | 765 |

---

## 🎯 Testing Entry Points

```kotlin
// To test outgoing call:
VoipCallManager.startOutgoingVoipCall(context, calleeUserId, calleeDisplayName)

// To test incoming call detection:
// Wait for IncomingCallListener.start() to trigger, then call someone from another device

// To test deepfake detection:
// Play demo audio during active call: 
webRtcClient?.playDemoAudio("demo_deepfake_sample.wav")

// To check detection results:
val dao = AppDatabase.getInstance(context).detectionResultDao()
val results = dao.getByCallId(callId)

// To verify Firebase signaling:
// Monitor Firestore console: collections/calls/{callId}
```

