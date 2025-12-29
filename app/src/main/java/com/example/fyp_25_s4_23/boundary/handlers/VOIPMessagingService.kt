package com.example.fyp_25_s4_23.boundary.handlers

import android.content.Intent
import android.util.Log
import com.example.fyp_25_s4_23.boundary.call.CallInProgressActivity

/**
 * Skeleton for handling incoming VOIP calls via Firebase Cloud Messaging.
 * Placeholder for FirebaseMessagingService to be linked later.
 */
class VOIPMessagingService {

    /**
     * Called when an FCM message is received.
     */
    fun onMessageReceived(remoteMessageData: Map<String, String>) {
        val type = remoteMessageData["type"]
        val caller = remoteMessageData["caller"]
        val offer = remoteMessageData["webrtc_offer"]

        if (type == "incoming_call") {
            Log.i("VOIPMessaging", "Incoming call from $caller")
            
            // TODO: Start CallInProgressActivity and show "Incoming Call" UI
            // val intent = Intent(context, CallInProgressActivity::class.java).apply {
            //     addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            //     putExtra("IS_INCOMING", true)
            //     putExtra("CALLER_NAME", caller)
            //     putExtra("OFFER", offer)
            // }
            // context.startActivity(intent)
        }
    }

    /**
     * Called when a new FCM token is generated.
     */
    fun onNewToken(token: String) {
        Log.i("VOIPMessaging", "New FCM Token: $token")
        // TODO: Sync this token with your user profile in Firestore
    }
}
