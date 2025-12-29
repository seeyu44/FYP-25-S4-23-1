package com.example.fyp_25_s4_23.control.webrtc

import android.util.Log

/**
 * Skeleton for Firebase-based signaling. 
 * Placeholder logic to be linked with Firebase Auth, Firestore, and FCM later.
 */
class FirebaseSignalingManager {

    /**
     * Sends a call request (WebRTC Offer) to a target username.
     */
    fun sendCallRequest(targetUsername: String, offer: String) {
        Log.i("Signaling", "Sending VOIP Call Request to $targetUsername...")
        // TODO: 
        // 1. Look up targetUsername's FCM token in Firestore
        // 2. Use Cloud Functions to send a High-Priority FCM data message
        // 3. Include the WebRTC 'offer' in the message data
    }

    /**
     * Sends an answer back to the caller.
     */
    fun sendCallAnswer(callerUsername: String, answer: String) {
        Log.i("Signaling", "Sending VOIP Call Answer to $callerUsername...")
        // TODO: Similar to sendCallRequest, but sends the WebRTC 'answer'
    }

    /**
     * Registers the current user's device token.
     */
    fun registerDeviceToken(username: String, token: String) {
        Log.i("Signaling", "Registering device token for $username: $token")
        // TODO: Save to Firestore: users -> {username} -> {fcmToken: token, status: "online"}
    }
}
