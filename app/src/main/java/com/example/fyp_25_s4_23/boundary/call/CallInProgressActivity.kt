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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.example.fyp_25_s4_23.control.call.IncomingCallListener

class CallInProgressActivity : ComponentActivity() {

    private val viewModel: CallInProgressViewModel by viewModels()

    private var webRtcClient: WebRtcClient? = null
    private var signaling: FirebaseSignalingManager? = null
    private var callId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IncomingCallListener.stop()

        signaling = FirebaseSignalingManager()
        val signalingRef = signaling!!

        // READ INTENT EXTRAS
        Log.d("CALL_INTENT", "extras=${intent.extras}")

        callId = intent.getStringExtra(IncomingCallIntent.EXTRA_CALL_ID)

        val isIncoming =
            intent.getBooleanExtra(IncomingCallIntent.EXTRA_IS_INCOMING, false)

        val remoteUserId =
            intent.getStringExtra(IncomingCallIntent.EXTRA_REMOTE_USER_ID)

        val localUserId = FirebaseAuthManager.currentUser()?.uid
            ?: error("User not logged in")


        // VALIDATION
        val activeSnapshot = ActiveCallStore.state.value

        if ((callId.isNullOrBlank() || remoteUserId.isNullOrBlank()) && activeSnapshot == null) {
            Log.e(
                "CALL_INTENT",
                "Missing CALL_ID / REMOTE_USER_ID and no active telecom call"
            )
            finish()
            return
        }

        // SIGNALING + WEBRTC INITIALIZATION

        if (!callId.isNullOrBlank() && !remoteUserId.isNullOrBlank()) {

            val callIdNN = callId!!
            val remoteUserNN = remoteUserId!!

            Log.d(
                "CALL_TYPE",
                "Using SIGNALING/WebRTC call (callId=$callIdNN, remote=$remoteUserNN)"
            )

            Log.d(
                "CALL_SIG",
                "Initializing signaling (isIncoming=$isIncoming)"
            )

            val microphonePermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) {
                        initializeWebRtc(
                            client = webRtcClient,
                            signalingRef = signalingRef,
                            callIdNN = callIdNN,
                            remoteUserNN = remoteUserNN,
                            isIncoming = isIncoming
                        )
                    } else {
                        Log.w("CALL_SIG", "Microphone permission denied")
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

            val hasMic =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

            if (hasMic) {
                initializeWebRtc(
                    client = webRtcClient,
                    signalingRef = signalingRef,
                    callIdNN = callIdNN,
                    remoteUserNN = remoteUserNN,
                    isIncoming = isIncoming
                )
            } else {
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            Log.d("CALL_TYPE", "Using TELECOM-only call path")
        }

        // UI
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
                            finish()
                        },
                        onMute = viewModel::toggleMute
                    )
                }
            }
        }
    }


    // WEBRTC + SIGNALING WIRING
    private fun initializeWebRtc(
        client: WebRtcClient?,
        signalingRef: FirebaseSignalingManager,
        callIdNN: String,
        remoteUserNN: String,
        isIncoming: Boolean
    ) {
        viewModel.attachWebRtcClient(client)

        client?.onEngineEnded ={
            Log.w("CALL_END","Engine ended -> finishing activity")
            ActiveCallStore.clear()
            runOnUiThread { finish() }
        }
        client?.initialize()
        client?.createAudioTrack()
        client?.createPeerConnection()

        signalingRef.listenToCall(
            callId = callIdNN,
            isCaller = !isIncoming,
            onOffer = { offer ->
                Log.w(
                    "WEBRTC_FLOW",
                    "Activity received OFFER | client=${System.identityHashCode(client)}"
                )
                if (isIncoming) {
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
                    "ringing" -> viewModel.setRinging(remoteUserNN, preserveReady = true)
                    "in_call" -> viewModel.setActive()
                    "ended" -> {
                        Log.w("CALL_SIG", "Remote requested end")
                        webRtcClient?.onRemoteEnded()
                    }
                }
            }
        )


        client?.start()
    }

    // CLEANUP
    override fun onDestroy() {
        super.onDestroy()

        try {
            webRtcClient?.requestHangUp()
            signaling?.stopListening()
            IncomingCallListener.start(applicationContext)
        } catch (e: Exception) {
            Log.e("CALL_END", "Error during cleanup", e)
        }

        webRtcClient = null
        signaling = null
    }
}
