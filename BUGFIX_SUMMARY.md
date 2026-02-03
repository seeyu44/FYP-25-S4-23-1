# Bug Fixes Summary

## Overview
This document summarizes the bug fixes implemented to address three critical issues in the deepfake detection application:

1. **False positives on silent audio**
2. **Demo audio detection behavior**
3. **Username display in call UI**

---

## Issue #1: False Positives on Silent Audio

### Problem
Silent audio frames were being flagged as deepfake with very high confidence (99.8%). The logs showed:
```
📊 Input audio: RMS=0.000003, Max=0.000031, Min=-0.000031, NonZeroSamples=450/48000
📊 Detection result: score=0.999, isDeepfake=true (threshold=0.7)
```

The issue was that the silence detection thresholds were too aggressive:
- RMS threshold: `0.001` (too low - RMS=0.000003 was passing through)
- Non-zero sample minimum: `100` (too low - only 450 out of 48000 samples needed)

### Root Cause
When audio is nearly silent (e.g., during call initialization, network pauses, or quiet moments), the mel spectrogram appears "synthetic" to the ML model, triggering false positives.

### Solution
**File:** [DeepfakeDetectionService.kt](app/src/main/java/com/example/fyp_25_s4_23/control/detection/DeepfakeDetectionService.kt#L227-L234)

Increased silence detection thresholds:
```kotlin
// BEFORE
val rmsThreshold = 0.001f
val minNonZeroSamples = 100

// AFTER  
val rmsThreshold = 0.01f  // Increased from 0.001 to catch more silent frames
val minNonZeroSamples = 1000  // Increased from 100 to require more active samples
```

**Impact:**
- ✅ Silent frames with RMS < 0.01 are now properly skipped
- ✅ Requires at least 1000 non-zero samples (out of 48000) to proceed with inference
- ✅ Eliminates false positives on silence

---

## Issue #2: Demo Audio Detection Behavior

### Problem
When playing demo audio for demonstration purposes:
1. The sender's device would detect its own demo audio as deepfake (due to echo/feedback)
2. Detection should be paused during demo playback
3. Detection should automatically resume when demo is stopped

### Root Cause
The `isDemoMode` flag in `WebRTCClient` was being set but never used to control detection. The detection service was analyzing all audio regardless of demo mode.

### Solution

#### Part 1: Add Pause/Resume Controls to Detection Service
**File:** [DeepfakeDetectionService.kt](app/src/main/java/com/example/fyp_25_s4_23/control/detection/DeepfakeDetectionService.kt)

Added pause functionality:
```kotlin
private var isPaused = false  // NEW: Pause detection during demo audio playback

/**
 * Pause detection (e.g., during demo audio playback)
 */
fun pauseDetection() {
    isPaused = true
    Log.d(TAG, "⏸️ Detection paused (e.g., for demo audio playback)")
}

/**
 * Resume detection after pausing
 */
fun resumeDetection() {
    isPaused = false
    Log.d(TAG, "▶️ Detection resumed")
}

// In runInference():
if (isPaused) {
    Log.d("DEEPFAKE_DETECT", "⏸️ Detection paused - skipping inference")
    return
}
```

#### Part 2: Wire Pause/Resume to Demo Audio Playback
**File:** [WebRTCClient.kt](app/src/main/java/com/example/fyp_25_s4_23/control/webrtc/WebRTCClient.kt#L903-L970)

Modified `playDemoAudio()` function:
```kotlin
// When demo starts:
isDemoMode = true
detectionService?.pauseDetection()
Log.i("DEMO_AUDIO", "✅ Demo audio playing - detection paused to avoid false positives")

// When demo stops:
isDemoMode = false
detectionService?.resumeDetection()
Log.i("DEMO_AUDIO", "✅ Demo audio stopped - detection resumed")
```

**Impact:**
- ✅ Detection is automatically paused when demo audio starts
- ✅ Detection is automatically resumed when demo audio stops  
- ✅ No false positives from demo audio echo/feedback
- ✅ Proper state management for demo mode

---

## Issue #3: Username Display in Call UI

### Problem
The call UI was displaying user IDs or emails instead of user-friendly display names:
- For incoming calls: Showed caller's userId instead of display name
- For outgoing calls: Showed callee's userId instead of display name

### Root Cause
The `CallInProgressActivity` was using `remoteUserId` directly as the display name instead of fetching the actual user profile from Firestore.

### Solution
**File:** [CallInProgressActivity.kt](app/src/main/java/com/example/fyp_25_s4_23/boundary/call/CallInProgressActivity.kt)

#### Part 1: Import UserProfileRepository
```kotlin
import com.example.fyp_25_s4_23.data.remote.firebase.UserProfileRepository
import kotlinx.coroutines.launch
```

#### Part 2: Fetch Display Name from Firestore
```kotlin
// Initialize display name with userId as fallback
displayName =
    if (isIncoming) {
        // Callee sees caller name
        intent.getStringExtra(IncomingCallIntent.EXTRA_DISPLAY_NAME)
            ?: remoteUserId
    } else {
        // Caller sees callee name - use userId as temporary fallback
        remoteUserId
    }

// Fetch remote user's display name from Firestore
lifecycleScope.launch {
    try {
        val userProfileRepo = UserProfileRepository()
        val remoteProfile = userProfileRepo.getUserProfile(remoteUserId)
        displayName = remoteProfile.displayName.takeIf { it.isNotBlank() } ?: remoteProfile.username
        viewModel.setRemoteDisplayName(displayName)
        Log.d(TAG_SIG, "✅ Fetched display name for remote user: $displayName")
    } catch (e: Exception) {
        Log.e(TAG_SIG, "Failed to fetch remote user profile, using fallback: $displayName", e)
    }
}
```

**Impact:**
- ✅ Displays user-friendly display names in call UI
- ✅ Fetches display name from Firestore user profile
- ✅ Falls back to username if display name is blank
- ✅ Falls back to userId if Firestore fetch fails
- ✅ Works for both incoming and outgoing calls

---

## Testing Recommendations

### Test Case 1: Silent Audio Detection
1. Start a call
2. Mute microphone or remain silent
3. **Expected:** No deepfake detections triggered
4. **Verify:** Logs show "⏭️ Skipping silent frame" messages

### Test Case 2: Demo Audio Behavior
1. Start a call
2. Press "Play Demo Audio" button
3. **Expected:** 
   - Demo audio plays
   - Logs show "⏸️ Detection paused"
   - No deepfake detections on sender side
4. Press "Stop Demo Audio" button
5. **Expected:**
   - Demo audio stops
   - Logs show "▶️ Detection resumed"
   - Normal detection resumes

### Test Case 3: Username Display
1. Make an outgoing call to another user
2. **Expected:** See the callee's display name (not userId)
3. Receive an incoming call
4. **Expected:** See the caller's display name (not userId)
5. **Verify:** Logs show "✅ Fetched display name for remote user: [name]"

---

## Files Modified

### 1. DeepfakeDetectionService.kt
- Increased RMS threshold from 0.001 to 0.01
- Increased min non-zero samples from 100 to 1000
- Added `isPaused` flag
- Added `pauseDetection()` and `resumeDetection()` functions
- Modified `runInference()` to check pause state

### 2. WebRTCClient.kt
- Modified `playDemoAudio()` to call `pauseDetection()` when demo starts
- Modified `playDemoAudio()` to call `resumeDetection()` when demo stops
- Improved logging for demo audio state changes

### 3. CallInProgressActivity.kt
- Added import for `UserProfileRepository`
- Added coroutine to fetch remote user profile from Firestore
- Modified display name initialization to use profile data
- Added fallback logic: displayName → username → userId

---

## Summary

All three critical bugs have been resolved:

1. ✅ **Silent audio false positives:** Fixed by increasing detection thresholds
2. ✅ **Demo audio behavior:** Fixed by adding pause/resume functionality
3. ✅ **Username display:** Fixed by fetching user profiles from Firestore

The changes are backward-compatible, include proper error handling, and follow the existing code patterns in the project.
