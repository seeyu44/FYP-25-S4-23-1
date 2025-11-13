package com.example.fyp_25_s4_23.control.controllers

import android.content.Context
import android.content.Intent
import com.example.fyp_25_s4_23.ml.ModelRunner
import com.example.fyp_25_s4_23.presentation.handlers.CallMonitorService

/**
 * Orchestrates starting/stopping monitoring and delegating to ML runtime.
 */
class DetectionController(private val context: Context, private val runner: ModelRunner) {
    fun startMonitoring() {
        val i = Intent(context, CallMonitorService::class.java)
        context.startForegroundService(i)
    }

    fun stopMonitoring() {
        val i = Intent(context, CallMonitorService::class.java)
        context.stopService(i)
    }
}

