package com.example.fyp_25_s4_23.boundary.call

import android.telecom.Call
import android.telecom.VideoProfile
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.control.call.ActiveCallStore
import com.example.fyp_25_s4_23.control.webrtc.WebRtcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow


private const val TAG_UI = "CALL_UI"
private const val TAG_STORE = "CALL_STORE"
private const val TAG_WEBRTC = "CALL_WEBRTC"

/* =========================
   UI STATE
   ========================= */
sealed class CallUiEvent {
    data class Vibrate(val score : Float): CallUiEvent()
}
sealed class CallUiState {

    data class Connecting(val handle: String) : CallUiState()

    data class Ringing(
        val handle: String,
        val isIncoming: Boolean,
        val isReadyToAnswer: Boolean
    ) : CallUiState()


    data class Active(
        val handle: String,
        val isMuted: Boolean,
        val isSpeakerOn: Boolean,
        val localAudioState: WebRtcClient.AudioState,
        val remoteAudioActive: Boolean,
        val detectionScore: Float? = null,
        val isDeepfake: Boolean = false,
        val isDetectionActive: Boolean = false
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

    fun setCallDirection(isIncoming: Boolean) {
        isIncomingCall = isIncoming
        Log.d(TAG_UI, "Call direction set → incoming=$isIncoming")
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
        
        // Start listening to ActiveCallStore after display name is set
        if (!activeCallListenerStarted) {
            startActiveCallListener()
            activeCallListenerStarted = true
        }
    }

    /* =========================
       WEBRTC ATTACH
       ========================= */
    fun attachWebRtcClient(client: WebRtcClient?) {
        webRtcClient = client
        Log.d(TAG_WEBRTC, "attachWebRtcClient(client=${client != null})")

        client?.setOnReadyToAnswerListener { ready ->
            val current = _state.value
            if (current is CallUiState.Ringing) {
                Log.d(TAG_WEBRTC, "ReadyToAnswer → $ready")
                _state.value = current.copy(isReadyToAnswer = ready)
            }
        }

        client?.setOnAnsweredListener {
            Log.d(TAG_WEBRTC, "onAnswered → setActive()")
            setActive()
        }

        client?.onLocalAudioStateChanged = { audio ->
            val current = _state.value
            if (current is CallUiState.Active) {
                _state.value = current.copy(localAudioState = audio)
            }
        }

        client?.onRemoteAudioStateChanged = { active ->
            val current = _state.value
            if (current is CallUiState.Active) {
                _state.value = current.copy(remoteAudioActive = active)
            }
        }

        client?.onSpeakerStateChanged = { enabled ->
            val current = _state.value
            if (current is CallUiState.Active) {
                _state.value = current.copy(isSpeakerOn = enabled)
            }
        }
        
        // Deepfake detection callbacks
        client?.onDeepfakeDetected = { score, isDeepfake ->
            val current = _state.value
            if (current is CallUiState.Active) {
                Log.w(TAG_WEBRTC, "🚨 DEEPFAKE DETECTED: score=$score")
                val alreadyAlerted = current.isDeepfake

                _state.value = current.copy(
                    detectionScore = score,
                    isDeepfake = isDeepfake,
                    isDetectionActive = true
                )

                if (isDeepfake && !alreadyAlerted) {
                    viewModelScope.launch {
                        _events.emit(CallUiEvent.Vibrate(score))
                    }
                }
            }
        }
        
        client?.onDetectionUpdate = { score ->
            val current = _state.value
            if (current is CallUiState.Active) {
                _state.value = current.copy(
                    detectionScore = score,
                    isDeepfake = score >= 0.7f,
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
                if (snapshot == null) {
                    if (webRtcClient != null) return@collectLatest
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

        ActiveCallStore.state.value?.call
            ?.answer(VideoProfile.STATE_AUDIO_ONLY)
            ?.also { return }

        if (webRtcClient?.answerIncomingCall() == true) {
            setActive()
        }
    }

    fun hangUp() {
        Log.d(TAG_UI, "HangUp pressed")
        ActiveCallStore.state.value?.call?.disconnect()
        webRtcClient?.requestHangUp()
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
    fun setRinging(handle: String, preserveReady: Boolean = false) {
        Log.d(TAG_UI, "setRinging(handle=$handle incoming=$isIncomingCall)")

        val ready =
            preserveReady && (_state.value as? CallUiState.Ringing)?.isReadyToAnswer == true
        
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
            isMuted = false,
            isSpeakerOn = false,
            localAudioState = WebRtcClient.AudioState.SILENT,
            remoteAudioActive = false
        )
        
        // Start deepfake detection when call becomes active
        webRtcClient?.startDeepfakeDetection()
        Log.d(TAG_UI, "Deepfake detection started")
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
