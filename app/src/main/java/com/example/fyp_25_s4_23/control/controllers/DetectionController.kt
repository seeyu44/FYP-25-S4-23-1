package com.example.fyp_25_s4_23.control.controllers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import com.example.fyp_25_s4_23.boundary.handlers.CallMonitorService

/**
 * Orchestrates starting/stopping monitoring and delegating to ML runtime.
 */
class DetectionController(private val context: Context, private val runner: ModelRunner) {
    fun startMonitoring() {
        if (!hasRecordAudioPermission()) {
            Log.w(TAG, "startMonitoring skipped: RECORD_AUDIO not granted")
            return
        }
        runner.warmUp()
        val intent = Intent(context, CallMonitorService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopMonitoring() {
        val intent = Intent(context, CallMonitorService::class.java)
        context.stopService(intent)
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "DetectionController"
    }
}
