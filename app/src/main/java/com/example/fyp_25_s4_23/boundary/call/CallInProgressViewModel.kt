package com.example.fyp_25_s4_23.boundary.call

import android.telecom.Call
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.control.call.ActiveCallStore
import com.example.fyp_25_s4_23.control.webrtc.WebRtcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest


private const val TAG_UI = "CALL_UI"
private const val TAG_CALL = "CALL_ENGINE"
private const val TAG_STORE = "ACTIVE_CALL_STORE"

/* =========================
   UI STATE
   ========================= */
sealed class CallUiEvent {
    data class Vibrate(val score : Float): CallUiEvent()
}

enum class CallAudioState { MUTED, SILENT, ACTIVE }
sealed class CallUiState {

    data class Connecting(val handle: String) : CallUiState()

    data class Ringing(
        val handle: String,
        val isIncoming: Boolean,
        val isReadyToAnswer: Boolean
    ) : CallUiState()


    data class Active(
        val handle: String,
        val isIncoming: Boolean,
        val isMuted: Boolean,
        val isSpeakerOn: Boolean,
        val localAudioState: CallAudioState,
        val remoteAudioActive: Boolean,
        val detectionScore: Float? = null,
        val isDeepfake: Boolean = false,
        val isDetectionActive: Boolean = false,
        val detectionThreshold: Float = 0.7f,
        val remoteConnected: Boolean = false,
        val inboundAudioLevel: Float = 0f,
        val outboundAudioLevel: Float = 0f
    ) : CallUiState()

    data class Disconnected(val handle: String, val reason: String = "Call Ended") : CallUiState()
}

/* =========================
   VIEWMODEL
   ========================= */
class CallInProgressViewModel : ViewModel() {

    private val _state =
        MutableStateFlow<CallUiState>(CallUiState.Connecting(""))
    val state: StateFlow<CallUiState> = _state

    private val _events = MutableSharedFlow<CallUiEvent>()
    val events = _events.asSharedFlow()
    private var webRtcClient: WebRtcClient? = null
    private var isIncomingCall: Boolean = false
    private var resolvedDisplayName: String = ""
    private var activeCallListenerStarted = false
    private var isInitialized = false
    
    // Callback for when user accepts incoming call or starts outgoing call
    var onStartCallRequested: (() -> Unit)? = null
    var onDeepfakeFlagged: ((Float) -> Unit)? = null

    fun setCallDirection(isIncoming: Boolean) {
        isIncomingCall = isIncoming
        Log.d(TAG_UI, "Call direction set → incoming=$isIncoming")
        
        // Initialize state based on call direction
        if (!isInitialized) {
            if (isIncoming) {
                // For incoming calls, start in Ringing state with remote user ringing
                // (We're waiting to accept)
                _state.value = CallUiState.Ringing(
                    handle = "",
                    isIncoming = true,
                    isReadyToAnswer = false  // Will become true when offer arrives
                )
            }
            isInitialized = true
        }
    }
    
    fun setDisplayName(displayName: String) {
        resolvedDisplayName = displayName
        Log.d(TAG_UI, "Display name set → $displayName")
        
        // Update current state with resolved name
        val current = _state.value
        _state.value = when (current) {
            is CallUiState.Connecting -> current.copy(handle = displayName)
            is CallUiState.Ringing -> current.copy(handle = displayName)
            is CallUiState.Active -> current.copy(handle = displayName)
            is CallUiState.Disconnected -> current.copy(handle = displayName)
        }
    }
    
    /**
     * MUST be called AFTER ActiveCallStore is populated (from Activity.onCreate)
     * This prevents race conditions where the listener fires before store is ready
     */
    fun startListeningToCallState() {
        if (activeCallListenerStarted) return
        Log.d(TAG_UI, "Starting ActiveCallStore listener")
        startActiveCallListener()
        activeCallListenerStarted = true
    }

    fun setRemoteDisplayName(displayName: String) {
        Log.d(TAG_UI, "setRemoteDisplayName=$displayName")
        val current = _state.value
        when (current) {
            is CallUiState.Ringing -> _state.value = current.copy(handle = displayName)
            is CallUiState.Connecting -> _state.value = current.copy(handle = displayName)
            is CallUiState.Active -> _state.value = current.copy(handle = displayName)
            else -> {} // Do nothing for disconnected
        }
    }

    /* =========================
       WEBRTC CLIENT ATTACHMENT
       ========================= */
    fun attachWebRtcClient(client: WebRtcClient?) {
        webRtcClient = client
        Log.d(TAG_CALL, "attachWebRtcClient(client=${client != null})")

        client?.onLocalAudioStateChanged = { audioState ->
            Log.d(TAG_CALL, "Local audio state changed → $audioState")
            val current = _state.value
            if (current is CallUiState.Active) {
                val mapped = when (audioState) {
                    WebRtcClient.AudioState.MUTED -> CallAudioState.MUTED
                    WebRtcClient.AudioState.SILENT -> CallAudioState.SILENT
                    WebRtcClient.AudioState.ACTIVE -> CallAudioState.ACTIVE
                }
                _state.value = current.copy(localAudioState = mapped)
            }
        }

        client?.onRemoteAudioStateChanged = { active ->
            Log.d(TAG_CALL, "Remote audio active → $active")
            val current = _state.value
            if (current is CallUiState.Active) {
                _state.value = current.copy(remoteAudioActive = active)
            }
        }

        client?.onDeepfakeDetected = { score, isDeepfake ->
            Log.d(TAG_CALL, "Deepfake detection → score=$score isDeepfake=$isDeepfake")
            val current = _state.value
            if (current is CallUiState.Active) {
                val alreadyAlerted = current.isDeepfake
                _state.value = current.copy(
                    detectionScore = score,
                    isDeepfake = isDeepfake,
                    isDetectionActive = true
                )
                if (isDeepfake && !alreadyAlerted) {
                    onDeepfakeFlagged?.invoke(score)
                    viewModelScope.launch {
                        _events.emit(CallUiEvent.Vibrate(score))
                    }
                }
            }
        }

        client?.onDetectionUpdate = { score ->
            Log.d(TAG_CALL, "Detection update → score=$score")
            val current = _state.value
            if (current is CallUiState.Active) {
                // ✅ CRITICAL FIX: Update isDeepfake based on CURRENT score
                // This ensures UI updates live when score drops below threshold
                val detectionThreshold = 0.7f
                val isCurrentlyDeepfake = score >= detectionThreshold
                _state.value = current.copy(
                    detectionScore = score,
                    isDeepfake = isCurrentlyDeepfake,
                    isDetectionActive = true
                )
            }
        }
    }

