# Call Lifecycle - Visual Flow & Architecture

## 🏗️ High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CALL LIFECYCLE SYSTEM                             │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────┐         ┌──────────────────────┐
│    USER INTERFACE    │         │   BACKGROUND SERVICE │
├──────────────────────┤         ├──────────────────────┤
│ • DialerScreen       │         │ • CallMonitorService │
│ • CallInProgressUI   │         │ • IncomingCallList.  │
│ • CallHistoryScreen  │         │ • AntiDeepfakeService│
└──────────────────────┘         └──────────────────────┘
        ↓                                  ↓
┌──────────────────────┐         ┌──────────────────────┐
│   ACTIVITY LAYER     │         │   LISTENER LAYER     │
├──────────────────────┤         ├──────────────────────┤
│ CallInProgressActivty├←────────┼─IncomingCallListener │
│ CallInProgressVM     │         │                      │
└──────────────────────┘         └──────────────────────┘
        ↓                                  │
        └──────────────┬───────────────────┘
                       ↓
        ┌──────────────────────────────┐
        │  CORE WEBRTC ENGINE          │
        ├──────────────────────────────┤
        │ WebRTCClient (1278 lines)    │
        │ • initialize()               │
        │ • createAudioTrack()         │
        │ • createPeerConnection()     │
        │ • SDP offer/answer           │
        │ • ICE candidate exchange     │
        │ • startDeepfakeDetection()   │
        │ • hangUp()                   │
        └────────┬──────────┬──────────┘
                 │          │
    ┌────────────┘          └──────────────┐
    ↓                                       ↓
┌──────────────────────────┐    ┌──────────────────────────┐
│  DETECTION PIPELINE      │    │  FIREBASE SIGNALING      │
├──────────────────────────┤    ├──────────────────────────┤
│ • DeepfakeDetectionSvc   │    │ • FirebaseSignalingMgr   │
│ • ModelRunner            │    │ • Firestore listener     │
│ • Audio sink attachment  │    │ • SDP transmission       │
│ • Inference loop         │    │ • ICE candidate exchange │
│ • Vibration alerts       │    │ • Call status updates    │
└────────┬─────────────────┘    └───────────┬──────────────┘
         │                                   │
         │                    ┌──────────────┴──────────────┐
         │                    ↓                             ↓
         │            ┌─────────────────┐        ┌─────────────────┐
         │            │    FIRESTORE    │        │   LOCAL DB      │
         │            ├─────────────────┤        ├─────────────────┤
         │            │ calls/{callId}  │        │ • CallEntity    │
         │            │ • offer_sdp     │        │ • DetectionRslt │
         │            │ • answer_sdp    │        │ • AlertEvent    │
         │            │ • status        │        │ • User/Contact  │
         │            │ • ice_candidates│        └─────────────────┘
         │            └─────────────────┘
         │
         └────────────────────┬─────────────────────────┘
                              ↓
                    ┌─────────────────────┐
                    │    PERSISTENCE      │
                    ├─────────────────────┤
                    │ • SQLite (Room)     │
                    │ • Firestore         │
                    └─────────────────────┘
