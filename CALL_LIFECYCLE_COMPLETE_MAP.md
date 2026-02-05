# Complete Call Lifecycle Flow - File Map & Documentation

## 📋 Overview
This document maps the entire call lifecycle from creation to termination, showing:
- **Chronological flow** of function calls and data creation
- **All key files** involved in each phase
- **Database operations** at each stage
- **Firebase synchronization** points

---

## 🚀 **PHASE 1: CALL INITIALIZATION & CREATION**

### 1.1 User Initiates Outgoing Call
**Entry Point**: [boundary/call/VOIPCallManager.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/VOIPCallManager.kt)

```
User clicks "Call" button
  ↓
VoipCallManager.startOutgoingVoipCall() 
  ├─ Generates unique callId (UUID)
  ├─ Retrieves current user from FirebaseAuthManager
  ├─ Creates Firebase call document via FirebaseSignalingManager.createCall()
  │  └─ Database: Firestore collection "calls/{callId}" created with:
  │     • caller_user_id
  │     • callee_user_id  
  │     • caller_username
  │     • callee_username
  │     • status: "ringing"
  │     • offer_sdp: null
  │     • answer_sdp: null
  │     • created_at: timestamp
  │
  └─ Launches CallInProgressActivity with Intent extras:
     • EXTRA_CALL_ID
     • EXTRA_REMOTE_USER_ID  
     • EXTRA_IS_INCOMING: false
     • EXTRA_DISPLAY_NAME
```

