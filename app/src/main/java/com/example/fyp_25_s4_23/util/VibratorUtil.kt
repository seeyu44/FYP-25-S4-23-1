package com.example.fyp_25_s4_23.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object VibratorUtil {
    private const val TAG = "VibratorUtil"

    private val WARNING_PATTERN = longArrayOf(0, 500, 500, 500)

    fun vibrate(context: Context, pattern: LongArray = WARNING_PATTERN) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Vibration permission denied. Add android.permission.VIBRATE to Manifest.", e)
            }
        }
    }
}