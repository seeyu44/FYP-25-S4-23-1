package com.example.fyp_25_s4_23.presentation.handlers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Foreground service placeholder for monitoring audio during calls.
 * Does not capture audio yet; scaffolding only.
 */
class CallMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitoringJobStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        beginDetectionLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        monitoringJobStarted = false
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channelId = MONITOR_CHANNEL_ID
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Call Monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Monitoring active")
            .setContentText("Deepfake detection running")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()
    }

    private fun beginDetectionLoop() {
        if (monitoringJobStarted) return
        monitoringJobStarted = true
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        serviceScope.launch {
            var alertId = ALERT_NOTIF_ID
            while (isActive) {
                delay(15_000)
                val probability = Random.nextFloat()
                if (probability > 0.8f) {
                    val notification = NotificationCompat.Builder(this@CallMonitorService, MONITOR_CHANNEL_ID)
                        .setContentTitle("Possible deepfake detected")
                        .setContentText("Confidence ${(probability * 100).toInt()}%")
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build()
                    manager.notify(alertId++, notification)
                }
            }
        }
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val ALERT_NOTIF_ID = 2001
        private const val MONITOR_CHANNEL_ID = "call_monitor_channel"
    }
}
