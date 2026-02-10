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
import com.example.fyp_25_s4_23.entity.data.db.AppDatabase
import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.example.fyp_25_s4_23.util.DisplayNameResolver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.example.fyp_25_s4_23.data.remote.firebase.GlobalBlockRepository
import kotlinx.coroutines.tasks.await
private const val TAG_SIG = "CALL_SIG"
private const val TAG_WEBRTC = "WEBRTC_FLOW"
private lateinit var displayName : String

class CallInProgressActivity : ComponentActivity() {

    private val viewModel: CallInProgressViewModel by viewModels()

    private var webRtcClient: WebRtcClient? = null
    private var signaling: FirebaseSignalingManager? = null
    private var hasFlaggedGlobalBlock: Boolean = false
    private var isRemoteKnownContact: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IncomingCallListener.stop()

        val callId =
            intent.getStringExtra(IncomingCallIntent.EXTRA_CALL_ID) ?: return finish()
        val remoteUserId =
            intent.getStringExtra(IncomingCallIntent.EXTRA_REMOTE_USER_ID) ?: return finish()
        val isIncoming =
            intent.getBooleanExtra(IncomingCallIntent.EXTRA_IS_INCOMING, false)
        val remoteUsername = intent.getStringExtra(IncomingCallIntent.EXTRA_USERNAME)

        // Resolve contact name asynchronously to avoid blocking the main thread
        val database = com.example.fyp_25_s4_23.entity.data.db.AppDatabase.getInstance(this)
        val contactRepository = ContactRepository(database.contactDao())
        
        // Check if phone number was passed (for outgoing calls from saved contacts)
        val passedPhoneNumber = intent.getStringExtra(IncomingCallIntent.EXTRA_PHONE_NUMBER)
        val incomingDisplayName = intent.getStringExtra(IncomingCallIntent.EXTRA_DISPLAY_NAME)
        displayName = when {
            !incomingDisplayName.isNullOrBlank() -> incomingDisplayName
            !passedPhoneNumber.isNullOrBlank() -> passedPhoneNumber
            else -> remoteUserId
        }

        Log.d(TAG_SIG, "Call started → id=$callId incoming=$isIncoming resolved name=$displayName")

        viewModel.setCallDirection(isIncoming)
        viewModel.setDisplayName(displayName)

        lifecycleScope.launch {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val knownByPhone = if (!passedPhoneNumber.isNullOrBlank()) {
                contactRepository.getContactByPhoneNumber(currentUserId, passedPhoneNumber)
            } else {
                null
            }
            val usernameToCheck = remoteUsername ?: incomingDisplayName
            val knownByUsername = if (!usernameToCheck.isNullOrBlank()) {
                contactRepository.getContactByUsername(currentUserId, usernameToCheck)
            } else {
                null
            }
            isRemoteKnownContact = knownByPhone != null || knownByUsername != null

            val resolved = DisplayNameResolver.resolveDisplayName(
                contactRepository = contactRepository,
                currentUserId = currentUserId,
                userId = remoteUserId,
                fallbackName = incomingDisplayName,
                fallbackPhone = passedPhoneNumber
            )

            if (resolved.isNotBlank() && resolved != displayName) {
                displayName = resolved
                viewModel.setDisplayName(resolved)
            }
        }

        val localUserId = FirebaseAuthManager.currentUser()?.uid
        if (localUserId == null) {
            finish()
            return
        }

        signaling = FirebaseSignalingManager()
        ActiveCallStore.setWebRtcActive(callId, remoteUserId)

        val globalBlockRepository = GlobalBlockRepository()

        viewModel.onDeepfakeFlagged = { _ ->
            if (isIncoming && !hasFlaggedGlobalBlock && !isRemoteKnownContact) {
                lifecycleScope.launch {
                    try {
                        val callSnapshot = FirebaseFirestore.getInstance()
                            .collection("calls")
                            .document(callId)
                            .get()
                            .await()
                        val highestKey = "${remoteUserId}_highest_is_deepfake"
                        val highestIsDeepfake = callSnapshot.getBoolean(highestKey) == true
                        if (!highestIsDeepfake) {
                            Log.d(TAG_SIG, "Skip global flag: $highestKey is not true")
                            return@launch
                        }

                        hasFlaggedGlobalBlock = true
                        globalBlockRepository.flagUser(
                            userId = remoteUserId,
                            username = remoteUsername ?: incomingDisplayName,
                            phoneNumber = passedPhoneNumber,
                            callId = callId
                        )
                    } catch (e: Exception) {
                        Log.e(TAG_SIG, "Failed to flag user in global block list", e)
                    }
                }
            }
        }

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

        // Show incoming call UI only when WebRTC is ready to answer
        client.setOnReadyToAnswerListener { ready ->
            if (isIncoming && ready) {
                viewModel.setRinging(displayName, preserveReady = false, readyToAnswer = true)
            }
        }

        // Wire answer callback (for incoming calls)
        viewModel.onStartCallRequested = {
            Log.d(TAG_SIG, "User accepted call → answering...")
            client.answerIncomingCall()
            viewModel.setActive()
        }

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
                    "ringing" -> {
                        if (!isIncoming) {
                            viewModel.setRinging(displayName, preserveReady = true)
                        }
                    }

                    "accepted", "in_call" -> {
                        if (!isIncoming) viewModel.setActive()
                    }

                    "ended" -> {
                        viewModel.setDisconnected()
                        client.onRemoteEnded()
                    }
                }
            },

            onStatusWithReason = { status, reason ->
                if (status == "ended" && reason == "blocked_contact") {
                    Log.w(TAG_SIG, "Call rejected: contact is blocked")
                    viewModel.setDisconnectedWithReason(reason)
                    client.onRemoteEnded()
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
