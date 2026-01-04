package com.example.fyp_25_s4_23.control.webrtc

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Skeleton for Firebase-based signaling. 
 * Placeholder logic to be linked with Firebase Auth, Firestore, and FCM later.
 */
class FirebaseSignalingManager {

//Removed createCall + registerDevice, these functions are handled by FastApi

    private val firestore = FirebaseFirestore.getInstance()
    private var callListener: ListenerRegistration? = null

    //Listen to call state changes (ringing/accepted/ended)
    fun listenToCall(callId: String, onAccepted:(offerSdp:String)->Unit, onEnded:()-> Unit){
        callListener = firestore.collection("calls")
            .document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    Log.e("Signaling", "Call listener error", error)
                    return@addSnapshotListener
                }
                val status = snapshot.getString("status")

                when (status) {
                    "in_call","accepted" -> {
                        val offer = snapshot.getString("offer_sdp")
                        if (offer != null) {
                            Log.i("Signaling", "Call accepted")
                            onAccepted(offer)
                        }
                    }
                    "ended" -> {
                        Log.i("Signaling", "Call ended")
                        onEnded()
                    }
                }
            }
    }


    // Sends an answer back to the caller.

    fun sendAnswer(callId: String, answerSdp: String) {
        Log.i("Signaling", "Sending VOIP Call Answer to $callId...")
        // TODO: Similar to sendCallRequest, but sends the WebRTC 'answer'
        firestore.collection("calls")
            .document(callId)
            .update(
                mapOf(
                    "answer_sdp" to answerSdp,
                    "status" to "in_call"
                )
            )
    }

    //Send ICE candidate
    fun sendIceCandidate(callId: String, userId: String, candidate: Map<String, Any>){
        firestore.collection("calls")
            .document(callId)
            .collection("ice_candidates")
            .document(userId)
            .collection("candidates")
            .add(candidate)
    }

    //Stop listening
    fun stopListening() {
        callListener?.remove()
        callListener = null
    }
}

