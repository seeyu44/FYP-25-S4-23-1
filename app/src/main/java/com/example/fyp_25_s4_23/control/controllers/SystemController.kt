package com.example.fyp_25_s4_23.control.controllers

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.fyp_25_s4_23.control.usecases.GetSystemUptime
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

// Data class to hold memory information
data class MemoryInfo(
    val usedMemory: Long,
    val totalMemory: Long,
    val usagePercentage: Float
)

class SystemController(private val context: Context) {
    private val getUptime = GetSystemUptime()

    fun fetchUptime(): String = getUptime()

    /**
     * Fetches the current memory usage of the device.
     */
    fun getMemoryInfo(): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMemory = memoryInfo.totalMem
        val usedMemory = totalMemory - memoryInfo.availMem
        val usagePercentage = if (totalMemory > 0) usedMemory.toFloat() / totalMemory.toFloat() else 0f

        return MemoryInfo(usedMemory, totalMemory, usagePercentage)
    }

    /**
     * Checks if the device supports hardware-accelerated ML inference via NNAPI.
     */
    fun hasHardwareAcceleration(): Boolean {
        return if (Build.VERSION.SDK_INT >= 27) {
            context.packageManager.hasSystemFeature("android.hardware.neuralnetworks")
        } else {
            false
        }
    }

    /**
     * Measures the latency to Firebase Auth service in milliseconds.
     * @return latency in ms, or -1 if the operation failed.
     */
    suspend fun getFirebaseLatency(): Long {
        val startTime = System.currentTimeMillis()
        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser != null) {
                auth.currentUser!!.getIdToken(false).await()
            } else {
                // If not logged in, just check app accessibility
                auth.app.name 
            }
            System.currentTimeMillis() - startTime
        } catch (e: Exception) {
            Log.e("FirebaseLatency", "Ping to Auth failed: ${e.message}")
            -1L
        }
    }
}
