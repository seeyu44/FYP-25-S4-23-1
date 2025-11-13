package com.example.fyp_25_s4_23.presentation.ui.call

import android.telecom.Call
import android.telecom.VideoProfile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.presentation.call.ActiveCallStore
import com.example.fyp_25_s4_23.presentation.call.InCallServiceHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CallUiState(
    val handle: String = "",
    val stateLabel: String = "Connecting…",
    val isMuted: Boolean = false,
    val call: Call? = null
)

class CallInProgressViewModel : ViewModel() {
    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state

    init {
        viewModelScope.launch {
            ActiveCallStore.state.collectLatest { snapshot ->
                if (snapshot == null) {
                    _state.value = CallUiState()
                } else {
                    _state.value = _state.value.copy(
                        handle = snapshot.handle,
                        stateLabel = stateToLabel(snapshot.state),
                        call = snapshot.call
                    )
                }
            }
        }
    }

    fun answer() {
        _state.value.call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun hangUp() {
        _state.value.call?.disconnect()
    }

    fun toggleMute() {
        val newMuted = !_state.value.isMuted
        InCallServiceHolder.service?.setMuted(newMuted)
        _state.value = _state.value.copy(isMuted = newMuted)
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
