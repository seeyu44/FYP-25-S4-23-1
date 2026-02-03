# Demo Audio Loading, Sensing, Detection & Monitoring Flow

## Complete Audio Journey from UI Button to Detection Alert

---

## STEP 1: User Activates Demo Audio (UI Layer)

**File**: [CallInProgressScreen.kt](CallInProgressScreen.kt#L179-L230)

```
User sees: "Play Demo Audio" button during active call
           ↓
User selects from dropdown: "D_0000000190.wav" (or other file)
           ↓
User presses "Play" button
           ↓
Compose callback triggered: onPlayDemoAudio?.invoke(selectedFile)
           ↓
That calls: webRtcClient?.playDemoAudio(filename)
```

**Key UI Elements**:
- Blue dropdown button: "Select Audio"
- Green "Play" button when file selected
- Red "Stop" button when playing
- Button text changes from "Play" → "Stop"

**Code Path**:
```kotlin
// CallInProgressScreen.kt, lines 179-230
if (selectedFile != null) {
    Button(
        onClick = {
            if (isDemoPlaying) {
                isDemoPlaying = false
                onPlayDemoAudio(null)  // Stop
            } else {
                isDemoPlaying = true
                onPlayDemoAudio(selectedFile)  // Play
            }
        }
    ) {
        Text(if (isDemoPlaying) "Stop" else "Play")
    }
}
```

---

## STEP 2: WebRTCClient Loads Demo Audio File from Assets

**File**: [WebRTCClient.kt](WebRTCClient.kt#L1064-1100)

### Entry Point: playDemoAudio()
```
webRtcClient?.playDemoAudio(filename)
           ↓
if (filename != null) {
    val assetPath = "demo_audio/$filename"
    val wav = loadDemoWav(assetPath)  ← Loads from assets
    
    synchronized(demoLock) {
        demoPcm = wav.first        ← ShortArray of audio samples
        demoSampleRate = wav.second ← Original sample rate (e.g., 16000)
        demoPosition = 0.0          ← Playback position
        demoEnabled = true          ← Flag: inject into encoder
    }
}
```

### What Gets Loaded
- **Location**: `app/src/main/assets/demo_audio/`
- **Current files**: 12 demo audio files (WAV, FLAC, MP3, M4A formats)
- **Example files**: `D_0000000190.wav`, `dt_zaviertest.mp3`, etc.
- **What's loaded**: Entire audio file converted to `ShortArray` of PCM samples
  - Example: 3-second audio at 16kHz = 48,000 short values
  - Each short represents a -32768 to +32767 sample value

### loadDemoWav() Details: [WebRTCClient.kt](WebRTCClient.kt#L339-390)

```
loadDemoWav("demo_audio/D_0000000190.wav")
           ↓
Opens WAV file from assets
           ↓
Parses WAV header:
  - Channels (mono/stereo)
  - Sample rate (e.g., 16kHz, 48kHz)
  - Bits per sample (16-bit PCM)
           ↓
Extracts PCM data from "data" chunk
           ↓
If stereo → downmix to mono (average channels)
           ↓
If sample rate ≠ WebRTC rate → resample to match
           ↓
Returns: Pair<ShortArray, Int>
         - ShortArray: PCM samples
         - Int: Sample rate (normalized to internal rate)
```

**Log Output When Loaded**:
```
I/DEMO_AUDIO: ✅ Demo injection enabled (sampleRate=16000)
```

---

## STEP 3: Demo Audio Injected into Microphone Stream

**File**: [WebRTCClient.kt](WebRTCClient.kt#L120-125)

### How It Works

The key is `setSamplesReadyCallback`:

```kotlin
audioDeviceModule = JavaAudioDeviceModule.builder(context)
    .setSamplesReadyCallback { audioSamples ->
        // Intercept OUTGOING mic PCM BEFORE it enters WebRTC
        if (demoEnabled) {
            replaceOutgoingWithDemo(audioSamples)
        }
    }
    .createAudioDeviceModule()
```

### What Is JavaAudioDeviceModule?
- **Purpose**: Captures microphone audio in WebRTC
- **Default behavior**: Reads from device microphone
- **setSamplesReadyCallback**: Intercepts audio **BEFORE** encoder
- **Called every**: ~10-20ms (audio frames)
- **Audio format**: Short array (int16 PCM)

### Audio Flow on Sender Device (Outgoing):
```
Microphone (48kHz, 48 samples every ~1ms)
    ↓
JavaAudioDeviceModule captures samples
    ↓
setSamplesReadyCallback triggered
    ↓
IF demoEnabled:
    replaceOutgoingWithDemo() replaces mic with demo PCM
    ↓
Modified audio passed to WebRTC encoder (Opus codec)
    ↓
Encoded → RTP packets → Network → Remote peer
```

---

## STEP 4: Demo Audio Modified & Injected

**File**: [WebRTCClient.kt](WebRTCClient.kt#L190-225)

### replaceOutgoingWithDemo() Function

This is where the **magic happens** - the actual audio replacement:

```
INPUT:  audioSamples (current mic PCM frame, ~48 samples at 48kHz)
        {5, 100, -50, 200, ...}  ← Current microphone values

PROCESS:
    1. Convert byte array to short array
    2. Calculate RMS before (mic noise level)
    3. Call writeDemoInto(shorts, sampleRate, channels)
       ← This fills shorts with demo audio instead of mic
    4. Calculate RMS after (demo energy)
    5. Write modified shorts back to byte array
       ← FIXED: Now using explicit byte-level copying
    6. Return to encoder

OUTPUT: audioSamples now contains demo PCM
        {8234, 12100, -5050, 21200, ...}  ← Demo audio values
```

**Key Fix (Issue #3)**:
```kotlin
// ✅ FIXED: Explicit byte-level copying
for (i in shorts.indices) {
    val offset = i * 2
    val short = shorts[i]
    data[offset] = (short.toInt() and 0xFF).toByte()
    data[offset + 1] = ((short.toInt() shr 8) and 0xFF).toByte()
}
```

This guarantees the demo data actually reaches the encoder (was the critical bug).

### writeDemoInto() Details: [WebRTCClient.kt](WebRTCClient.kt#L313-340)

```
writeDemoInto(target=shorts, sampleRate=48000, channels=1)
    ↓
1. Zero out the target array (clear old mic data)
   java.util.Arrays.fill(target, 0)
    ↓
2. Resample demo to WebRTC's sample rate:
   demoSampleRate = 16000 (demo file)
   sampleRate = 48000 (WebRTC captures at this rate)
   step = 16000 / 48000 = 0.333...
    ↓
3. Loop through target frames, interpolate from demo:
   for (i in 0 until frames) {
       srcIndex = demoPosition.toInt()
       sample = demoPcm[srcIndex]  ← Get demo sample
       target[i] = sample          ← Write to output
       demoPosition += step        ← Advance by resampling factor
   }
    ↓
4. Handle looping:
   if (demoPosition >= demoPcm.size) {
       demoPosition -= demoPcm.size  ← Loop back to start
   }
```

**Log Output Every ~50 Frames**:
```
D/AUDIO_PIPELINE: ✅ Demo PCM written (RMS: 7698 vs Mic: 0) → will be encoded and transmitted
D/AUDIO_PIPELINE_DEMO: 📝 Demo resample step=0.3333, frames=48, channels=1, demoRms=7698
```

---

## STEP 5: Audio Encoded & Transmitted Over RTP

**File**: WebRTC (Android library, not user code)

```
Modified audio with demo PCM
    ↓
WebRTC encoder (Opus codec, ~20ms frames)
    ↓
Encodes to compressed audio packets
    ↓
RTP protocol (Real-time Transport Protocol)
    ↓
Network transmission to remote peer
    ↓
Remote peer receives RTP packets
```

---

## STEP 6: Receiving Side - Audio Arrives & Decoded

**File**: [WebRTCClient.kt](WebRTCClient.kt#L230-310)

### attachIncomingDetectionSink() 

```
Remote RTP packets arrive with demo audio
    ↓
WebRTC decoder (Opus) decompresses to PCM
    ↓
AudioTrack (Android system audio player)
    ↓
attachIncomingDetectionSink() intercepts audio
```

**Code**:
```kotlin
incomingAudioSink = object : AudioTrackSink {
    override fun onData(
        data: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        channels: Int,
        frames: Int,
        timestamp: Long
    ) {
        // data contains decoded remote audio
        // (which includes demo audio if sender played it)
        
        // Convert to 16kHz mono if needed
        val resampled = convertTo16kHzMono(...)
        
        // Feed to detection service
        detectionService?.feedAudioChunk(resampled)
    }
}
```

### Audio Conversion: [WebRTCClient.kt](WebRTCClient.kt#L240-300)

```
INCOMING: Remote audio (possibly 48kHz, stereo)
    ↓
1. Convert ByteBuffer to short array
2. If stereo → downmix to mono (average channels)
3. If sample rate ≠ 16kHz → resample to 16kHz
4. Normalize to float: short / 32768.0f
    ↓
OUTPUT: FloatArray at 16kHz, mono
        {0.125f, 0.375f, -0.154f, ...}  ← Normalized [-1.0, 1.0]
```

---

## STEP 7: Demo Audio Fed to Detection Service

**File**: [DeepfakeDetectionService.kt](DeepfakeDetectionService.kt#L130-156)

### feedAudioChunk() Entry Point

```
detectionService?.feedAudioChunk(resampled)
    ↓
if (!isRunning) return
    ↓
1. Convert short PCM to float array:
   floatData[i] = pcmData[i] / 32768f
    ↓
2. Add to audio buffer queue:
   audioBufferQueue.offer(floatData)
   queuedSamples += floatData.size
    ↓
3. Limit buffer (prevent memory leak):
   while (queuedSamples > maxBufferedSamples) {
       removed = audioBufferQueue.poll()
       queuedSamples -= removed.size
   }
```

**Log Output (first 5 chunks)**:
```
D/DEEPFAKE_DETECT: 📥 Received audio chunk: 960 samples, queue size: 0
D/DEEPFAKE_DETECT: 📥 Received audio chunk: 960 samples, queue size: 1
...
```

### Buffer Accumulation

```
Buffer setup:
  - Target: 48,000 samples (3 seconds at 16kHz)
  - Each chunk: 960 samples (~60ms frame)
  - Need: ~50 chunks to fill buffer

When demo audio arrives:
  - HIGH energy samples (demo is ~7698 RMS)
  - vs normal speech (low RMS ~0.007)
  - This difference is CRUCIAL for detection
```

---

## STEP 8: Buffered Audio Processed & Detected

**File**: [DeepfakeDetectionService.kt](DeepfakeDetectionService.kt#L164-200)

### Background Processing Loop (runs every 1 second)

```
Background coroutine (Dispatchers.Default)
    ↓
while (isRunning && isActive) {
    processAudioBuffer()  ← Check if we have 48,000 samples
    delay(1000)           ← Wait 1 second before checking again
}
```

### processAudioBuffer()

```
Accumulated samples: 48,000 floats (3 seconds of audio)
    ↓
STEP 1: Drain queue and concatenate samples
        audioSegment = FloatArray(targetSamples)  // 48,000
        while (queue not empty) {
            chunk = queue.poll()
            Copy chunk into audioSegment
        }
    ↓
STEP 2: Validate input audio (NEW DIAGNOSTIC LOGGING)
        val rms = sqrt(audioSegment.map { it*it }.average())
        val maxSample = audioSegment.maxOrNull()
        val minSample = audioSegment.minOrNull()
        val nonZeroCount = audioSegment.count { it != 0f }
        
        Log.d("DEEPFAKE_DETECT", "📊 Input audio: RMS=0.125, Max=0.95, Min=-0.87, NonZeroSamples=47234/48000")
        
        ← THIS TELLS US IF DEMO AUDIO ACTUALLY ARRIVED!
        ← Demo should have HIGH RMS (0.235+)
        ← Normal speech: RMS ~0.01-0.05
    ↓
STEP 3: Send to inference
        runInference(audioSegment)
```

**Key Insight**: The RMS value tells us everything:
- **Normal speech**: RMS ~ 0.01 - 0.05 (quiet)
- **Demo audio**: RMS ~ 0.2 - 0.3 (loud synthetic)
- **If RMS is low when demo should be playing**: Demo didn't reach receiver

---

## STEP 9: Inference - Model Analysis

**File**: [DeepfakeDetectionService.kt](DeepfakeDetectionService.kt#L207-260)

### runInference() Flow

```
INCOMING: audioSegment (48,000 float samples)
    ↓
STEP 1: Preprocess to mel spectrogram
        audioPreprocessor.preprocess(audioSegment)
        ↓
        - Simple VAD (trim silence)
        - Pad/crop to exactly 48,000 samples
        - Compute mel spectrogram:
          * FFT: n_fft=1024
          * Hop: 256 (overlapping windows)
          * Mel bands: 64
        - Convert to dB scale
        - Normalize: (value - mean) / std
        ↓
        Result: Array<FloatArray> = [64 mel bands] x [time frames]
    ↓
STEP 2: Run ONNX model inference
        modelRunner.inferMel(mel)
        ↓
        Input: [1, 1, 64, ~188] tensor (4D)
        Output: Single float value [0.0 - 1.0]
        ↓
        Returns: score (sigmoid activated)
    ↓
STEP 3: Compare to threshold
        isDeepfake = score >= 0.7f
        
        Examples:
        - Demo audio: score = 0.92 → isDeepfake = TRUE ✅
        - Normal speech: score = 0.08 → isDeepfake = FALSE ✓
        - False positive: score = 0.78 → isDeepfake = TRUE ❌ (BUG!)
    ↓
STEP 4: Log result
        Log.i("DEEPFAKE_DETECT", "Detection result: score=0.92, isDeepfake=true")
```

**Log Output**:
```
D/DEEPFAKE_DETECT: 📊 Input audio: RMS=0.235, Max=0.95, Min=-0.89, NonZeroSamples=47890/48000
D/DEEPFAKE_DETECT: 🔄 Preprocessing audio to mel spectrogram...
D/DEEPFAKE_DETECT: 📊 Input audio validated before preprocessing
I/DEEPFAKE_DETECT: Detection result: score=0.92, isDeepfake=true (threshold=0.7)
D/TAG: ✅ Detection saved to database
```

---

## STEP 10: Callbacks Triggered - UI & User Notified

**File**: [DeepfakeDetectionService.kt](DeepfakeDetectionService.kt#L279-295)

### Callback Execution

```
DETECTION RESULT: score=0.92, isDeepfake=true
    ↓
if (isDeepfake) {
    onDeepfakeDetected?.invoke(score)  ← Callback #1
    Log.w("DEEPFAKE", "━━━ INCOMING AUDIO FLAGGED AS DEEPFAKE! ━━━")
}
    ↓
ALWAYS:
    val result = DetectionResult(
        score = score,
        isDeepfake = isDeepfake,
        timestamp = timestamp,
        confidence = score
    )
    onDetectionUpdate?.invoke(result)  ← Callback #2
```

### Callback #1: onDeepfakeDetected

**File**: [WebRTCClient.kt](WebRTCClient.kt#L1035-1041)

```kotlin
detectionService?.onDeepfakeDetected = { score ->
    Log.w("DEEPFAKE", "━━━ INCOMING AUDIO FLAGGED AS DEEPFAKE! ━━━")
    Log.w("DEEPFAKE", "   Score: $score")
    onDeepfakeDetected?.invoke(score, true)  ← Pass to ViewModel
}
```

### Callback #2: onDetectionUpdate

**File**: [WebRTCClient.kt](WebRTCClient.kt#L1042-1047)

```kotlin
detectionService?.onDetectionUpdate = { result ->
    Log.d("DEEPFAKE", "📊 Incoming audio analyzed: score=${result.score}")
    onDetectionUpdate?.invoke(result.score)  ← Pass to ViewModel
    if (result.isDeepfake) {
        onDeepfakeDetected?.invoke(result.score, true)
    }
}
```

---

## STEP 11: ViewModel Updates UI State

**File**: [CallInProgressViewModel.kt](CallInProgressViewModel.kt#L114-148)

### onDeepfakeDetected Handler

```kotlin
client?.onDeepfakeDetected = { score, isDeepfake ->
    val current = _state.value
    if (current is CallUiState.Active) {
        _state.value = current.copy(
            detectionScore = score,        ← Update score display
            isDeepfake = isDeepfake,       ← Set RED state
            isDetectionActive = true
        )
        if (isDeepfake && !alreadyAlerted) {
            viewModelScope.launch {
                _events.emit(CallUiEvent.Vibrate(score))  ← Phone vibrates!
            }
        }
    }
}
```

### onDetectionUpdate Handler (✅ FIXED - Issue #1)

```kotlin
client?.onDetectionUpdate = { score ->
    val current = _state.value
    if (current is CallUiState.Active) {
        val detectionThreshold = 0.7f
        val isCurrentlyDeepfake = score >= detectionThreshold  ← LIVE UPDATE
        _state.value = current.copy(
            detectionScore = score,
            isDeepfake = isCurrentlyDeepfake,  ← KEY FIX: Updates dynamically!
            isDetectionActive = true
        )
        Log.d(TAG_CALL, "🔄 UI updated: score=$score, isDeepfake=$isCurrentlyDeepfake")
    }
}
```

---

## STEP 12: UI Renders Detection Alert

**File**: [CallInProgressScreen.kt](CallInProgressScreen.kt#L409-440)

### DeepfakeDetectionIndicator Composable

```kotlin
DeepfakeDetectionIndicator(
    score = activeState.detectionScore,      // 0.92
    isDeepfake = activeState.isDeepfake      // true
)
    ↓
Renders:
    if (isDeepfake) {
        // RED ALERT
        bgColor = Color(0xFFFF3B30)
        emoji = "🚨"
        statusText = "DEEPFAKE DETECTED"
    } else if (score > 0.3f) {
        // ORANGE WARNING
        bgColor = Color(0xFFFF9500)
        emoji = "⚠️"
        statusText = "Suspicious"
    } else {
        // GREEN OK
        bgColor = Color(0xFF34C759)
        emoji = "✅"
        statusText = "Real Voice"
    }
    ↓
Display on screen with animated background color transition
```

**User Sees**:
```
┌─────────────────┐
│  🚨             │
│ DEEPFAKE        │  ← RED background
│ DETECTED        │
│ Score: 0.92     │
└─────────────────┘

Phone vibrates! 📳
```

---

## Complete Timeline Example: Demo Audio Detection

```
00:00.0 - User presses "Play" on D_0000000190.wav
00:00.1 - WebRTCClient.playDemoAudio() called
00:00.2 - WAV file loaded from assets (3 seconds, 48,000 samples)
00:00.3 - demoEnabled = true, demoSampleRate = 16000
00:00.4 - onPlayDemoAudio callback returns

(Next mic frame ~10ms later)
00:00.5 - setSamplesReadyCallback triggered
00:00.6 - replaceOutgoingWithDemo() replaces mic with demo PCM
00:00.7 - Log: "✅ Demo PCM written (RMS: 7698 vs Mic: 0)"
00:00.8 - Modified audio enters encoder
00:00.9 - Encoder transmits RTP packets

(Receiver's end, simultaneous)
00:00.5 - RTP packets received
00:00.6 - Decoded to PCM (demo audio)
00:00.7 - attachIncomingDetectionSink() gets demo samples
00:00.8 - feedAudioChunk() called with demo data (high RMS)

(After ~3 seconds accumulation)
00:03.0 - 48,000 samples accumulated in buffer
00:03.1 - processAudioBuffer() detects buffer is full
00:03.2 - Log: "📊 Input audio: RMS=0.235 ..." (high because demo!)
00:03.3 - runInference() preprocesses to mel spectrogram
00:03.4 - Model inference: score = 0.92
00:03.5 - onDeepfakeDetected callback triggered
00:03.6 - WebRTCClient relays to ViewModel
00:03.7 - CallInProgressViewModel updates UI state
00:03.8 - UI renders RED "DEEPFAKE DETECTED" alert
00:03.9 - Phone vibrates
00:04.0 - User sees detection alert on screen 🚨
```

---

## How App "Senses" Audio Quality Changes

The app doesn't monitor volume - it monitors **RMS energy** at the detection service:

```
Normal Human Speech:
├─ Typical RMS: 0.01 - 0.05 (quiet to moderate)
├─ Mel spectrogram: Scattered across frequency bands
├─ Natural variation in amplitude
└─ Model score: 0.05 - 0.15 (LOW)

Deepfake/Synthetic Audio:
├─ Typical RMS: 0.2 - 0.35 (louder, consistent)
├─ Mel spectrogram: Different frequency patterns
├─ Artificial, processed characteristics
└─ Model score: 0.8 - 0.99 (HIGH)

False Positive (Human voice scored as deepfake):
├─ Typical RMS: 0.2 - 0.3 (loud speaker!)
├─ Mel spectrogram: Unusual patterns
├─ Could be yelling, singing, or bad audio
└─ Model score: 0.7+ (WRONGLY flagged as deepfake)
```

---

## Current Issue #2 Diagnosis

When user reports "deepfake does false positives even when demo audio is not playing", the diagnostic logs will show:

```
D/DEEPFAKE_DETECT: 📊 Input audio: RMS=0.78, Max=0.95, Min=-0.87, NonZeroSamples=47000/48000
I/DEEPFAKE_DETECT: Detection result: score=0.78, isDeepfake=true
```

**This tells us**:
- ✅ Audio IS reaching the detection service (RMS is high)
- ✅ Audio has NO SILENT SECTIONS (NonZeroSamples near 48000)
- ❌ But model thinks it's deepfake (score 0.78)

**Possible causes**:
1. User is speaking very loudly (RMS naturally high)
2. Audio preprocessing doesn't match training
3. Model accuracy issue with this specific voice
4. Threshold (0.7) too sensitive for production

---

## Summary: How Demo Audio Flows

```
1. UI BUTTON
   ↓
2. loadDemoWav() from assets/demo_audio/
   ↓
3. Inject into microphone stream via setSamplesReadyCallback
   ↓
4. Replace mic PCM with demo PCM (resampled to WebRTC rate)
   ↓
5. WebRTC encoder (Opus) compresses modified audio
   ↓
6. RTP transmission to remote peer
   ↓
7. Decode incoming RTP to PCM
   ↓
8. attachIncomingDetectionSink() intercepts audio
   ↓
9. Convert to 16kHz mono, feed to DeepfakeDetectionService
   ↓
10. Buffer 3 seconds (48,000 samples)
    ↓
11. Preprocess to mel spectrogram
    ↓
12. ONNX model inference → score (0.0 - 1.0)
    ↓
13. Trigger callbacks if score >= 0.7
    ↓
14. ViewModel updates UI state
    ↓
15. UI renders RED alert with 🚨
    ↓
16. Phone vibrates
    ↓
17. User sees detection! ✅
```
