package com.example.fyp_25_s4_23.boundary.call

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.fyp_25_s4_23.control.call.ActiveCallStore
import com.example.fyp_25_s4_23.control.call.IncomingCallIntent
import com.example.fyp_25_s4_23.control.call.IncomingCallListener
import com.example.fyp_25_s4_23.control.webrtc.FirebaseSignalingManager
import com.example.fyp_25_s4_23.control.webrtc.WebRtcClient
import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseAuthManager
import com.example.fyp_25_s4_23.ui.theme.FYP25S423Theme
import androidx.lifecycle.lifecycleScope
import com.example.fyp_25_s4_23.util.VibratorUtil
private const val TAG_SIG = "CALL_SIG"
private const val TAG_WEBRTC = "WEBRTC_FLOW"
private lateinit var displayName : String

class CallInProgressActivity : ComponentActivity() {

    private val viewModel: CallInProgressViewModel by viewModels()

    private var webRtcClient: WebRtcClient? = null
    private var signaling: FirebaseSignalingManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IncomingCallListener.stop()

        val callId =
            intent.getStringExtra(IncomingCallIntent.EXTRA_CALL_ID) ?: return finish()
        val remoteUserId =
            intent.getStringExtra(IncomingCallIntent.EXTRA_REMOTE_USER_ID) ?: return finish()
        val isIncoming =
            intent.getBooleanExtra(IncomingCallIntent.EXTRA_IS_INCOMING, false)

        displayName =
            if (isIncoming) {
                // Callee sees caller name
                intent.getStringExtra(IncomingCallIntent.EXTRA_DISPLAY_NAME)
                    ?: remoteUserId
            } else {
                // Caller sees callee name
                remoteUserId   // TEMP (later replace with callee display name)
            }


        Log.d(TAG_SIG, "Call started → id=$callId incoming=$isIncoming")

        viewModel.setCallDirection(isIncoming)

        val localUserId =
            FirebaseAuthManager.currentUser()?.uid ?: return finish()

        signaling = FirebaseSignalingManager()
        ActiveCallStore.setWebRtcActive(callId, remoteUserId)

        webRtcClient = WebRtcClient(
            context = this,
            isCaller = !isIncoming,
            signaling = signaling!!,
            callId = callId,
            userId = localUserId,
            remoteUserId = remoteUserId
        )

        val micPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) startWebRtc(callId, remoteUserId, isIncoming)
                else finish()
            }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startWebRtc(callId, remoteUserId, isIncoming)
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        // Load available demo audio files
        val demoAudioFiles = loadDemoAudioFiles()
        
        setContent {
            FYP25S423Theme {
                CallInProgressScreen(
                    state = viewModel.state,
                    onAnswer = viewModel::answer,
                    onHangUp = {
                        viewModel.hangUp()
                        finish()
                    },
                    onMute = viewModel::toggleMute,
                    onToggleSpeaker = viewModel::toggleSpeaker,
                    onPlayDemoAudio = { filename ->
                        webRtcClient?.playDemoAudio(filename)
                    },
                    demoAudioFiles = demoAudioFiles
                )
            }
        }
        lifecycleScope.launchWhenStarted {
            viewModel.events.collect { event ->
                when (event) {
                    is CallUiEvent.Vibrate -> {
                        Log.w("CALL_UI", "🔔 Vibrating (deepfake score=${event.score})")
                        VibratorUtil.vibrate(this@CallInProgressActivity)
                    }
                }
            }
        }

    }

    private fun startWebRtc(callId: String, remoteUserId: String, isIncoming: Boolean) {
        val client = webRtcClient ?: return

        viewModel.attachWebRtcClient(client)

        client.onEngineEnded = {
            Log.w("ICE_STATE", "Engine ended → finishing activity")
            ActiveCallStore.clear()
            runOnUiThread { finish() }
        }

        client.initialize()
        client.createAudioTrack()
        client.createPeerConnection()

        signaling?.listenToCall(
            callId = callId,
            isCaller = !isIncoming,

            onOffer = { offer ->
                Log.d(TAG_WEBRTC, "OFFER received")
                if (isIncoming) {
                    viewModel.setRinging(displayName, preserveReady = true)
                    client.onRemoteOfferReceived(offer)
                }
            },

            onAnswer = { answer ->
                Log.d(TAG_WEBRTC, "ANSWER received")
                if (!isIncoming) client.onRemoteAnswerReceived(answer)
            },

            onStatus = { status ->
                Log.d(TAG_SIG, "Status → $status (incoming=$isIncoming)")
                when (status) {
                    "ringing" ->
                        viewModel.setRinging(displayName, preserveReady = true)

                    "accepted", "in_call" -> {
                        if (!isIncoming) viewModel.setActive()
                    }

                    "ended" -> {
                        viewModel.setDisconnected()
                        client.onRemoteEnded()
                    }
                }
            }
        )

        client.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        signaling?.stopListening()
        IncomingCallListener.start(applicationContext)
        Log.d(TAG_SIG, "Call activity destroyed")
    }
    
    private fun loadDemoAudioFiles(): List<String> {
        return try {
            assets.list("demo_audio")?.filter { filename ->
                filename.endsWith(".wav", ignoreCase = true) ||
                filename.endsWith(".mp3", ignoreCase = true) ||
                filename.endsWith(".mp4", ignoreCase = true) ||
                filename.endsWith(".m4a", ignoreCase = true) ||
                filename.endsWith(".flac", ignoreCase = true)
            }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG_SIG, "Failed to load demo audio files", e)
            emptyList()
        }
    }
}
