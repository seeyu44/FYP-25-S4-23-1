package com.example.fyp_25_s4_23.presentation.handlers

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.example.fyp_25_s4_23.presentation.call.ActiveCallStore
import com.example.fyp_25_s4_23.presentation.ui.call.CallInProgressActivity

class AntiDeepfakeInCallService : InCallService() {
    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            ActiveCallStore.update(call)
            if (state == Call.STATE_DISCONNECTED) {
                stopMonitoring()
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        call.registerCallback(callback)
        ActiveCallStore.update(call)
        startMonitoring()
        val intent = Intent(this, CallInProgressActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        ActiveCallStore.clear()
        stopMonitoring()
        super.onCallRemoved(call)
    }

    private fun startMonitoring() {
        startForegroundService(Intent(this, CallMonitorService::class.java))
    }

    private fun stopMonitoring() {
        stopService(Intent(this, CallMonitorService::class.java))
    }
}

