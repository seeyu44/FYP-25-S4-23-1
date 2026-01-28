package com.example.fyp_25_s4_23.boundary.handlers

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.fyp_25_s4_23.ml.AudioFeatureExtractor
import com.example.fyp_25_s4_23.ml.ModelRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.fyp_25_s4_23.control.AlertHandlerHolder
import com.example.fyp_25_s4_23.util.VibratorUtil

class CallMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var audioRecord: AudioRecord? = null
    private var currentJob: kotlinx.coroutines.Job? = null
    private val modelRunner by lazy { ModelRunner(this) }
    private var lastAlertTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        startCapture()
        return START_STICKY
    }

    override fun onDestroy() {
        stopCapture()
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

    private fun startCapture() {
        if (audioRecord != null) return
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            stopSelf()
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord?.startRecording()

        currentJob = serviceScope.launch {
            val shortBuffer = ShortArray(FRAME_SIZE)
            while (isActive && audioRecord != null) {
                val read = audioRecord?.read(shortBuffer, 0, FRAME_SIZE) ?: 0
                if (read > 0) {
                    val frame = shortBuffer.copyOf(read)
                    val energy = AudioFeatureExtractor.energy(frame)
                    val zcr = AudioFeatureExtractor.zeroCrossRate(frame)

                    val probability = modelRunner.infer(floatArrayOf(energy, zcr))

                    if (probability >= ALERT_THRESHOLD && System.currentTimeMillis() - lastAlertTime > ALERT_COOLDOWN) {
                        lastAlertTime = System.currentTimeMillis()

                        Log.d("CallMonitorService", "REAL-TIME Deepfake Detected! Prob: $probability")

                        VibratorUtil.vibrate(this@CallMonitorService)

                        launch(Dispatchers.Main) {
                            AlertHandlerHolder.handler?.displayCriticalAlert(probability)
                        }
                    }
                }
            }
        }
    }

    private fun stopCapture() {
        currentJob?.cancel()
        currentJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val MONITOR_CHANNEL_ID = "call_monitor_channel"
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_SIZE = 1024
        private const val ALERT_THRESHOLD = 0.7f
        private const val ALERT_COOLDOWN = 10_000L
    }
}
