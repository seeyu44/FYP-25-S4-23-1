package com.example.fyp_25_s4_23.control.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log
import com.example.fyp_25_s4_23.control.call.IncomingCallIntent

object IncomingCallNotifier {

    private const val CHANNEL_ID = "incoming_calls"

    fun showIncomingCall(
        context: Context,
        callId: String,
        callerId: String
    ) {
        Log.d("INCOMING_CALL", "Showing notification for callId=$callId caller=$callerId")
        createChannel(context)

        val intent = IncomingCallIntent.create(
            context = context,
            callId = callId,
            callerId = callerId,
            isIncoming = true
        )

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming) //icon
            .setContentTitle("Incoming VoIP Call")
            .setContentText("Caller: $callerId")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                // Permission not granted → fail silently (do NOT crash)
                return
            }
        }

        NotificationManagerCompat.from(context)
            .notify(callId.hashCode(), notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
