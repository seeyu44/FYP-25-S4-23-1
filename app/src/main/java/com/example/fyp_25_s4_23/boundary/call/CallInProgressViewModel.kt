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

private const val TAG_UI = "CALL_UI"
private const val TAG_STORE = "CALL_STORE"
private const val TAG_WEBRTC = "CALL_WEBRTC"

/* =========================
   UI STATE
   ========================= */
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
        val localAudioState: WebRtcClient.AudioState,
        val remoteAudioActive: Boolean
    ) : CallUiState()

    data class Disconnected(val handle: String) : CallUiState()
}

/* =========================
   VIEWMODEL
   ========================= */
class CallInProgressViewModel : ViewModel() {

    private val _state =
        MutableStateFlow<CallUiState>(CallUiState.Connecting(""))
    val state: StateFlow<CallUiState> = _state

    private var webRtcClient: WebRtcClient? = null
    private var isIncomingCall: Boolean = false

    fun setCallDirection(isIncoming: Boolean) {
        isIncomingCall = isIncoming
        Log.d(TAG_UI, "Call direction set → incoming=$isIncoming")
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
    }

    /* =========================
       TELECOM BRIDGE
       ========================= */
    init {
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
                        setRinging(snapshot.handle, preserveReady = true)

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

    /* =========================
       STATE TRANSITIONS
       ========================= */

    /** MUST BE PUBLIC */
    fun setRinging(handle: String, preserveReady: Boolean = false) {
        Log.d(TAG_UI, "setRinging(handle=$handle incoming=$isIncomingCall)")

        val ready =
            preserveReady && (_state.value as? CallUiState.Ringing)?.isReadyToAnswer == true

        _state.value = CallUiState.Ringing(
            handle = handle,
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

        Log.d(TAG_UI, "Transition → Active")

        _state.value = CallUiState.Active(
            handle = handle,
            isMuted = false,
            localAudioState = WebRtcClient.AudioState.SILENT,
            remoteAudioActive = false
        )
    }

    fun setDisconnected() {
        val handle = when (val s = _state.value) {
            is CallUiState.Ringing -> s.handle
            is CallUiState.Active -> s.handle
            is CallUiState.Connecting -> s.handle
            is CallUiState.Disconnected -> s.handle
        }

        Log.d(TAG_UI, "Transition → Disconnected")
        _state.value = CallUiState.Disconnected(handle)
    }
}
