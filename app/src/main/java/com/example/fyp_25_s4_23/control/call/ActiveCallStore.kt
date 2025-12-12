package com.example.fyp_25_s4_23.control.call

import android.telecom.Call
import android.telecom.Call.Details
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveCallStore {
    data class CallSnapshot(
        val handle: String,
        val state: Int,
        val details: Details,
        val call: Call
    )

    private val _state = MutableStateFlow<CallSnapshot?>(null)
    val state: StateFlow<CallSnapshot?> = _state.asStateFlow()

    fun update(call: Call) {
        val handle = call.details.handle?.schemeSpecificPart ?: "Unknown"
        _state.value = CallSnapshot(handle, call.state, call.details, call)
    }

    fun clear() {
        _state.value = null
    }
}