**Files Involved**:
- [boundary/call/VOIPCallManager.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/VOIPCallManager.kt) - Entry point for outgoing calls
- [control/webrtc/FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt#L20-L48) - `createCall()` method
- [control/call/ActiveCallStore.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/ActiveCallStore.kt) - Stores active call state in-memory
- [data/remote/firebase/FirebaseAuthManager.kt](app/src/main/java/com/example/fyp_25_s4_23/data/remote/firebase/FirebaseAuthManager.kt) - Gets current user

**Database Operations**:
- **Firestore Write**: `calls/{callId}` document created (status: "ringing")

---

### 1.2 Incoming Call Detection
**Entry Point**: [control/call/IncomingCallListener.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallListener.kt)

```
Background Service/Listener monitors Firebase
  ↓
IncomingCallListener.start()
  ├─ Firestore listener watches: collection("calls")
  │  .whereEqualTo("callee_user_id", currentUid)
  │  .whereEqualTo("status", "ringing")
  │
  ├─ When new call doc added (DocumentChange.Type.ADDED):
  │  ├─ Validates call is recent (not >5 minutes old)
  │  ├─ Checks if callId already handled
  │  ├─ Retrieves caller details from Firestore
  │  └─ Shows incoming call notification
  │
  └─ Launches CallInProgressActivity with:
     • EXTRA_CALL_ID  
     • EXTRA_REMOTE_USER_ID (caller's uid)
     • EXTRA_IS_INCOMING: true
     • EXTRA_DISPLAY_NAME (caller's name)
```

**Files Involved**:
- [control/call/IncomingCallListener.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallListener.kt) - Watches Firebase for ringing calls
- [control/call/IncomingCallNotifier.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallNotifier.kt) - Shows notification UI
- [control/call/IncomingCallIntent.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallIntent.kt) - Constants for Intent extras
- [boundary/handlers/CallMonitorService.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/handlers/CallMonitorService.kt) - Background service managing listeners

**Database Operations**:
- **Firestore Read**: Listens to `calls/{callId}` with filters
- **Contact DB Operations**: Looks up caller in local ContactEntity if stored

---

## 📞 **PHASE 2: CALL ACTIVITY SETUP & UI INITIALIZATION**

### 2.1 CallInProgressActivity Created
**File**: [boundary/call/CallInProgressActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressActivity.kt)

```
Activity.onCreate()
  ├─ Extract Intent extras:
  │  ├─ callId
  │  ├─ remoteUserId  
  │  ├─ isIncoming
  │  └─ displayName
  │
  ├─ Initialize ViewModel: CallInProgressViewModel
  │  └─ Manages UI state and callbacks
  │
  ├─ Stop any existing IncomingCallListener
  │
  ├─ Create FirebaseSignalingManager instance
  │  └─ Will handle Firestore signaling
  │
  ├─ Update ActiveCallStore with call info
  │  └─ In-memory store: callId, remoteUserId, state
  │
  ├─ Get AppDatabase singleton
  │  └─ Initialize DetectionResultDao for detection writes
  │
  ├─ Create WebRtcClient instance
  │  ├─ isCaller = !isIncoming
  │  ├─ Pass signaling, callId, userIds
  │  └─ Ready for WebRTC setup
  │
  ├─ Request RECORD_AUDIO permission
  │
  └─ Show CallInProgressScreen UI
     └─ Compose Screen with call state, buttons, etc.
```

**Files Involved**:
- [boundary/call/CallInProgressActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressActivity.kt) - Activity managing the call
- [boundary/call/CallInProgressViewModel.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressViewModel.kt) - ViewModel for UI state
- [boundary/call/CallInProgressScreen.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressScreen.kt) - Composable UI
- [control/call/ActiveCallStore.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/ActiveCallStore.kt) - In-memory call state
- [entity/data/db/AppDatabase.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/db/AppDatabase.kt) - Database access

**Database Operations**:
- **In-Memory**: ActiveCallStore updated
- No DB writes yet

---

## 🔌 **PHASE 3: WEBRTC INITIALIZATION**

### 3.1 WebRTC Client Setup
**File**: [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt)

```
startWebRtc() called from Activity
  ├─ webRtcClient.initialize()
  │  ├─ PeerConnectionFactory.initialize()
  │  ├─ JavaAudioDeviceModule created
  │  │  └─ Sets up raw audio PCM capture (16kHz, 16-bit, mono)
  │  └─ PeerConnectionFactory created with audioDeviceModule
  │
  ├─ webRtcClient.createAudioTrack()
  │  ├─ audioSource = factory.createAudioSource(constraints)
  │  ├─ audioTrack = factory.createAudioTrack("audio0", audioSource)
  │  └─ audioTrack.setEnabled(true)
  │
  ├─ webRtcClient.createPeerConnection()
  │  ├─ Configure ICE servers (STUN + TURN)
  │  ├─ Create PeerConnection with RTCConfiguration
  │  ├─ Add remote audio track receiver
  │  ├─ Add local audioTrack to peer connection
  │  └─ Attach PeerConnectionObserver for events:
  │     ├─ onIceCandidate (send to Firestore)
  │     ├─ onTrack (receive remote audio → attach detection sink)
  │     ├─ onIceConnectionChange (state transitions)
  │     └─ onSignalingChange
  │
  ├─ signaling.listenToCall(callId, isCaller, callbacks)
  │  └─ Setup Firestore listener for:
  │     ├─ offer_sdp (callee receives)
  │     ├─ answer_sdp (caller receives)
  │     └─ status (both track call state)
  │
  └─ webRtcClient.start()
     ├─ If isCaller: start ring timeout (60s)
     ├─ Start audio monitoring (polls RTCStats every 300ms)
     └─ If isCaller: createOffer()
```

**Files Involved**:
- [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt#L108-L615) - Core WebRTC engine
- [control/webrtc/FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt#L49-L98) - `listenToCall()` method

**Database Operations**:
- **Firestore Write**: SDP offers/answers written to `calls/{callId}`
- **Firestore Write**: ICE candidates to `calls/{callId}/ice_candidates/{userId}/candidates/{id}`
- **Firestore Read**: Continuous listener on `calls/{callId}` for offer/answer/status

---

## 🤝 **PHASE 4: SIGNALING & CONNECTION ESTABLISHMENT**

### 4.1 Caller Creates & Sends Offer (SDP)

```
Caller's WebRtcClient.start() → createOffer()
  ├─ peerConnection.createOffer()
  │  └─ Generates SDP offer (Session Description Protocol)
  │
  ├─ peerConnection.setLocalDescription(offer)
  │  └─ Caller's local SDP is set
  │
  └─ signaling.sendOffer(callId, sdpString)
     └─ Firestore: UPDATE calls/{callId}.offer_sdp = sdpString
```

### 4.2 Callee Receives Offer & Creates Answer

```
Callee's Firestore listener receives offer
  ├─ onOffer callback triggered
  │
  └─ webRtcClient.onRemoteOfferReceived(offer)
     ├─ peerConnection.setRemoteDescription(offer)
     │
     └─ onReadyToAnswer callback fired
        └─ UI shows "Answer" button (enabled)
```

### 4.3 Callee Answers (User Action)

```
User taps "Answer" button
  ├─ viewModel.answer() 
  │  └─ webRtcClient.answerIncomingCall()
  │
  ├─ peerConnection.createAnswer()
  │  └─ Generates SDP answer
  │
  ├─ peerConnection.setLocalDescription(answer)
  │
  └─ signaling.sendAnswer(callId, sdpString)
     └─ Firestore: UPDATE calls/{callId}.answer_sdp = sdpString
```

### 4.4 Caller Receives Answer

```
Caller's Firestore listener receives answer
  ├─ onAnswer callback triggered
  │
  └─ webRtcClient.onRemoteAnswerReceived(answer)
     ├─ peerConnection.setRemoteDescription(answer)
     │
     └─ Signaling state → STABLE
        └─ Ready for ICE candidates
```

**Files Involved**:
- [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt#L800-L950) - SDP creation & handling
- [control/webrtc/FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt#L120-L165) - SDP transmission

**Database Operations**:
- **Firestore Write**: `calls/{callId}.offer_sdp` and `calls/{callId}.answer_sdp` populated

---

## 🌐 **PHASE 5: ICE CANDIDATE EXCHANGE**

### 5.1 ICE Candidate Generation & Exchange

```
Both peers generate ICE candidates (in parallel)
  ├─ onIceCandidate() callback fired multiple times
  │  ├─ Candidate types:
  │  │  ├─ HOST (local IP) - fastest
  │  │  ├─ SRFLX (STUN-discovered) - external IP
  │  │  └─ RELAY (TURN) - fallback through server
  │  │
  │  └─ signaling.sendIceCandidate(callId, userId, candidate)
  │     └─ Firestore: ADD to calls/{callId}/ice_candidates/{userId}/candidates/{id}
  │
  └─ Each peer listens for remote ICE candidates
     └─ signaling.listenForIceCandidates(callId, remoteUserId, onCandidate)
        ├─ Firestore listener on calls/{callId}/ice_candidates/{remoteUserId}/candidates
        │
        └─ When received: peerConnection.addIceCandidate(candidate)
```

### 5.2 ICE Connection State Transitions

```
ICE State Flow:
  ├─ CHECKING: Trying to establish connection
  │  ├─ Set iceInProgress = true
  │  └─ Extend ring timeout (give more time for NAT traversal)
  │
  ├─ CONNECTED / COMPLETED: ✅ Peer connection established
  │  ├─ iceInProgress = false
  │  ├─ callConnected = true
  │  ├─ Cancel ring timeout
  │  ├─ setupAudioRouting()
  │  │  ├─ Set audio mode to IN_COMMUNICATION
  │  │  ├─ Configure speaker/earpiece
  │  │  └─ Set mic unmuted
  │  │
  │  ├─ startAudioMonitoring()
  │  │  └─ Poll RTCStats every 300ms for audio levels
  │  │
  │  ├─ signaling.updateCallStatus(callId, "in_call")
  │  │  └─ Firestore: UPDATE calls/{callId}.status = "in_call"
  │  │
  │  ├─ onAnswered() callback (UI shows active call)
  │  │
  │  └─ ⭐ startDeepfakeDetection() [CRITICAL - see Phase 6]
  │
  ├─ DISCONNECTED: Temporary loss, will retry
  │
  └─ FAILED: ❌ Connection failed
     ├─ Wait 15 seconds for recovery attempt
     ├─ If still failed: signaling.updateCallStatus(callId, "ended")
     └─ engineEnd("ICE_FAILED")
```

**Files Involved**:
- [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt#L689-L750) - ICE handling
- [control/webrtc/FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt#L185-L220) - ICE candidate exchange

**Database Operations**:
- **Firestore Write**: Multiple ICE candidates written to `calls/{callId}/ice_candidates/{userId}/candidates/{id}`
- **Firestore Listener**: Continuous reading of remote ICE candidates
- **Firestore Write**: `calls/{callId}.status` updated to "in_call"

---

## 🔍 **PHASE 6: DEEPFAKE DETECTION INITIALIZATION & OPERATION**

### 6.1 Detection Service Start (on ICE CONNECTED)

**File**: [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt#L850-L890)

```
When ICE connection succeeds (state == CONNECTED)
  ├─ webRtcClient.startDeepfakeDetection()
  │
  └─ detectionService = DeepfakeDetectionService(context, callId)
     ├─ Gets AppDatabase.getInstance(context)
     ├─ Creates ModelRunner for inference
     ├─ Warmup model in background coroutine
     │
     └─ onDeepfakeDetected callback = { score: Float →
        ├─ UI vibrates as alert
        └─ Save detection result to SQLite
     }
```

### 6.2 Attach Incoming Audio Sink for Detection

**File**: [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt#L200-L260)

```
When remote audio track received (onTrack callback)
  ├─ remoteAudioTrack = track
  │
  └─ attachIncomingDetectionSink(track)
     ├─ Create AudioSink that intercepts RTP audio
     │
     ├─ Receives audio samples from WebRTC (variable sample rates)
     │
     └─ For each audio frame:
        ├─ Convert to mono (if stereo)
        ├─ Resample to 16kHz (if needed)
        ├─ detectionService?.feedAudioChunk(shortArray)
        │  └─ Queues 16-bit PCM samples for processing
        │
        └─ Audio flows simultaneously to:
           ├─ Speaker (normal call audio)
           └─ Detection service (inference buffer)
```

### 6.3 Detection Processing Loop

**File**: [control/detection/DeepfakeDetectionService.kt](app/src/main/java/com/example/fyp_25_s4_23/control/detection/DeepfakeDetectionService.kt)

```
DeepfakeDetectionService.startMonitoring()
  ├─ Start background coroutine (Dispatchers.Default)
  │
  └─ Processing loop (every 300-500ms):
     ├─ Check audioBufferQueue for samples
     │
     ├─ When buffer reaches targetSamples (48,000 samples = 3 seconds):
     │  ├─ Convert ShortArray → FloatArray (normalize to [-1, 1])
     │  ├─ Apply preprocessing (if model requires)
     │  │
     │  └─ Run inference:
     │     ├─ modelRunner.predict(floatArray)
     │     │  └─ PyTorch model forward pass
     │     │     └─ Outputs: [prob_genuine, prob_deepfake]
     │     │
     │     ├─ score = prob_deepfake (0.0 to 1.0)
     │     │
     │     ├─ isDeepfake = (score > detectionThreshold) [default: 0.7]
     │     │
     │     └─ Create DetectionResult(score, isDeepfake, timestamp, confidence)
     │
     ├─ Save to SQLite (Room):
     │  └─ detectionDao.insert(
     │        DetectionResultEntity(
     │           id = UUID,
     │           callId = callId,
     │           probability = score,
     │           isDeepfake = isDeepfake,
     │           timestampSeconds = current time,
     │           modelVersion = "melcnn.pt",
     │           confidenceLevel = classify(score)
     │        )
     │     )
     │
     ├─ Update DetectionState (Flow)
     │  └─ lastScore, isDeepfake, detectionCount, deepfakeCount, averageScore
     │
     ├─ If isDeepfake AND score > threshold:
     │  ├─ onDeepfakeDetected(score) callback
     │  │  └─ Vibrate UI (via VibratorUtil.vibrate())
     │  │
     │  └─ Emit to UI via StateFlow
     │
     └─ Clear buffer and repeat
```

**Files Involved**:
- [control/detection/DeepfakeDetectionService.kt](app/src/main/java/com/example/fyp_25_s4_23/control/detection/DeepfakeDetectionService.kt) - Core detection engine
- [entity/ml/ModelRunner.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/ml/ModelRunner.kt) - PyTorch model inference
- [entity/data/db/AppDatabase.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/db/AppDatabase.kt) - Database singleton
- [entity/data/dao/DetectionResultDao.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/dao/DetectionResultDao.kt) - DAO for detection writes
- [entity/data/entities/DetectionResultEntity.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/entities/DetectionResultEntity.kt) - Room entity

**Database Operations**:
- **SQLite Write** (Room): `INSERT` into `detection_results` table for each inference
- **SQLite Read**: Query detection results for display (if needed)

---

## 📊 **PHASE 7: ACTIVE CALL STATE MANAGEMENT**

### 7.1 Audio Monitoring & Level Tracking

```
While call is active (parallel to detection)
  ├─ Every 300ms: peerConnection.getStats(callback)
  │  ├─ Extract audioLevel / audioInputLevel (outbound)
  │  ├─ Extract audioLevel / inbound-rtp (inbound)
  │  │
  │  └─ Apply exponential moving average (EMA) smoothing
  │     └─ Smoothed levels used for state detection
  │
  ├─ Local audio state transition:
  │  ├─ Muted (if audioTrack.enabled == false)
  │  ├─ Silent (level < silentThreshold = 0.1)
  │  └─ Active (level > activeThreshold = 0.5)
  │
  └─ Remote audio state:
     ├─ Active (if smoothed remote level > activeThreshold)
     └─ Inactive (if smoothed remote level < silentThreshold)
```

### 7.2 User Controls During Call

```
UI provides controls:
  ├─ Mute/Unmute
  │  └─ audioTrack.setEnabled(enabled)
  │
  ├─ Speaker/Earpiece toggle
  │  └─ audioManager.isSpeakerphoneOn = enabled
  │
  ├─ Hang Up
  │  └─ [See Phase 8 - Call Termination]
  │
  └─ Play Demo Audio (testing only)
     └─ webRtcClient.playDemoAudio(filename)
        ├─ Load audio from assets/demo_audio/
        ├─ Play via MediaPlayer
        ├─ Audio captured by microphone naturally
        └─ Processed by detection service
```

**Files Involved**:
- [boundary/call/CallInProgressScreen.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressScreen.kt) - UI controls
- [boundary/call/CallInProgressViewModel.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressViewModel.kt) - State management
- [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt#L240-L350) - Audio control methods

---

## 🛑 **PHASE 8: CALL TERMINATION & CLEANUP**

### 8.1 Call End (User or Timeout)

```
User taps "Hang Up" OR ring timeout (60s) OR ICE failed
  ├─ viewModel.hangUp()
  │  └─ webRtcClient.hangUp() OR engineEnd(reason)
  │
  ├─ signaling.updateCallStatus(callId, "ended")
  │  └─ Firestore: UPDATE calls/{callId}.status = "ended"
  │
  ├─ Notify remote peer (via Firestore status change)
  │
  └─ Remote peer receives status="ended"
     └─ onStatus callback → onRemoteEnded()
```

### 8.2 WebRTC Cleanup

```
webRtcClient.hangUp() / engineEnd()
  ├─ Set ended = true
  │
  ├─ Cancel ring timeout
  │  └─ If caller and still ringing
  │
  ├─ Stop audio monitoring
  │  └─ Stop RTCStats polling
  │
  ├─ Stop deepfake detection
  │  └─ detectionService?.stopMonitoring()
  │     ├─ Cancel processing coroutine
  │     ├─ Clear audio buffer
  │     └─ Model cleanup
  │
  ├─ Close peer connection
  │  └─ peerConnection.dispose()
  │     ├─ Stop ICE gathering
  │     ├─ Close all RTP streams
  │     └─ Release native WebRTC resources
  │
  ├─ Stop Firestore listeners
  │  └─ signaling.stopListening()
  │     ├─ callListener?.remove()
  │     ├─ iceListener?.remove()
  │     └─ detectionListener?.remove()
  │
  └─ onEngineEnded callback
     └─ Activity.finish() (closes CallInProgressActivity)
```

### 8.3 Activity Cleanup

```
CallInProgressActivity.onDestroy()
  ├─ Stop signaling listeners
  │  └─ signaling.stopListening()
  │
  ├─ Clear ActiveCallStore
  │  └─ ActiveCallStore.clear()
  │
  ├─ Restart IncomingCallListener
  │  └─ IncomingCallListener.start(context)
  │     └─ Resume watching for new incoming calls
  │
  └─ Log cleanup
```

**Files Involved**:
- [boundary/call/CallInProgressActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressActivity.kt#L125-L160) - Activity teardown
- [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt#L1100-L1200) - WebRTC cleanup
- [control/webrtc/FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt#L250-L280) - Signaling cleanup
- [control/call/ActiveCallStore.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/ActiveCallStore.kt#L35) - Call state cleanup
- [control/call/IncomingCallListener.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallListener.kt#L25-L50) - Resume listener

**Database Operations**:
- **Firestore Write**: `calls/{callId}.status` updated to "ended"
- **Firestore Cleanup**: Listeners removed, no more reads
- **SQLite**: Detection results already persisted (no cleanup)

---

## 💾 **DATABASE SCHEMA & OPERATIONS SUMMARY**

### SQLite (Room) - Local Database

**Tables Created**:

1. **calls** (CallEntity)
   - id (String) - PRIMARY KEY
   - user_id (Long) - FOREIGN KEY to users
   - status (String) - "ringing", "in_call", "ended"
   - created_seconds (Long)
   - updated_seconds (Long)
   - notes (String)
   - **Operations**: INSERT (call start), UPDATE (status changes), SELECT (call history)

2. **detection_results** (DetectionResultEntity)
   - id (String) - PRIMARY KEY (UUID)
   - call_id (String) - FOREIGN KEY to calls
   - probability (Float) - Deepfake score (0.0-1.0)
   - is_deepfake (Boolean) - Score > threshold
   - timestamp_seconds (Long)
   - model_version (String) - "melcnn.pt"
   - confidence_level (String) - "HIGH", "MEDIUM", "LOW"
   - **Operations**: INSERT (every 3 seconds during call), SELECT (analytics/history)

3. **alert_events** (AlertEventEntity)
   - Triggered when deepfake detected
   - Linked to detection result

**Room Indices**:
```kotlin
- detection_results: [call_id] - Fast lookup by call
- detection_results: [timestamp_seconds] - Time-based queries
```

### Firestore (Remote Database)

**Collections**:

1. **calls** (Call Signaling)
   ```
   calls/{callId}
   ├─ caller_user_id (String)
   ├─ callee_user_id (String)
   ├─ caller_username (String)
   ├─ callee_username (String)
   ├─ status (String) - "ringing", "in_call", "accepted", "ended"
   ├─ offer_sdp (String)
   ├─ answer_sdp (String)
   └─ created_at (Timestamp)
   
   → Subcollection: ice_candidates/{userId}/candidates/{id}
     ├─ candidate (String)
     ├─ sdpMid (String)
     └─ sdpMLineIndex (Number)
   ```

**Firestore Operations**:
- CREATE: `createCall()` - Outgoing call initialization
- READ: `listenToCall()` - Continuous listener for SDP changes
- READ: `listenForIceCandidates()` - ICE candidate listener
- UPDATE: `updateCallStatus()` - Status transitions
- UPDATE: `sendOffer()/sendAnswer()` - SDP exchange
- CREATE: `sendIceCandidate()` - ICE candidate exchange
- DELETE (implicit): Call doc cleanup after termination

---

## 📈 **CALL LIFECYCLE TIMELINE**

```
TIME    | EVENT                                    | FILES
--------|------------------------------------------|------------------------------------------
T+0s    | User initiates call                     | VOIPCallManager
T+0.1s  | Firebase call doc created              | FirebaseSignalingManager.createCall()
        | status: "ringing"                       | 
T+0.2s  | CallInProgressActivity launched         | CallInProgressActivity.onCreate()
        | WebRTC initialization starts            | 
T+0.5s  | Audio track created                     | WebRTCClient.createAudioTrack()
T+1s    | Peer connection created                 | WebRTCClient.createPeerConnection()
T+1.5s  | Firestore listener attached             | FirebaseSignalingManager.listenToCall()
        | Offer/answer monitoring ready          | 
T+2s    | Offer SDP created & sent (caller)       | WebRTCClient.createOffer()
        | Firestore: offer_sdp written           | FirebaseSignalingManager.sendOffer()
T+3s    | Callee receives offer                   | Firestore listener callback
        | onReadyToAnswer() fired                 | 
T+4s    | Callee taps "Answer" button            | UI Interaction
T+4.5s  | Answer SDP created & sent              | WebRTCClient.answerIncomingCall()
T+5s    | Caller receives answer                  | Firestore listener callback
T+5.5s  | ICE candidate exchange begins          | WebRTCClient.onIceCandidate()
        | Multiple candidates sent (HOST/SRFLX/RELAY)
T+7s    | ICE connection established ✅          | onIceConnectionChange(CONNECTED)
        | Audio routing configured               | setupAudioRouting()
T+7.5s  | Audio monitoring started               | startAudioMonitoring()
        | Status updated to "in_call"            | signaling.updateCallStatus()
T+8s    | Deepfake detection started             | startDeepfakeDetection()
        | Remote audio sink attached             | attachIncomingDetectionSink()
T+8.5s  | Detection service initialized          | DeepfakeDetectionService.startMonitoring()
        | Model warmed up                        | ModelRunner.warmUp()
T+10s   | First detection inference               | DeepfakeDetectionService loop
        | Audio buffer reached 3 seconds         | 
T+13s   | Second detection inference             | (continues every 3 seconds)
...     | Detection loop continues              | Saves to SQLite every 3s
...     | User controls (mute, speaker)         | Reflected in audio stream
T+60s   | User taps "Hang Up"                    | UI Interaction
T+60.5s | Status updated to "ended"              | signaling.updateCallStatus()
T+61s   | Detection stopped                      | DeepfakeDetectionService.stopMonitoring()
        | Peer connection closed                 | peerConnection.dispose()
T+61.5s | Firestore listeners removed            | signaling.stopListening()
        | Activity finished                      | Activity.finish()
T+62s   | IncomingCallListener restarted         | IncomingCallListener.start()
```

---

## 🔄 **DOCUMENT MODIFICATION SEQUENCE**

### Call Lifecycle Document Updates

```
Firebase "calls/{callId}" Document

Time    Status              offer_sdp       answer_sdp      Note
--------|------------------|-----------------|--------------|-------------------
T+0.1s  "ringing"          null             null            Created by caller
T+2s    "ringing"          [SDP_OFFER]      null            Caller sends offer
T+4.5s  "ringing"          [SDP_OFFER]      [SDP_ANSWER]    Callee sends answer
T+7.5s  "in_call"          [SDP_OFFER]      [SDP_ANSWER]    Connection established
T+60.5s "ended"            [SDP_OFFER]      [SDP_ANSWER]    Call terminated
```

### Detection Results Accumulation (SQLite)

```
Local "detection_results" Table

Timestamp       Call ID         Score   Is_Deepfake  Notes
----------------|----------------|---------|-------------|------------------
T+10s           {callId}        0.25    false        Genuine voice
T+13s           {callId}        0.28    false        Genuine voice
T+16s           {callId}        0.82    true         ⚠️ DEEPFAKE DETECTED
T+19s           {callId}        0.19    false        Back to genuine
T+22s           {callId}        0.45    false        Normal speech
... (continues every 3 seconds until T+60s)
```

---

## 📂 **COMPLETE FILE HIERARCHY FOR CALL LIFECYCLE**

```
📦 Call Lifecycle System
├── 🎯 Entry Points
│   ├── boundary/call/VOIPCallManager.kt
│   │   └─ startOutgoingVoipCall() - User initiates call
│   ├── boundary/call/CallInProgressActivity.kt
│   │   └─ onCreate() - Activity setup
│   └── boundary/handlers/CallMonitorService.kt
│       └─ Manages IncomingCallListener background service
│
├── 🔊 Call Detection & Notification
│   ├── control/call/IncomingCallListener.kt
│   │   ├─ start() - Watch Firestore for incoming calls
│   │   └─ stop() - Stop listening
│   ├── control/call/IncomingCallNotifier.kt
│   │   └─ Show incoming call notification UI
│   ├── control/call/IncomingCallIntent.kt
│   │   └─ Intent extra constants
│   └── control/call/ActiveCallStore.kt
│       └─ In-memory active call state storage
│
├── 🎬 UI & State Management
│   ├── boundary/call/CallInProgressViewModel.kt
│   │   ├─ UI state management (Ringing, Active, Disconnected)
│   │   ├─ Handle answer/hangup callbacks
│   │   └─ Mute/speaker toggle
│   └── boundary/call/CallInProgressScreen.kt
│       └─ Compose UI for call screen
│
├── 🔌 WebRTC Core
│   ├── control/webrtc/WebRTCClient.kt ⭐ [CRITICAL]
│   │   ├─ initialize() - Setup PeerConnectionFactory
│   │   ├─ createAudioTrack() - Create local audio source
│   │   ├─ createPeerConnection() - Setup peer connection
│   │   ├─ createOffer() / createAnswer() - SDP generation
│   │   ├─ onRemoteOfferReceived() - Handle offer
│   │   ├─ onRemoteAnswerReceived() - Handle answer
│   │   ├─ answerIncomingCall() - User accepts call
│   │   ├─ startDeepfakeDetection() - Initialize detection
│   │   ├─ attachIncomingDetectionSink() - Wire detection
│   │   ├─ setLocalAudioEnabled() - Mute/unmute
│   │   ├─ setSpeakerEnabled() - Speaker toggle
│   │   ├─ setupAudioRouting() - Configure audio
│   │   ├─ startAudioMonitoring() - Monitor audio levels
│   │   ├─ hangUp() / engineEnd() - Termination
│   │   └─ playDemoAudio() - Demo audio playback
│   │
│   └── control/webrtc/FirebaseSignalingManager.kt ⭐ [CRITICAL]
│       ├─ createCall() - Create Firebase call doc
│       ├─ listenToCall() - Listen for offer/answer/status
│       ├─ sendOffer() - Send SDP offer
│       ├─ sendAnswer() - Send SDP answer
│       ├─ sendIceCandidate() - Send ICE candidate
│       ├─ listenForIceCandidates() - Listen for remote candidates
│       ├─ updateCallStatus() - Update call status
│       ├─ endCall() - Mark call as ended
│       └─ stopListening() - Remove all listeners
│
├── 🎙️ Audio & Detection
│   ├── control/detection/DeepfakeDetectionService.kt ⭐ [CRITICAL]
│   │   ├─ startMonitoring() - Begin detection loop
│   │   ├─ feedAudioChunk() - Queue audio samples
│   │   ├─ Detection processing loop (inference)
│   │   ├─ stopMonitoring() - Stop detection
│   │   └─ onDeepfakeDetected callback
│   │
│   └── entity/ml/ModelRunner.kt
│       ├─ warmUp() - Pre-warm model
│       ├─ predict() - Run inference
│       └─ [Uses melcnn.pt PyTorch model]
│
├── 💾 Local Database (Room/SQLite)
│   ├── entity/data/db/AppDatabase.kt ⭐ [CRITICAL]
│   │   └─ getInstance() - Singleton database
│   ├── entity/data/entities/
│   │   ├── CallEntity.kt - Call records
│   │   ├── DetectionResultEntity.kt - Detection results
│   │   ├── AlertEventEntity.kt - Deepfake alerts
│   │   └── Other entities (User, Contact, etc.)
│   └── entity/data/dao/
│       ├── CallDao.kt
│       ├── DetectionResultDao.kt ⭐ [CRITICAL]
│       │   ├─ insert() - Save detection result
│       │   ├─ getByCallId() - Query by call
│       │   └─ getLatestByCallId() - Get latest result
│       └── Other DAOs
│
├── 🌐 Remote Database (Firebase)
│   └── data/remote/firebase/
│       ├── FirebaseAuthManager.kt - User authentication
│       ├── CallHistoryRepository.kt - Call history fetch
│       └── [Uses Firestore for call signaling]
│
└── 🛠️ Utilities
    ├── util/VibratorUtil.kt
    │   └─ vibrate() - Haptic feedback on detection
    └── [Other utilities]
```

---

## 🔑 **KEY DEPENDENCIES & FLOW**

```
Call Lifecycle Flow Dependencies:

┌─────────────────────────────────────────────────────────────────┐
│                    OUTGOING CALL                                │
├─────────────────────────────────────────────────────────────────┤
│ 1. VOIPCallManager → FirebaseSignalingManager.createCall()     │
│ 2. → CallInProgressActivity.onCreate()                         │
│ 3. → WebRTCClient initialization                               │
│    ├─ initialize() → createAudioTrack() → createPeerConnection()
│    └─ listenToCall() from Firestore                            │
│ 4. WebRTCClient.createOffer() → sendOffer() to Firestore       │
│ 5. Wait for answer from remote peer                            │
│ 6. onRemoteAnswerReceived() → ICE exchange begins              │
│ 7. ICE CONNECTED → startDeepfakeDetection()                    │
│ 8. Detection runs in parallel with call                        │
│ 9. hangUp() → cleanup → Activity.finish()                      │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    INCOMING CALL                                │
├─────────────────────────────────────────────────────────────────┤
│ 1. IncomingCallListener watches Firestore for ringing calls    │
│ 2. → Detects new call doc (status="ringing")                   │
│ 3. → Shows notification → launches CallInProgressActivity     │
│ 4. → WebRTCClient initialization (isCaller=false)             │
│ 5. → listenToCall() from Firestore                             │
│ 6. Wait for offer from remote peer                             │
│ 7. onRemoteOfferReceived() → setRemoteDescription() → offer applied
│ 8. UI shows "Answer" button (enabled)                          │
│ 9. User taps Answer → answerIncomingCall()                     │
│ 10. peerConnection.createAnswer() → sendAnswer() to Firestore │
│ 11. ICE CONNECTED → startDeepfakeDetection()                   │
│ 12. Detection runs in parallel with call                       │
│ 13. hangUp() → cleanup → Activity.finish()                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📋 **SUMMARY TABLE: FILES BY LIFECYCLE PHASE**

| Phase | Key Files | Primary Operations |
|-------|-----------|-------------------|
| **1. Call Creation** | VOIPCallManager, FirebaseSignalingManager, ActiveCallStore | Firebase call doc creation, Intent launch |
| **2. Incoming Detection** | IncomingCallListener, IncomingCallNotifier, CallMonitorService | Firestore listener, notification, activity launch |
| **3. Activity & VM Setup** | CallInProgressActivity, CallInProgressViewModel, AppDatabase | UI initialization, database access setup |
| **4. WebRTC Init** | WebRTCClient, FirebaseSignalingManager | PeerConnectionFactory, audio track, peer connection |
| **5. SDP Signaling** | WebRTCClient, FirebaseSignalingManager | Offer/answer creation, Firestore sync |
| **6. ICE Exchange** | WebRTCClient, FirebaseSignalingManager | Candidate gathering, network path establishment |
| **7. Detection** | WebRTCClient, DeepfakeDetectionService, ModelRunner, DetectionResultDao | Audio capture, inference, SQLite storage |
| **8. Termination** | WebRTCClient, FirebaseSignalingManager, CallInProgressActivity | Cleanup, listeners removal, activity finish |

---

## ✅ **VERIFICATION POINTS**

**Critical files to review for call flow understanding**:
1. [control/webrtc/WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt) - 1278 lines, core engine
2. [control/webrtc/FirebaseSignalingManager.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/FirebaseSignalingManager.kt) - Firestore coordination
3. [boundary/call/CallInProgressActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressActivity.kt) - Activity orchestration
4. [control/detection/DeepfakeDetectionService.kt](app/src/main/java/com/example/fyp_25_s4_23/control/detection/DeepfakeDetectionService.kt) - Detection engine
5. [entity/data/db/AppDatabase.kt](app/src/main/java/com/example/fyp_25_s4_23/entity/data/db/AppDatabase.kt) - Database schema
6. [control/call/IncomingCallListener.kt](app/src/main/java/com/example/fyp_25_s4_23/control/call/IncomingCallListener.kt) - Incoming call detection