    /* =========================
       TELECOM BRIDGE
       ========================= */
    private fun startActiveCallListener() {
        viewModelScope.launch {
            ActiveCallStore.state.collectLatest { snapshot ->
                // If snapshot is null, only disconnect if WebRTC has also ended
                if (snapshot == null) {
                    // If WebRTC client is still active, ignore null snapshot
                    // (might be a temporary state update)
                    if (webRtcClient != null) {
                        Log.d(TAG_STORE, "Snapshot null but WebRTC active, ignoring")
                        return@collectLatest
                    }
                    
                    Log.d(TAG_STORE, "Snapshot null and no WebRTC client, disconnecting")
                    setDisconnected()
                    return@collectLatest
                }

                Log.d(TAG_STORE, "Telecom state=${snapshot.state}")

                when (snapshot.state) {
                    Call.STATE_RINGING ->
                        setRinging(resolvedDisplayName, preserveReady = true)

                    Call.STATE_ACTIVE ->
                        setActive()

                    Call.STATE_DISCONNECTED ->
                        setDisconnected()
                }
            }
        }
    }

    /* =========================
       USER ACTIONS
       ========================= */
    fun answer() {
        Log.d(TAG_UI, "Answer pressed")
        onStartCallRequested?.invoke()
    }

    fun hangUp() {
        Log.d(TAG_UI, "HangUp pressed")
        webRtcClient?.close()
        setDisconnected()
    }

    fun toggleMute() {
        val current = _state.value
        if (current !is CallUiState.Active) return

        val newMuted = !current.isMuted
        webRtcClient?.setLocalAudioEnabled(!newMuted)
        _state.value = current.copy(isMuted = newMuted)
    }

    fun toggleSpeaker() {
        val current = _state.value
        if (current !is CallUiState.Active) return

        val newSpeaker = !current.isSpeakerOn
        webRtcClient?.setSpeakerEnabled(newSpeaker)
        _state.value = current.copy(isSpeakerOn = newSpeaker)
    }

    /* =========================
       STATE TRANSITIONS
       ========================= */

    /** MUST BE PUBLIC */
    fun setRinging(handle: String, preserveReady: Boolean = false, readyToAnswer: Boolean = true) {
        Log.d(TAG_UI, "setRinging(handle=$handle incoming=$isIncomingCall)")

        val ready = if (preserveReady) {
            (_state.value as? CallUiState.Ringing)?.isReadyToAnswer == true
        } else {
            readyToAnswer
        }
        
        // Use resolved display name if available, otherwise use the provided handle
        val displayHandle = resolvedDisplayName.ifBlank { handle }

        _state.value = CallUiState.Ringing(
            handle = displayHandle,
            isIncoming = isIncomingCall,
            isReadyToAnswer = ready
        )
    }

    fun setActive() {
        if (_state.value is CallUiState.Active) return

        val handle = when (val s = _state.value) {
            is CallUiState.Ringing -> s.handle
            is CallUiState.Connecting -> s.handle
            is CallUiState.Active -> s.handle
            is CallUiState.Disconnected -> s.handle
        }
        
        // Use resolved display name if available, otherwise use the current handle
        val displayHandle = resolvedDisplayName.ifBlank { handle }

        Log.d(TAG_UI, "Transition → Active")

        _state.value = CallUiState.Active(
            handle = displayHandle,
            isIncoming = isIncomingCall,
            isMuted = false,
            isSpeakerOn = false,
            localAudioState = CallAudioState.SILENT,
            remoteAudioActive = false,
            isDetectionActive = false,
            remoteConnected = false,
            inboundAudioLevel = 0f,
            outboundAudioLevel = 0f
        )
    }

    fun startOutgoingCall() {
        onStartCallRequested?.invoke()
    }

    fun setDisconnected(reason: String = "Call Ended") {
        val handle = when (val s = _state.value) {
            is CallUiState.Ringing -> s.handle
            is CallUiState.Active -> s.handle
            is CallUiState.Connecting -> s.handle
            is CallUiState.Disconnected -> s.handle
        }
        
        // Use resolved display name if available, otherwise use the current handle
        val displayHandle = resolvedDisplayName.ifBlank { handle }

        Log.d(TAG_UI, "Transition → Disconnected (reason=$reason)")
        _state.value = CallUiState.Disconnected(displayHandle, reason)
    }

    fun setDisconnectedWithReason(reason: String?) {
        val finalReason = when (reason) {
            "blocked_contact" -> "Call Failed"
            null, "" -> "Call Ended"
            else -> reason
        }
        setDisconnected(finalReason)
    }

}
