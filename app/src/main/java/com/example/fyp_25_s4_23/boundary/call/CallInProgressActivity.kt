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
    private var callId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        signaling = FirebaseSignalingManager()
        val signalingRef = signaling!!

        //Read intent extras
        Log.d("CALL_INTENT","extras=${intent.extras}")
        val callId = intent.getStringExtra(IncomingCallIntent.EXTRA_CALL_ID)
        this.callId = callId
        val isIncoming = intent.getBooleanExtra(IncomingCallIntent.EXTRA_IS_INCOMING,false)
        val remoteUserId = intent.getStringExtra(IncomingCallIntent.EXTRA_REMOTE_USER_ID)

        val localUserId = FirebaseAuthManager.currentUser()?.uid
            ?: error("User not logged in")

        // If we don't have signaling extras, allow Telecom path (ActiveCallStore) to drive UI
        val activeSnapshot = ActiveCallStore.state.value
        if ((callId.isNullOrBlank() || remoteUserId.isNullOrBlank()) && activeSnapshot == null) {
            Log.e("CALL_INTENT","Missing Call_ID and REMOTE_USER_ID and no active telecom call")
            finish()
            return
        }

        // Setup signaling + WebRTC only if we have a callId and remoteUserId (signaling-based call)
        if (!callId.isNullOrBlank() && !remoteUserId.isNullOrBlank()) {
            // Create non-null locals for Kotlin type-safety
            val callIdNN = callId!!
            val remoteUserNN = remoteUserId!!

            Log.d("CALL_TYPE", "Using SIGNALING/WebRTC call (callId=$callIdNN, remote=$remoteUserNN)")
            Log.d("CALL_SIG", "Initializing signaling for callId=$callIdNN remoteUser=$remoteUserNN isIncoming=$isIncoming")

            val microphonePermissionLauncher = registerForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    initializeWebRtc(webRtcClient, signalingRef, callIdNN, remoteUserNN, isIncoming)
                } else {
                    Log.w("CALL_SIG", "Microphone permission denied; cannot start WebRTC call")
                    finish()
                }
            }

            webRtcClient = WebRtcClient(
                context = this,
                isCaller = !isIncoming,
                signaling = signalingRef,
                callId = callIdNN,
                userId = localUserId,
                remoteUserId = remoteUserNN
            )

            val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasMic) {
                initializeWebRtc(webRtcClient, signalingRef, callIdNN, remoteUserNN, isIncoming)
            } else {
                microphonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        } else {
            Log.d("CALL_TYPE", "Using TELECOM call (system dialer integration)")
        }

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
                            // notify remote (if signaling-based)
                            viewModel.hangUp()
                            finish()
                        },
                        onMute = viewModel::toggleMute
                    )
                }
            }
        }
    }

    private fun initializeWebRtc(client: WebRtcClient?, signalingRef: FirebaseSignalingManager, callIdNN: String, remoteUserNN: String, isIncoming: Boolean) {
        viewModel.attachWebRtcClient(client)
        client?.initialize()
        client?.createAudioTrack()
        client?.createPeerConnection()

        signalingRef.listenToCall(
            callId = callIdNN,
            onOffer = { offer ->
                Log.d("CALL_SIG", "onOffer callback invoked for callId=$callIdNN")
                if (isIncoming) {
                    Log.d("CALL_SIG", "Applying remote offer for callId=$callIdNN")
                    viewModel.setRinging(remoteUserNN)
                    client?.onRemoteOfferReceived(offer)
                }
            },
            onAnswer = { answer ->
                if (!isIncoming) {
                    client?.onRemoteAnswerReceived(answer)
                }
            },
            onStatus = { status ->
                when (status) {
                    "ringing" -> viewModel.setRinging(remoteUserNN)
                    "in_call" -> viewModel.setActive()
                    "ended" -> {
                        viewModel.setDisconnected()
                        finish()
                    }
                }
            }
        )

        client?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        // notify remote that call ended if we had a signaling call id
        //when app is backgrounded, prevents call from hanging up
        webRtcClient = null
    }
}
