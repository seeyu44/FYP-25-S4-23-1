package com.example.fyp_25_s4_23.boundary.handlers

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.fyp_25_s4_23.control.call.InCallServiceHolder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class InCallAlertHandler(private val context: Context) : AlertHandler {
    private val TAG = "InCallAlertHandler"

    override fun displayCriticalAlert(probability: Float) {
        val message = "🚨 Deepfake threat detection: ${(probability * 100).toInt()}%"

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) 이상
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 1000)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }

        InCallServiceHolder.service?.let {
            Log.i(TAG, "Requesting InCallService to show alert: $message")
        } ?: run {
            Log.w(TAG, "InCallService not active or accessible.")
        }
    }
}