```

---

## 📞 Call Sequence Diagram - OUTGOING CALL

```
Caller          CallActivity    WebRTCClient    Firebase    Callee's Listener
  │                  │               │             │               │
  │ Click "Call"     │               │             │               │
  ├────────────────────→             │             │               │
  │                  │               │             │               │
  │                  │  initialize() │             │               │
  │                  ├──────────────→│             │               │
  │                  │               │ createCall()│               │
  │                  │               ├────────────→│               │
  │                  │               │             │ [NEW CALL DOC]│
  │                  │               │             │ status:"ringing"
  │                  │               │             ├──────────────→│
  │                  │               │             │  (notification)
  │                  │ createOffer() │             │               │
  │                  ├──────────────→│             │               │
  │                  │               │ sendOffer()│               │
  │                  │               ├────────────→│               │
  │                  │               │             │[UPDATE offer] │
  │                  │               │  listenCall() (await)      │
  │                  │               ├────────────→│               │
  │                  │               │             │←─── ANSWER ───┤
  │                  │  onRemoteAnswer             │[UPDATE answer]│
  │                  │←──────────────┤             │               │
  │                  │               │             │               │
  │                  │ [ICE exchange]│             │               │
  │                  ├──────────────→├────────────→├──────────────→│
  │                  │               │             │               │
  │                  │[ICE CONNECTED]│             │               │
  │                  ├──────────────→│             │               │
  │                  │               │ startDetection()           │
  │                  │               ├──────────────────────────→│
  │                  │               │             │ status:"in_call"
  │                  │               │             ├──────────────→│
  │                  │               │             │               │
  │ [CALL ACTIVE]    │               │             │               │
  │ [DETECTION]      │               │             │               │
  │ [AUDIO FLOW]     │               │             │               │
  │                  │               │             │               │
  │ Tap "Hang Up"    │               │             │               │
  ├────────────────────→             │             │               │
  │                  │   hangUp()    │             │               │
  │                  ├──────────────→│             │               │
  │                  │               │ updateStatus("ended")      │
  │                  │               ├────────────→│               │
  │                  │               │             │[UPDATE status]│
  │                  │               │             ├──────────────→│
  │                  │               │  stopListening()           │
  │                  │               ├────────────→│               │
  │                  │               │             │               │
  │ [CALL ENDED]     │               │             │               │
  │                  │               │             │               │
```

---

## 🎧 Audio & Detection Subflow

```
Remote Peer                    WebRTCClient              DetectionService     Database
  │                                 │                           │                 │
  │                                 │                           │                 │
  │ [RTP Audio Stream]              │                           │                 │
  ├────────────────────────────────→│                           │                 │
  │                                 │                           │                 │
  │                              onTrack()                       │                 │
  │                                 ├─→ remoteAudioTrack       │                 │
  │                                 │                           │                 │
  │                    attachIncomingDetectionSink()            │                 │
  │                                 ├──────────────────────────→│                 │
  │                                 │                           │                 │
  │                                 │  onFrame() [300+ ms]      │                 │
  │                                 ├──────────────────────────→│                 │
  │                                 │                           │                 │
  │                                 │                      feedAudioChunk()       │
  │                                 │                           │                 │
  │                                 │                      [3s buffer]            │
  │                                 │                           │                 │
  │ [RTP Audio continues...]        │                           │                 │
  ├────────────────────────────────→│                           │                 │
  │                                 │                      processingLoop()       │
  │                                 │                           │                 │
  │                                 │                      ModelRunner.predict()  │
  │                                 │                           │                 │
  │                                 │                      [Inference]            │
  │                                 │                           │                 │
  │                                 │                      detectionDao.insert()  │
  │                                 │                           ├────────────────→│
  │                                 │                           │   [ROW: prob=X] │
  │                                 │                           │   [isDeepfake]  │
  │                                 │                           │                 │
  │                                 │    onDeepfakeDetected()    │                 │
  │                                 │←──────────────────────────┤                 │
  │                                 │                           │                 │
  │                              [Vibrate UI]                   │                 │
  │                                 │                           │                 │
  │ [Audio continues...]            │                           │                 │
  ├────────────────────────────────→│                           │                 │
  │                                 │  [Loop repeats every 3s]  │                 │
  │                                 │                           │                 │
