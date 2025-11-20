package com.example.fyp_25_s4_23.control.usecases

import android.os.SystemClock

class GetSystemUptime {

    operator fun invoke(): String {
        val uptime = SystemClock.uptimeMillis()

        val seconds = (uptime / 1000) % 60
        val minutes = (uptime / (1000 * 60)) % 60
        val hours = (uptime / (1000 * 60 * 60))

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
