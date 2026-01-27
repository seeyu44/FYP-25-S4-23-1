package com.example.fyp_25_s4_23.control.call

import android.telecom.Call
import android.telecom.Call.Details
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveCallStore {

    data class CallSnapshot(
        val callId: String?,
        val handle: String,
        val state: Int,
        val details: Details?,
        val call: Call?
    )

    private val _state = MutableStateFlow<CallSnapshot?>(null)
    val state: StateFlow<CallSnapshot?> = _state.asStateFlow()

    /** Telecom calls */
    fun update(call: Call) {
        val handle = call.details.handle?.schemeSpecificPart ?: "Unknown"
        _state.value = CallSnapshot(
            callId = null,
            handle = handle,
            state = call.state,
            details = call.details,
            call = call
        )
    }

    /** WebRTC calls */
    fun setWebRtcActive(callId: String, remoteUserId: String, state: Int = Call.STATE_CONNECTING) {
        _state.value = CallSnapshot(
            callId = callId,
            handle = remoteUserId,
            state = state,
            details = null,
            call = null
        )
    }

    fun clear() {
        _state.value = null
    }
}