```

---

## 🎬 State Machines

### Call State Machine (CallUiState)
```
                     ┌──────────────┐
                     │   Connecting │
                     │ (initializing)
                     └───────┬──────┘
                             │
                    ┌────────▼────────┐
                    │     Ringing      │
                    │ (waiting for SDP)
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │      Active      │
                    │(call connected) │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  Disconnected    │
                    │(call ended/failed
                    └──────────────────┘
```

### ICE Connection State Machine
```
              ┌──────────────────┐
              │   new            │
              └────────┬─────────┘
                       │
              ┌────────▼────────┐
              │   CHECKING       │ ← ICE gathering & connecting
              │ (gathering cands)
              └────────┬────────┘
                       │
     ┌─────────────────┼─────────────────┐
     │                 │                 │
     ▼                 ▼                 ▼
┌────────────┐   ┌────────────┐   ┌──────────────┐
│ CONNECTED  │   │ COMPLETED  │   │  DISCONNECTED│
│ (P2P OK)   │   │ (fallback) │   │  (temp loss) │
└─────┬──────┘   └─────┬──────┘   └────────┬─────┘
      │                │                   │
      │ startDetection │                   │ [reconnect attempt]
      │                │                   │
      └────────┬───────┘                   │
               │                           │
               │         ┌─────────────────┘
               │         │
               ▼         ▼
           ┌──────────────────┐
           │      FAILED      │ ← ICE failed
           │ (end call)       │
           └──────────────────┘
```

### Detection State Machine
```
    START
      │
      ▼
┌──────────────┐
│  CREATED     │
│ (not yet     │
│  monitoring) │
└──────┬───────┘
       │ startMonitoring()
       ▼
┌──────────────┐
│ MONITORING   │
│ (loop active)│
└──────┬───────┘
       │
       ├─→ [Buffer < 3s] → Wait
       │
       ├─→ [Buffer ≥ 3s] → Infer
       │
       ├─→ [Score > threshold] → Alert
       │
       │ stopMonitoring()
       ▼
┌──────────────┐
│  STOPPED     │
│ (cleanup)    │
└──────────────┘
```

---

## 💾 Data Flow - Detection Results

```
Remote Audio Stream
        │
        ▼
┌──────────────────────────────┐
│  WebRTC Remote Audio Track   │
│  (RTP packets)               │
└───────────┬──────────────────┘
            │
            ▼
┌──────────────────────────────┐
│  AudioSink.onFrame()         │
│  (intercept audio buffer)    │
└───────────┬──────────────────┘
            │
            ├─→ Convert to mono (if stereo)
            ├─→ Resample to 16kHz (if needed)
            ├─→ Convert to ShortArray (16-bit PCM)
            │
            ▼
┌──────────────────────────────┐
│  DeepfakeDetectionService    │
│  feedAudioChunk(shortArray)  │
└───────────┬──────────────────┘
            │
            ▼
┌──────────────────────────────┐
│  audioBufferQueue            │
│  (ConcurrentLinkedQueue)     │
│                              │
│  queuedSamples counter       │
│  maxBufferedSamples = limit  │
└───────────┬──────────────────┘
            │
    ┌───────┴────────┐
    │                │
    ▼ (background)   ▼ (main)
processingLoop()    [if paused for demo]
    │
    │ Every 500ms check:
    │ if (queuedSamples >= targetSamples)
    │
    ▼
┌──────────────────────────────┐
│  Dequeue & Convert           │
│  ShortArray → FloatArray     │
│  (normalize to [-1, 1])      │
└───────────┬──────────────────┘
            │
            ▼
┌──────────────────────────────┐
│  ModelRunner.predict()       │
│  (PyTorch inference)         │
│                              │
│  Returns: [prob_genuine,     │
│            prob_deepfake]    │
└───────────┬──────────────────┘
            │
            ├─→ score = prob_deepfake
            ├─→ isDeepfake = score > threshold
            │
            ▼
┌──────────────────────────────┐
│  DetectionResult            │
│  • score: Float             │
│  • isDeepfake: Boolean      │
│  • timestamp: Long          │
│  • confidence: Float        │
└───────────┬──────────────────┘
            │
            ├─→ Update StateFlow
            │   (UI can observe)
            │
            ├─→ If isDeepfake:
            │   onDeepfakeDetected(score)
            │   → Vibrate UI
            │
            ▼
┌──────────────────────────────┐
│  DetectionResultDao.insert() │
│  (Room suspend function)     │
└───────────┬──────────────────┘
            │
            ▼
┌──────────────────────────────┐
│  SQLite Database            │
│                              │
│  Table: detection_results   │
│  • id: UUID                 │
│  • call_id: String          │
│  • probability: Float       │
│  • is_deepfake: Boolean     │
│  • timestamp_seconds: Long  │
│  • model_version: String    │
│  • confidence_level: String │
└──────────────────────────────┘
```

---

## 🔄 Lifecycle Timeline with File Operations

```
TIME    FILE                              OPERATION          DATABASE
─────────────────────────────────────────────────────────────────────────────
0ms     VOIPCallManager                   startOutgoingCall   
        FirebaseSignalingManager          createCall()        → Firebase WRITE
        
100ms   CallInProgressActivity            onCreate()
        ActiveCallStore                   setWebRtcActive()   → In-Memory
        AppDatabase                       getInstance()       → SQLite OPEN
        
200ms   WebRTCClient                      initialize()
                                          createAudioTrack()
                                          createPeerConnection()
        
500ms   FirebaseSignalingManager          listenToCall()      → Firebase LISTEN
        
2000ms  WebRTCClient                      createOffer()
        FirebaseSignalingManager          sendOffer()         → Firebase WRITE
        
5000ms  [Remote receives offer via listener, responds]
        
5500ms  FirebaseSignalingManager          sendAnswer()        → Firebase WRITE
        WebRTCClient                      onRemoteAnswer()
        
7000ms  WebRTCClient                      ICE candidates
        FirebaseSignalingManager          sendIceCandidate()  → Firebase WRITE (×N)
                                          listenForIceCand()  → Firebase LISTEN
        
7500ms  WebRTCClient                      onIceConnectionChange(CONNECTED)
                                          startAudioRouting()
                                          startAudioMonitoring()
        FirebaseSignalingManager          updateCallStatus()  → Firebase WRITE
        
8000ms  WebRTCClient                      startDeepfakeDetection()
        DeepfakeDetectionService          startMonitoring()
        ModelRunner                       warmUp()
        
8500ms  WebRTCClient                      attachIncomingDetectionSink()
        
10000ms DeepfakeDetectionService          [1st inference]
        DetectionResultDao                insert()            → SQLite INSERT
        
13000ms DeepfakeDetectionService          [2nd inference]
        DetectionResultDao                insert()            → SQLite INSERT
        
... [continues every 3 seconds during call]

60000ms CallInProgressViewModel            hangUp()
        WebRTCClient                      engineEnd()
        DeepfakeDetectionService          stopMonitoring()
        
60500ms FirebaseSignalingManager          updateCallStatus()  → Firebase WRITE
        
61000ms CallInProgressActivity            onDestroy()
        FirebaseSignalingManager          stopListening()     → Firebase UNLISTEN
        ActiveCallStore                   clear()             → In-Memory CLEAR
        
61500ms IncomingCallListener              start()             → Firebase LISTEN
```

---

## 🔌 Module Dependencies Graph

```
┌─────────────────────────────────────────────────────────────────┐
│                       UI LAYER                                  │
│  CallInProgressActivity ← CallInProgressViewModel               │
│         ↓                                                        │
│  CallInProgressScreen ← CallInProgressViewModel.state           │
└────────────────┬──────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ORCHESTRATION LAYER                          │
│  WebRTCClient ← FirebaseSignalingManager                        │
│  DeepfakeDetectionService ← WebRTCClient                        │
│  ModelRunner ← DeepfakeDetectionService                         │
└────────────────┬──────────────────────────────────────────────┘
                 │
        ┌────────┴──────────┐
        ▼                   ▼
┌──────────────────┐  ┌──────────────────┐
│  FIRESTORE       │  │  LOCAL DATABASE  │
│  • Signaling     │  │  • Room/SQLite   │
│  • Call docs     │  │  • Entities      │
│  • ICE cands     │  │  • DAOs          │
│  • Status        │  │  • Repositories  │
└──────────────────┘  └──────────────────┘
```

---

## ⏳ Critical Timing

```
Event                          Timeout/Interval    File
──────────────────────────────────────────────────────────────
Ring timeout (caller)          60 seconds          WebRTCClient
ICE extended timeout           +30 seconds         WebRTCClient
Audio monitoring poll           300ms              WebRTCClient
Detection buffer accumulation   3 seconds          DeepfakeDetectionService
Detection inference cycle       ~500ms             DeepfakeDetectionService
Incoming call filter            ≤5 minutes old     IncomingCallListener
ICE failure wait                15 seconds         WebRTCClient
Firestore listener timeout      5 minutes          FirebaseSignalingManager
```

---

## 🛡️ Error Handling Flows

```
Ring Timeout (60s)
  ↓ [No answer received]
  ├─→ Check if ICE in progress
  │   ├─ YES: Extend timeout 30s
  │   └─ NO: End call immediately
  ↓
ICE FAILED
  ├─→ engineEnd("ICE_FAILED")
  ├─→ updateCallStatus("ended")
  └─→ Activity.finish()

Audio Sink Creation Fails
  ├─→ Log warning
  ├─→ Continue call without detection
  └─→ User not notified

Model Warmup Fails
  ├─→ Log error
  ├─→ Detection loop continues
  ├─→ predict() may fail later
  └─→ Fallback to safe score

Detection Insert Fails
  ├─→ Log error
  ├─→ Continue monitoring
  ├─→ Try insert again next cycle
  └─→ Results may be lost

Firebase Write Fails
  ├─→ Log error
  ├─→ Retry via WebRTC fallback
  │   (usually recovers)
  └─→ Call may still work locally
```

---

## 📈 Resource Usage

```
Component                   Memory      CPU         Threads
─────────────────────────────────────────────────────────
WebRTCClient                ~50-100MB   ~5-15%      3-5
  (audio encoding/decoding)

DeepfakeDetectionService    ~100-200MB  ~20-40%     2-3
  (model inference, buffering)

ModelRunner                  ~150MB      ~15-30%     1
  (PyTorch model)

FirebaseSignalingManager    ~10MB       <1%         2
  (listener threads)

Room Database              ~5-10MB      <1%         1
  (SQLite + DAOs)

Total Per Call             ~300-500MB   ~40-85%     10-15
```

---

## 🎯 Key Integration Points

```
Between WebRTCClient & DeepfakeDetectionService:
  ├─ attachIncomingDetectionSink() - Wire audio
  ├─ startDeepfakeDetection() - Initialize
  ├─ onDeepfakeDetected callback - Alert
  └─ stopMonitoring() - Cleanup

Between WebRTCClient & FirebaseSignalingManager:
  ├─ listenToCall() - Receive offer/answer
  ├─ sendOffer/Answer() - Send SDP
  ├─ sendIceCandidate() - Send candidates
  ├─ updateCallStatus() - Status sync
  └─ stopListening() - Cleanup

Between DeepfakeDetectionService & Database:
  ├─ AppDatabase.getInstance() - Access
  ├─ detectionResultDao.insert() - Persist
  └─ [Direct SQLite writes for speed]

Between CallInProgressActivity & WebRTCClient:
  ├─ initialize() - Init WebRTC
  ├─ onEngineEnded callback - Cleanup
  ├─ answerIncomingCall() - User action
  └─ hangUp() - User termination

Between CallInProgressViewModel & UI:
  ├─ state: StateFlow - UI updates
  ├─ events: SharedFlow - One-time events
  ├─ callbacks - User interactions
  └─ vibrate events - Haptic feedback
```

