package com.example.fyp_25_s4_23.boundary.handlers

import android.content.Intent
import android.util.Log
import com.example.fyp_25_s4_23.boundary.call.CallInProgressActivity
import com.example.fyp_25_s4_23.control.call.IncomingCallIntent

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.fyp_25_s4_23.data.remote.dto.FCMTokenStore


/**
 * Skeleton for handling incoming VOIP calls via Firebase Cloud Messaging.
 * Placeholder for FirebaseMessagingService to be linked later.
 */
class VOIPMessagingService : FirebaseMessagingService(){

    /**
     * Called when an FCM message is received.
     */
    override fun onMessageReceived(remoteMessageData: RemoteMessage) {
        val data = remoteMessageData.data
        val type = data["type"]
        val caller = data["caller"]
        val callId = data["call_id"]

        if (type == "incoming_call") {
            Log.i("VOIPMessaging", "Incoming call from $caller")

            if (callId.isNullOrBlank() || caller.isNullOrBlank()) {
                Log.w("VOIPMessaging", "Missing callId or caller in incoming_call payload")
                return
            }

            val intent = IncomingCallIntent.create(
                this,
                callId,
                caller,
                true
            )
            startActivity(intent)
        }
    }

    /**
     * Called when a new FCM token is generated.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i("VOIPMessaging", "New FCM Token: $token")
        // Send token -> FastAPI Backend -> Firebase Admin

        FCMTokenStore(applicationContext).saveFCMToken(token)
    }
}
