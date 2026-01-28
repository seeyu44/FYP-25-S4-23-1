package com.example.fyp_25_s4_23.boundary.handlers

import android.util.Log
import com.example.fyp_25_s4_23.control.call.IncomingCallIntent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.fyp_25_s4_23.data.remote.dto.FCMTokenStore

class VOIPMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"]
        val caller = data["caller"]
        val callId = data["call_id"]

        if (type == "incoming_call" && !caller.isNullOrBlank() && !callId.isNullOrBlank()) {
            Log.i("VOIPMessaging", "Incoming call from $caller")
            startActivity(
                IncomingCallIntent.create(this, callId, caller, caller, isIncoming=true)
            )
        }
    }

    override fun onNewToken(token: String) {
        Log.i("VOIPMessaging", "New FCM Token: $token")
        FCMTokenStore(applicationContext).saveFCMToken(token)
    }
}
