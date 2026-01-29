package com.example.fyp_25_s4_23.control.utils

import android.app.ActivityManager
import android.content.Context

fun getMemoryUsageGb(context: Context): Float {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)

    val usedBytes = info.totalMem - info.availMem
    return usedBytes / (1024f * 1024f * 1024f)
}
