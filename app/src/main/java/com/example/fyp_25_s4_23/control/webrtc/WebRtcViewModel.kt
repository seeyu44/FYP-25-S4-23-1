package com.example.fyp_25_s4_23.control.webrtc

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import com.example.fyp_25_s4_23.control.webrtc.FirebaseSignalingManager


class WebRtcCallViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val signaling = FirebaseSignalingManager()
    private var webRtcClient: WebRtcClient? = null

    fun startCall(
        callId: String,
        isCaller: Boolean,
        remoteUserId: String
    ) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            ?: error("User not logged in")

        Log.d(
            "CALL_VM",
            "startCall(callId=$callId, isCaller=$isCaller, remote=$remoteUserId)"
        )

        webRtcClient = WebRtcClient(
            context = getApplication(),
            isCaller = isCaller,
            signaling = signaling,
            callId = callId,
            userId = currentUid,
            remoteUserId = remoteUserId
        ).also { client ->

            //engine → ViewModel callback
            client.onEngineEnded = {
                Log.w("CALL_VM", "Engine ended call")
                // UI layer should react (Activity / StateFlow / finish())
            }

            client.initialize()
            client.createAudioTrack()
            client.createPeerConnection()
        }

        //listen to OFFER / ANSWER / STATUS
        signaling.listenToCall(
            callId = callId,
            isCaller = isCaller,   // ⭐ REQUIRED

            onOffer = { offer ->
                if (!isCaller) {
                    Log.d("CALL_VM", "Received OFFER")
                    webRtcClient?.onRemoteOfferReceived(offer)
                }
            },

            onAnswer = { answer ->
                if (isCaller) {
                    Log.d("CALL_VM", "Received ANSWER")
                    webRtcClient?.onRemoteAnswerReceived(answer)
                }
            },

            onStatus = { status ->
                Log.d("CALL_VM", "Status update = $status")

                if (status == "ended") {
                    Log.w("CALL_VM", "Remote ended call")
                    webRtcClient?.onRemoteEnded()
                }
            },

            onStatusWithReason = { status, reason ->
                if (status == "ended" && reason == "blocked_contact") {
                    Log.w("CALL_VM", "Call rejected: contact is blocked")
                    // The reason will be handled by the ViewModel
                }
            }
        )
        webRtcClient?.start()
    }

    fun hangUp() {
        Log.i("CALL_VM", "User requested hang up")
        webRtcClient?.requestHangUp()
    }

    override fun onCleared() {
        super.onCleared()
        Log.w("CALL_VM", "ViewModel cleared → ending call")
    }
}
