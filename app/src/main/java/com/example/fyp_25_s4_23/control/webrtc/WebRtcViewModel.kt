package com.example.fyp_25_s4_23.control.webrtc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth

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
        val currentUid = FirebaseAuth.getInstance().currentUser!!.uid

        webRtcClient = WebRtcClient(
            context = getApplication(),
            isCaller = isCaller,
            signaling = signaling,
            callId = callId,
            userId = currentUid,
            remoteUserId = remoteUserId
        )

        webRtcClient!!.initialize()
        webRtcClient!!.createAudioTrack()
        webRtcClient!!.createPeerConnection()

        signaling.listenToCall(
            callId = callId,

            onOffer = { offer ->
                if (!isCaller) {
                    webRtcClient!!.onRemoteOfferReceived(offer)
                }
            },

            onAnswer = { answer ->
                if (isCaller) {
                    webRtcClient!!.onRemoteAnswerReceived(answer)
                }
            },

            onEnded = {
                webRtcClient!!.endCall()
            }
        )
    }
}
