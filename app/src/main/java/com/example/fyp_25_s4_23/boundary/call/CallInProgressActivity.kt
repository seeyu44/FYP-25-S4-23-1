package com.example.fyp_25_s4_23.boundary.call

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import com.example.fyp_25_s4_23.control.webrtc.FirebaseSignalingManager
import com.example.fyp_25_s4_23.control.webrtc.WebRtcClient
import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseAuthManager
import com.example.fyp_25_s4_23.ui.theme.FYP25S423Theme
import com.example.fyp_25_s4_23.control.call.IncomingCallIntent

class CallInProgressActivity : ComponentActivity() {

    private val viewModel: CallInProgressViewModel by viewModels()

    private var webRtcClient: WebRtcClient? = null
    private var signaling: FirebaseSignalingManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        signaling = FirebaseSignalingManager()
        val signalingRef = signaling!!

        //Read intent extras
        Log.d("CALL_INTENT","extras=${intent.extras}")
        val callId = intent.getStringExtra(IncomingCallIntent.EXTRA_CALL_ID)
        if(callId.isNullOrBlank()){
            Log.e("CALL_INTENT","Missing Call_ID")
            finish()
            return
        }

        val isIncoming = intent.getBooleanExtra(IncomingCallIntent.EXTRA_IS_INCOMING,false)

        val remoteUserId = intent.getStringExtra(IncomingCallIntent.EXTRA_REMOTE_USER_ID)
        if (remoteUserId.isNullOrBlank()) {
            Log.e("CALL_INTENT","Missing REMOTE_USER_ID")
            finish()
            return
        }

        val localUserId = FirebaseAuthManager.currentUser()?.uid
            ?: error("User not logged in")

        //Setup signaling + WebRTC

        webRtcClient = WebRtcClient(
            context = this,
            isCaller = !isIncoming,
            signaling = signalingRef,
            callId = callId,
            userId = localUserId,
            remoteUserId = remoteUserId
        )

        //Initialize WebRTC ONCE
        webRtcClient!!.initialize()
        webRtcClient!!.createAudioTrack()
        webRtcClient!!.createPeerConnection()

        //Listen to signaling updates
        signalingRef.listenToCall(
            callId = callId,

            onOffer = { offer ->
                if (isIncoming) {
                    webRtcClient!!.onRemoteOfferReceived(offer)
                }
            },

            onAnswer = { answer ->
                if (!isIncoming) {
                    webRtcClient!!.onRemoteAnswerReceived(answer)
                }
            },

            onEnded = {
                webRtcClient!!.endCall()
                finish()
            }
        )

        //Start ICE + create offer if caller
        webRtcClient!!.start()

        //UI
        setContent {
            FYP25S423Theme {
                Surface {
                    CallInProgressScreen(
                        state = viewModel.state,
                        onAnswer = {
                            viewModel.answer()
                        },
                        onHangUp = {
                            viewModel.hangUp()
                            signaling?.stopListening()
                            webRtcClient?.endCall()
                            finish()
                        },
                        onMute = viewModel::toggleMute
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        signaling?.stopListening()
        webRtcClient?.endCall()
    }
}
