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
    private var iceListener: ListenerRegistration? = null


    //Listen to call state changes (ringing/accepted/ended)
    fun listenToCall(
        callId: String,
        onOffer: (String) -> Unit,
        onEnded: () -> Unit
    ) {
        callListener = firestore.collection("calls")
            .document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                when (snapshot.getString("status")) {
                    "ringing", "accepted", "in_call" -> {
                        snapshot.getString("offer_sdp")?.let(onOffer)
                    }
                    "ended" -> onEnded()
                }
            }
    }


    // Send offer to callee
    fun sendOffer(callId: String, offerSdp: String){
        firestore.collection("calls")
            .document(callId)
            .update(
                mapOf(
                    "offer.sdp" to offerSdp,
                    "status" to "ringing"
                )
            )
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

    fun listenForIceCandidates(
        callId: String,
        remoteUserId: String,
        onCandidate: (Map<String, Any>) -> Unit
    ) {
        iceListener = firestore.collection("calls")
            .document(callId)
            .collection("ice_candidates")
            .document(remoteUserId)
            .collection("candidates")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documents?.forEach { doc ->
                    doc.data?.let(onCandidate)
                }
            }
    }

    //Stop listening
    fun stopListening() {
        callListener?.remove()
        iceListener?.remove()
        callListener = null
        iceListener = null
    }
}

