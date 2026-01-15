package com.example.fyp_25_s4_23.boundary.call

import android.telecom.Call
import android.telecom.VideoProfile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.boundary.call.ActiveCallStore
import com.example.fyp_25_s4_23.boundary.call.InCallServiceHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.fyp_25_s4_23.control.webrtc.WebRtcClient

data class CallUiState(
    val handle: String = "",
    val stateLabel: String = "Connecting",
    val isMuted: Boolean = false,
    val call: Call? = null,
    val isReadyToAnswer: Boolean = false,
    val localAudioState: com.example.fyp_25_s4_23.control.webrtc.WebRtcClient.AudioState = com.example.fyp_25_s4_23.control.webrtc.WebRtcClient.AudioState.SILENT,
    val remoteAudioActive: Boolean = false
)

class CallInProgressViewModel : ViewModel() {
    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state

    // Optional WebRTC client used for signaling-based calls (non-Telecom)
    private var webRtcClient: WebRtcClient? = null

    fun attachWebRtcClient(client: WebRtcClient?) {
        webRtcClient = client

        client?.setOnReadyToAnswerListener { ready ->
            _state.value = _state.value.copy(isReadyToAnswer = ready)
            android.util.Log.d("CALL_SIG", "ReadyToAnswer: $ready")
        }

        client?.setOnAnsweredListener {
            setActive()
        }

        // Subscribe to audio indicator callbacks
        client?.onLocalAudioStateChanged = { state ->
            _state.value = _state.value.copy(localAudioState = state)
            android.util.Log.d("CALL_SIG", "LocalAudioState: $state")
        }

        client?.onRemoteAudioStateChanged = { active ->
            _state.value = _state.value.copy(remoteAudioActive = active)
            android.util.Log.d("CALL_SIG", "RemoteAudioActive: $active")
        }
    }

    init {
        viewModelScope.launch {
            ActiveCallStore.state.collectLatest { snapshot ->
                if (snapshot == null) {
                    // Keep any existing signaling state (e.g., ringing) if present
                    if (_state.value.stateLabel == "Ringing") return@collectLatest
                    _state.value = CallUiState()
                } else {
                    _state.value = _state.value.copy(
                        handle = snapshot.handle,
                        stateLabel = stateToLabel(snapshot.state),
                        call = snapshot.call
                    )
                    // For Telecom calls (no WebRTC client), update localAudioState based on mute state
                    if (webRtcClient == null) {
                        val audioState = if (_state.value.isMuted) {
                            com.example.fyp_25_s4_23.control.webrtc.WebRtcClient.AudioState.MUTED
                        } else {
                            com.example.fyp_25_s4_23.control.webrtc.WebRtcClient.AudioState.SILENT // Assume silent when unmuted
                        }
                        _state.value = _state.value.copy(localAudioState = audioState)
                    }
                }
            }
        }
    }

    fun answer() {
        android.util.Log.d("CALL_UI","Answer requested (state=${_state.value.stateLabel})")
        // If there's a telecom Call, use platform API
        _state.value.call?.answer(VideoProfile.STATE_AUDIO_ONLY)

        // If this is a signaling/WebRTC call, instruct WebRtcClient to answer
        if (_state.value.call == null) {
            val answeredNow = webRtcClient?.answerIncomingCall() ?: false
            if (answeredNow) {
                _state.value = _state.value.copy(stateLabel = "Active")
            } else {
                android.util.Log.d("CALL_UI", "Answer queued; waiting for remote offer to be applied")
                // keep Ringing; UI will enable when remote offer applied
            }
        }
    }

    fun hangUp() {
        // Hangup platform call if present
        _state.value.call?.disconnect()

        // End WebRTC call if attached
        webRtcClient?.endCall()
        _state.value = _state.value.copy(stateLabel = "Disconnected")
    }

    fun toggleMute() {
        val newMuted = !_state.value.isMuted
        android.util.Log.d("CALL_UI", "toggleMute -> newMuted=$newMuted")

        // If there's a platform Telecom Call, use platform mute
        if (_state.value.call != null) {
            InCallServiceHolder.service?.setMuted(newMuted)
            android.util.Log.d("CALL_UI", "Telecom mute set to $newMuted")
        } else {
            // Signaling/WebRTC call: toggle local audio track
            webRtcClient?.setLocalAudioEnabled(!newMuted)
            android.util.Log.d("CALL_UI", "WebRTC local audio set to ${!newMuted}")
        }

        _state.value = _state.value.copy(isMuted = newMuted)

        // Update localAudioState for Telecom calls
        if (_state.value.call != null) {
            val audioState = if (newMuted) {
                com.example.fyp_25_s4_23.control.webrtc.WebRtcClient.AudioState.MUTED
            } else {
                com.example.fyp_25_s4_23.control.webrtc.WebRtcClient.AudioState.SILENT
            }
            _state.value = _state.value.copy(localAudioState = audioState)
        }
    }

    fun setRinging(handle: String) {
        // When ringing begins, assume not ready until a remote offer is applied
        _state.value = _state.value.copy(handle = handle, stateLabel = "Ringing", isReadyToAnswer = false)
    }

    fun setActive() {
        _state.value = _state.value.copy(stateLabel = "Active")
    }

    fun setDisconnected() {
        _state.value = _state.value.copy(stateLabel = "Disconnected")
    }

    private fun stateToLabel(state: Int): String = when (state) {
        Call.STATE_ACTIVE -> "Active"
        Call.STATE_DIALING -> "Dialing"
        Call.STATE_RINGING -> "Ringing"
        Call.STATE_CONNECTING -> "Connecting"
        Call.STATE_DISCONNECTING -> "Hanging up"
        Call.STATE_DISCONNECTED -> "Disconnected"
        else -> "Idle"
    }
}
