package com.example.fyp_25_s4_23.control.webrtc

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * Skeleton for Firebase-based signaling. 
 * Placeholder logic to be linked with Firebase Auth, Firestore, and FCM later.
 */
class FirebaseSignalingManager {

    private val db:DatabaseReference = FirebaseDatabase.getInstance().reference


    // Sends a call request (WebRTC Offer) to a target username.
    fun createCall(callId: String, callerId: String, calleeId: String, offerSdp: String){
        Log.i("Signaling", "Sending VOIP Call Request to $calleeId...")
        // TODO:
        // 1. Look up targetUsername's FCM token in Firestore
        // 2. Use Cloud Functions to send a High-Priority FCM data message
        // 3. Trigger FCM notif via backend (no SDP in FCM)
        val callData = mapOf(
                "callerId" to callerId,
                "calleeId" to calleeId,
                "offer" to offerSdp,
                "status" to "ringing"
        )
        db.child("calls").child(callId).setValue(callData)
    }

    // Sends an answer back to the caller.

    fun sendAnswer(callId: String, answerSdp: String) {
        Log.i("Signaling", "Sending VOIP Call Answer to $callId...")
        // TODO: Similar to sendCallRequest, but sends the WebRTC 'answer'
        db.child("calls")
            .child(callId)
            .child("answer")
            .setValue(answerSdp)
    }

    fun sendIceCandidate(callId: String, userId: String, candidate: Map<String, Any>){
        db.child("calls")
            .child(callId)
            .child("iceCandidates")
            .child(userId)
            .push()
            .setValue(candidate)
    }



    // Registers the current user's device token.

    fun registerDeviceToken(userId: String, token: String) {
        Log.i("Signaling", "Registering device token for $userId: $token")
        // TODO: Save to Firestore: users -> {username} -> {fcmToken: token, status: "online"}
    }
}
