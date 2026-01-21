package com.example.fyp_25_s4_23.boundary.call

import android.telecom.Call
import android.telecom.VideoProfile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.control.call.ActiveCallStore
import com.example.fyp_25_s4_23.control.webrtc.WebRtcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/* =========================
   LOG TAGS
   ========================= */
private const val TAG_UI = "CALL_UI"
private const val TAG_SIG = "CALL_SIG"
private const val TAG_STORE = "CALL_STORE"

/* =========================
   SEALED UI STATE
   ========================= */
sealed class CallUiState {

    data class Connecting(
        val handle: String
    ) : CallUiState()

    data class Ringing(
        val handle: String,
        val isReadyToAnswer: Boolean
    ) : CallUiState()

    data class Active(
        val handle: String,
        val isMuted: Boolean,
        val localAudioState: WebRtcClient.AudioState,
        val remoteAudioActive: Boolean
    ) : CallUiState()

    data class Disconnected(
        val handle: String
    ) : CallUiState()
}

/* =========================
   VIEW MODEL
   ========================= */
class CallInProgressViewModel : ViewModel() {

    private val _state =
        MutableStateFlow<CallUiState>(CallUiState.Connecting(handle = ""))
    val state: StateFlow<CallUiState> = _state

    private var webRtcClient: WebRtcClient? = null
    var onCallEnded: (() -> Unit)? = null

    /* =========================
       WEBRTC ATTACH
       ========================= */
    fun attachWebRtcClient(client: WebRtcClient?) {
        webRtcClient = client
        android.util.Log.d(TAG_SIG, "attachWebRtcClient(client=${client != null})")

        client?.setOnReadyToAnswerListener { ready ->
            android.util.Log.d(TAG_SIG, "onReadyToAnswer → $ready")

            val current = _state.value
            if (current is CallUiState.Ringing) {
                _state.value = current.copy(isReadyToAnswer = ready)
            }
        }

        client?.setOnAnsweredListener {
            android.util.Log.d(TAG_SIG, "onAnswered → setActive()")
            setActive()
        }

        client?.onLocalAudioStateChanged = { audio ->
            android.util.Log.d(TAG_SIG, "LocalAudioState → $audio")
            val current = _state.value
            if (current is CallUiState.Active) {
                _state.value = current.copy(localAudioState = audio)
            }
        }

        client?.onRemoteAudioStateChanged = { active ->
            android.util.Log.d(TAG_SIG, "RemoteAudioActive → $active")
            val current = _state.value
            if (current is CallUiState.Active) {
                _state.value = current.copy(remoteAudioActive = active)
            }
        }
    }

    /* =========================
       ACTIVE CALL STORE BRIDGE
       ========================= */
    init {
        viewModelScope.launch {
            ActiveCallStore.state.collectLatest { snapshot ->
                if (snapshot == null) {
                    android.util.Log.d(TAG_STORE, "ActiveCallStore cleared")


                    if (webRtcClient != null) {
                        android.util.Log.d(TAG_STORE, "Ignoring store clear (WebRTC active)")
                        return@collectLatest
                    }

                    setDisconnected()
                    return@collectLatest
                }

                android.util.Log.d(
                    TAG_STORE,
                    "Store update → state=${snapshot.state} handle=${snapshot.handle}"
                )

                when (snapshot.state) {
                    Call.STATE_RINGING ->
                        setRinging(snapshot.handle)

                    Call.STATE_ACTIVE ->
                        setActive()

                    Call.STATE_DISCONNECTED ->
                        setDisconnected()

                    else -> Unit
                }
            }
        }
    }

    /* =========================
       USER ACTIONS
       ========================= */
    fun answer() {
        android.util.Log.d(TAG_UI, "Answer pressed")

        // Telecom call
        ActiveCallStore.state.value?.call
            ?.answer(VideoProfile.STATE_AUDIO_ONLY)
            ?.also { return }

        // WebRTC call
        val answeredNow = webRtcClient?.answerIncomingCall() ?: false
        android.util.Log.d(TAG_UI, "answerIncomingCall → $answeredNow")

        if (answeredNow) {
            setActive()
        }
    }

    fun hangUp() {
        android.util.Log.d(TAG_UI, "HangUp pressed")

        ActiveCallStore.state.value?.call?.disconnect()
        webRtcClient?.requestHangUp()

        setDisconnected()
    }

    fun toggleMute() {
        val current = _state.value
        if (current !is CallUiState.Active) return

        val newMuted = !current.isMuted
        android.util.Log.d(TAG_UI, "toggleMute → $newMuted")

        webRtcClient?.setLocalAudioEnabled(!newMuted)
        _state.value = current.copy(isMuted = newMuted)
    }

    /* =========================
       STATE TRANSITIONS
       ========================= */
    fun setRinging(handle: String, preserveReady: Boolean = false) {
        android.util.Log.d(TAG_UI, "setRinging(handle=$handle)")

        if (_state.value is CallUiState.Active) {
            android.util.Log.w(TAG_UI, "Ignoring Ringing → already Active")
            return
        }

        val ready =
            preserveReady && (_state.value as? CallUiState.Ringing)?.isReadyToAnswer == true

        _state.value = CallUiState.Ringing(
            handle = handle,
            isReadyToAnswer = ready || (webRtcClient != null)
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

        android.util.Log.d(TAG_UI, "Transition → Active")

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
            is CallUiState.Connecting -> s.handle
            is CallUiState.Active -> s.handle
            is CallUiState.Disconnected -> s.handle
        }

        android.util.Log.d(TAG_UI, "Transition → Disconnected")

        _state.value = CallUiState.Disconnected(handle)
        onCallEnded?.invoke()
    }

    fun setEndedfromEngine() {
        android.util.Log.d(TAG_UI, "Engine ended call")
        setDisconnected()
    }
}
