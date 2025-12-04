package com.example.fyp_25_s4_23.boundary.handlers

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.fyp_25_s4_23.control.call.ActiveCallStore
import com.example.fyp_25_s4_23.control.call.InCallServiceHolder
import com.example.fyp_25_s4_23.boundary.call.CallInProgressActivity

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
        InCallServiceHolder.service = this
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
        if (call == ActiveCallStore.state.value?.call) {
            InCallServiceHolder.service = null
        }
        super.onCallRemoved(call)
    }

    override fun onDestroy() {
        InCallServiceHolder.service = null
        super.onDestroy()
    }

    private fun startMonitoring() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return
        }
        startForegroundService(Intent(this, CallMonitorService::class.java))
    }

    private fun stopMonitoring() {
        stopService(Intent(this, CallMonitorService::class.java))
    }
}
