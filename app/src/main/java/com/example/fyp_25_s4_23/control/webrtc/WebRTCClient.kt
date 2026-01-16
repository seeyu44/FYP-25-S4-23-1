package com.example.fyp_25_s4_23.control.webrtc

import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import org.webrtc.*

class WebRtcClient(
    private val context: Context,
    private val isCaller: Boolean,
    private val signaling: FirebaseSignalingManager,
    private val callId: String,
    private val userId: String,
    private val remoteUserId: String
) {

    private lateinit var factory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private lateinit var audioSource: AudioSource
    private lateinit var audioTrack: AudioTrack

    enum class AudioState { MUTED, SILENT, ACTIVE }

    // Callbacks for UI: local audio state (MUTED/SILENT/ACTIVE) and remote audio activity (true/false)
    var onLocalAudioStateChanged: ((AudioState) -> Unit)? = null
    var onRemoteAudioStateChanged: ((Boolean) -> Unit)? = null

    // Track whether local audio was enabled via setLocalAudioEnabled()
    private var localEnabled: Boolean = true

    // Monitoring / smoothing state
    private var monitoringHandler: Handler? = null
    private var monitoringRunnable: Runnable? = null
    private var monitorPollMs: Long = 300
    private var alpha: Double = 0.25
    private var smoothedLocalLevel: Double = 0.0
    private var smoothedRemoteLevel: Double = 0.0

    private val activeThreshold = 0.01
    private val silentThreshold = 0.005
    private val neededAbove = 2
    private val neededBelow = 3
    private var localAboveCount = 0
    private var localBelowCount = 0
    private var remoteAboveCount = 0
    private var remoteBelowCount = 0

    private var currentLocalState: AudioState = AudioState.SILENT
    private var currentRemoteActive: Boolean = false

    // State guards for offer/answer ordering
    private var remoteOfferApplied: Boolean = false
    private var pendingAnswer: Boolean = false
    private var ended: Boolean = false

    // Callbacks to update UI/ViewModel
    private var onReadyToAnswer: ((Boolean) -> Unit)? = null
    private var onAnswered: (() -> Unit)? = null

    // --- NEW: connection / timeout guards ---
    private var callConnected: Boolean = false
    private var ringTimeoutHandler: Handler? = null
    private var ringTimeoutRunnable: Runnable? = null
    private val ringTimeoutMs: Long = 30_000

    fun setOnReadyToAnswerListener(listener: (Boolean) -> Unit) { onReadyToAnswer = listener }
    fun setOnAnsweredListener(listener: () -> Unit) { onAnswered = listener }

    fun initialize() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions()
        )

        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    fun createAudioTrack() {
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("AUDIO", audioSource)
        try {
            audioTrack.setEnabled(true)
        } catch (e: Exception) {
            Log.w("WebRTC", "Failed to set audioTrack enabled by default", e)
        }
    }

    //Toggle local audio send for WebRTC calls (used by mute/unmute UI when not on Telecom)
    fun setLocalAudioEnabled(enabled: Boolean) {
        try {
            localEnabled = enabled
            if (this::audioTrack.isInitialized) {
                audioTrack.setEnabled(enabled)
                Log.d("WebRTC", "Local audio enabled=$enabled")
                if (!enabled) {
                    currentLocalState = AudioState.MUTED
                    onLocalAudioStateChanged?.invoke(AudioState.MUTED)
                } else {
                    currentLocalState = AudioState.SILENT
                    onLocalAudioStateChanged?.invoke(AudioState.SILENT)
                }
            } else {
                Log.w("WebRTC", "setLocalAudioEnabled called but audioTrack not initialized")
            }
        } catch (e: Exception) {
            Log.w("WebRTC", "Failed to set local audio enabled", e)
        }
    }

    private fun startAudioMonitoring(pollMs: Int = 300) {
        if (monitoringHandler != null) return
        monitorPollMs = pollMs.toLong()
        monitoringHandler = Handler(Looper.getMainLooper())
        monitoringRunnable = object : Runnable {
            override fun run() {
                try {
                    if (!this@WebRtcClient::peerConnection.isInitialized) {
                        monitoringHandler?.postDelayed(this, monitorPollMs)
                        return
                    }

                    try {
                        peerConnection.getStats(object : RTCStatsCollectorCallback {
                            override fun onStatsDelivered(report: RTCStatsReport?) {
                                var localSample = 0.0
                                var remoteSample = 0.0

                                if (report == null) return

                                try {
                                    val statsMap = report.statsMap
                                    for ((id, stat) in statsMap) {
                                        try {
                                            val type = stat.type
                                            val members = stat.members
                                            for ((name, valueAny) in members) {
                                                val nameLower = name
                                                if (
                                                    nameLower == "audioLevel" ||
                                                    nameLower == "audioOutputLevel" ||
                                                    nameLower == "audioInputLevel" ||
                                                    nameLower == "totalAudioEnergy"
                                                ) {
                                                    val d = when (valueAny) {
                                                        is Number -> valueAny.toDouble()
                                                        is String -> valueAny.toDoubleOrNull() ?: continue
                                                        else -> continue
                                                    }

                                                    if (id.contains("outbound") || type == "ssrc" || type == "outbound-rtp") {
                                                        localSample = d
                                                    } else if (id.contains("inbound") || type == "inbound-rtp") {
                                                        remoteSample = d
                                                    } else {
                                                        if (this@WebRtcClient::audioTrack.isInitialized) localSample = d else remoteSample = d
                                                    }
                                                }
                                            }
                                        } catch (_: Exception) { }
                                    }
                                } catch (_: Exception) { }

                                smoothedLocalLevel = alpha * localSample + (1 - alpha) * smoothedLocalLevel
                                smoothedRemoteLevel = alpha * remoteSample + (1 - alpha) * smoothedRemoteLevel

                                Log.d("WebRTC_STATS", "Local: $localSample/$smoothedLocalLevel | Remote: $remoteSample/$smoothedRemoteLevel")

                                // Local state
                                if (!localEnabled) {
                                    if (currentLocalState != AudioState.MUTED) {
                                        currentLocalState = AudioState.MUTED
                                        onLocalAudioStateChanged?.invoke(AudioState.MUTED)
                                    }
                                } else {
                                    if (smoothedLocalLevel > activeThreshold) {
                                        localAboveCount++; localBelowCount = 0
                                        if (localAboveCount >= neededAbove && currentLocalState != AudioState.ACTIVE) {
                                            currentLocalState = AudioState.ACTIVE
                                            onLocalAudioStateChanged?.invoke(AudioState.ACTIVE)
                                        }
                                    } else if (smoothedLocalLevel < silentThreshold) {
                                        localBelowCount++; localAboveCount = 0
                                        if (localBelowCount >= neededBelow && currentLocalState != AudioState.SILENT) {
                                            currentLocalState = AudioState.SILENT
                                            onLocalAudioStateChanged?.invoke(AudioState.SILENT)
                                        }
                                    } else {
                                        localAboveCount = 0
                                        localBelowCount = 0
                                    }
                                }

                                // Remote active boolean
                                val remoteCandidate = smoothedRemoteLevel > activeThreshold
                                if (remoteCandidate) {
                                    remoteAboveCount++; remoteBelowCount = 0
                                    if (remoteAboveCount >= neededAbove && !currentRemoteActive) {
                                        currentRemoteActive = true
                                        onRemoteAudioStateChanged?.invoke(true)
                                    }
                                } else {
                                    remoteBelowCount++; remoteAboveCount = 0
                                    if (remoteBelowCount >= neededBelow && currentRemoteActive) {
                                        currentRemoteActive = false
                                        onRemoteAudioStateChanged?.invoke(false)
                                    }
                                }
                            }
                        })
                    } catch (e: Exception) {
                        Log.w("WebRTC", "Audio monitoring getStats failed", e)
                    }
                } finally {
                    monitoringHandler?.postDelayed(this, monitorPollMs)
                }
            }
        }
        monitoringHandler?.post(monitoringRunnable!!)
    }

    private fun stopAudioMonitoring() {
        try { monitoringRunnable?.let { monitoringHandler?.removeCallbacks(it) } } catch (_: Exception) {}
        monitoringRunnable = null
        monitoringHandler = null

        smoothedLocalLevel = 0.0
        smoothedRemoteLevel = 0.0
        localAboveCount = 0
        localBelowCount = 0
        remoteAboveCount = 0
        remoteBelowCount = 0

        if (currentRemoteActive) {
            currentRemoteActive = false
            onRemoteAudioStateChanged?.invoke(false)
        }

        if (localEnabled && currentLocalState != AudioState.SILENT) {
            currentLocalState = AudioState.SILENT
            onLocalAudioStateChanged?.invoke(AudioState.SILENT)
        }
    }

    private fun iceServers() = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        // do we need to add TURN servers here?
    )

    fun createPeerConnection() {
        peerConnection = factory.createPeerConnection(
            PeerConnection.RTCConfiguration(iceServers()),
            object : PeerConnectionObserver() {

                override fun onIceCandidate(candidate: IceCandidate) {
                    signaling.sendIceCandidate(
                        callId,
                        userId,
                        mapOf(
                            "candidate" to candidate.sdp,
                            "sdpMid" to candidate.sdpMid,
                            "sdpMLineIndex" to candidate.sdpMLineIndex
                        )
                    )
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    val track = transceiver?.receiver?.track()
                    if (track is AudioTrack) {
                        track.setEnabled(true)
                        Log.d("WebRTC", "Remote audio track received")
                        if (!currentRemoteActive) {
                            currentRemoteActive = true
                            onRemoteAudioStateChanged?.invoke(true)
                        }
                    }
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    Log.d("ICE", "ICE connection state = $state")

                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            if (!callConnected) {
                                callConnected = true
                                cancelRingTimeout()
                                Log.d("CALL_STATE", "ICE connected → updating Firebase")
                                signaling.updateCallStatus(callId, "in_call")
                            }
                        }

                        // ICE FAILED handling
                        PeerConnection.IceConnectionState.FAILED -> {
                            Log.e("ICE", "ICE failed → ending call")
                            signaling.updateCallStatus(callId, "ended")
                            endCall()
                        }

                        else -> Unit
                    }
                }
            }
        )!!

        peerConnection.addTrack(audioTrack)
    }

    fun start() {
        // start ring timeout (caller only)
        if (isCaller) {
            startRingTimeout()
        }

        signaling.listenForIceCandidates(callId, remoteUserId) {
            peerConnection.addIceCandidate(
                IceCandidate(
                    it["sdpMid"] as String?,
                    (it["sdpMLineIndex"] as Long).toInt(),
                    it["candidate"] as String
                )
            )
        }

        startAudioMonitoring()

        if (isCaller) createOffer()
    }

    private fun startRingTimeout() {
        if (ringTimeoutHandler != null) return
        ringTimeoutHandler = Handler(Looper.getMainLooper())
        ringTimeoutRunnable = Runnable {
            if (ended || callConnected) return@Runnable
            Log.w("CALL_TIMEOUT", "No answer within ${ringTimeoutMs}ms → ending call")
            signaling.updateCallStatus(callId, "ended")
            endCall()
        }
        ringTimeoutHandler?.postDelayed(ringTimeoutRunnable!!, ringTimeoutMs)
    }

    private fun cancelRingTimeout() {
        try { ringTimeoutRunnable?.let { ringTimeoutHandler?.removeCallbacks(it) } } catch (_: Exception) {}
        ringTimeoutRunnable = null
        ringTimeoutHandler = null
    }

    private fun createOffer() {
        peerConnection.createOffer(object : SdpObserverImpl() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection.setLocalDescription(this, sdp)
                signaling.sendOffer(callId, sdp.description)
                Log.d("WebRTC", "Offer sent")
            }
        }, MediaConstraints())
    }

    // callee receive offer
    fun onRemoteOfferReceived(offer: String) {
        remoteOfferApplied = false
        peerConnection.setRemoteDescription(
            object : SdpObserverImpl() {
                override fun onSetSuccess() {
                    remoteOfferApplied = true
                    onReadyToAnswer?.invoke(true)
                    Log.d("WebRTC", "Remote offer applied (onSetSuccess)")
                    if (pendingAnswer) {
                        pendingAnswer = false
                        Log.d("WebRTC", "Pending answer exists, creating answer now")
                        createAnswer()
                    }
                }

                override fun onSetFailure(error: String) {
                    Log.e("WebRTC", "Failed to set remote offer: $error")
                    onReadyToAnswer?.invoke(false)
                }
            },
            SessionDescription(SessionDescription.Type.OFFER, offer)
        )
        Log.d("WebRTC", "Remote offer set (setRemoteDescription called)")
    }

    // User tap answer
    fun answerIncomingCall(): Boolean {
        if (isCaller) return false

        if (!remoteOfferApplied) {
            pendingAnswer = true
            Log.d("WebRTC", "Answer requested but remote offer not yet applied; queuing answer")
            onReadyToAnswer?.invoke(false)
            return false
        }

        val sigState = peerConnection.signalingState()
        if (
            sigState != PeerConnection.SignalingState.HAVE_REMOTE_OFFER &&
            sigState != PeerConnection.SignalingState.HAVE_LOCAL_PRANSWER
        ) {
            Log.w("WebRTC", "PeerConnection not in correct signaling state: $sigState; queuing answer")
            pendingAnswer = true
            return false
        }

        createAnswer()
        return true
    }

    // Caller receive answer
    fun onRemoteAnswerReceived(answer: String) {
        peerConnection.setRemoteDescription(
            SdpObserverImpl(),
            SessionDescription(SessionDescription.Type.ANSWER, answer)
        )
        Log.d("WebRTC", "Remote answer applied")
    }

    private fun createAnswer() {
        try {
            peerConnection.createAnswer(object : SdpObserverImpl() {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    peerConnection.setLocalDescription(this, sdp)
                    signaling.sendAnswer(callId, sdp.description)
                    Log.d("WebRTC", "Answer created and sent")
                    onAnswered?.invoke()
                }

                override fun onCreateFailure(error: String) {
                    Log.e("WebRTC", "Failed to create answer: $error")
                }
            }, MediaConstraints())
        } catch (e: Exception) {
            Log.e("WebRTC", "createAnswer failed to start", e)
            pendingAnswer = true
        }
    }

    // release audio routing on end
    private fun releaseAudioRouting() {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

        audioManager.mode = android.media.AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
        audioManager.isMicrophoneMute = false

        Log.d("WebRTC", "Audio routing released")
    }

    fun endCall() {
        if (ended) {
            Log.d("WebRTC", "endCall called but already ended; skipping")
            return
        }
        ended = true

        // Ensure timeout stops
        cancelRingTimeout()

        // Release audio routing early
        try { releaseAudioRouting() } catch (_: Exception) {}

        stopAudioMonitoring()

        try {
            if (this::peerConnection.isInitialized) peerConnection.close()
        } catch (e: Exception) {
            Log.w("WebRTC", "Error closing peerConnection", e)
        }

        try {
            if (this::audioSource.isInitialized) audioSource.dispose()
        } catch (e: Exception) {
            Log.w("WebRTC", "audioSource.dispose() failed or already disposed", e)
        }

        try {
            signaling.stopListening()
        } catch (e: Exception) {
            Log.w("WebRTC", "Failed to stop signaling listener", e)
        }

        onReadyToAnswer?.invoke(false)
        onAnswered = null
        onReadyToAnswer = null

        Log.i("WebRTC", "Call ended cleanly")
    }
}
