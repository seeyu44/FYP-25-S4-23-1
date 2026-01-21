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

    private var remoteAnswerApplied: Boolean = false
    private var pendingAnswer: Boolean = false
    private var ended: Boolean = false
    var onEngineEnded: (() -> Unit)? = null

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
        //STUN
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    )

    fun createPeerConnection() {
        peerConnection = factory.createPeerConnection(
            PeerConnection.RTCConfiguration(iceServers()),
            object : PeerConnectionObserver() {

                override fun onIceCandidate(candidate: IceCandidate) {
                    Log.w(
                        "ICE_FLOW",
                        "LOCAL ICE → sdpMid=${candidate.sdpMid}, " +
                                "sdpMLineIndex=${candidate.sdpMLineIndex}, " +
                                "candidate=${candidate.sdp.take(60)}..."
                    )

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
                    Log.w("ICE_STATE", "ICE connection state → $state")

                    when (state) {
                        PeerConnection.IceConnectionState.CHECKING ->
                            Log.w("ICE_STATE", "ICE checking candidates")

                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            if (!callConnected) {
                                callConnected = true
                                cancelRingTimeout()
                                startAudioMonitoring()
                                onAnswered?.invoke()
                                Log.w("ICE_STATE", "ICE connected successfully")
                                //signaling.updateCallStatus(callId, "in_call")

                                if(isCaller) {
                                    signaling.updateCallStatus("callId", "in_call")
                                }
                            }
                        }

                        PeerConnection.IceConnectionState.FAILED -> {
                            Log.e("ICE_STATE", "ICE FAILED")
                            signaling.updateCallStatus(callId, "ended")
                            engineEnd("ICE_FAILED")
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED ->
                            Log.w("ICE_STATE", "ICE disconnected")


                        else -> Unit
                    }
                }

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                    Log.w("ICE_GATHER", "ICE gathering state → $state")
                }

                override fun onSignalingChange(state: PeerConnection.SignalingState) {
                    Log.w("SIG_STATE", "Signaling state → $state")
                }
            }
        )!!

        peerConnection.addTrack(audioTrack)
        Log.w("ICE_FLOW", "Audio track added → ICE can start")
    }

    fun start() {
        // start ring timeout (caller only)
        if (isCaller) {
            startRingTimeout()
        }

        signaling.listenForIceCandidates(callId, remoteUserId) {

            Log.w(
                "ICE_FLOW",
                "REMOTE ICE received → $it"
            )
            peerConnection.addIceCandidate(
                IceCandidate(
                    it["sdpMid"] as String?,
                    (it["sdpMLineIndex"] as Long).toInt(),
                    it["candidate"] as String
                )
            )
        }

        //startAudioMonitoring()

        if (isCaller) createOffer()
    }

    private fun startRingTimeout() {
        if (ringTimeoutHandler != null) return
        ringTimeoutHandler = Handler(Looper.getMainLooper())
        ringTimeoutRunnable = Runnable {
            if (ended || callConnected) return@Runnable
            Log.w("CALL_TIMEOUT", "No answer within ${ringTimeoutMs}ms → ending call")
            signaling.updateCallStatus(callId, "ended")
            engineEnd("RING_TIMEOUT")
        }
        ringTimeoutHandler?.postDelayed(ringTimeoutRunnable!!, ringTimeoutMs)
    }

    private fun cancelRingTimeout() {
        try { ringTimeoutRunnable?.let { ringTimeoutHandler?.removeCallbacks(it) } } catch (_: Exception) {}
        ringTimeoutRunnable = null
        ringTimeoutHandler = null
    }

    private fun sdpConstraints(): MediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
    }
    private fun createOffer() {
        peerConnection.createOffer(object : SdpObserverImpl() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.w("SDP_FLOW", "Offer created → calling setLocalDescription")

                peerConnection.setLocalDescription(object : SdpObserverImpl() {
                    override fun onSetSuccess() {
                        Log.w("SDP_FLOW", "setLocalDescription(offer) SUCCESS → ICE can start")
                        signaling.sendOffer(callId, sdp.description)
                    }

                    override fun onSetFailure(error: String) {
                        Log.e("SDP_FLOW", "setLocalDescription(offer) FAILED: $error")
                    }
                }, sdp)
            }

            override fun onCreateFailure(error: String) {
                Log.e("SDP_FLOW", "createOffer FAILED: $error")
            }
        }, sdpConstraints())
    }

    // callee receive offer
    fun onRemoteOfferReceived(offer: String) {

        // Guard against duplicate offers (Firestore fires multiple times)
        if (remoteOfferApplied) {
            Log.w("WEBRTC_FLOW", "Remote offer already applied — ignoring duplicate")
            return
        }

        Log.d("WEBRTC_FLOW", "Applying remote offer")

        peerConnection.setRemoteDescription(
            object : SdpObserverImpl() {

                override fun onSetSuccess() {
                    remoteOfferApplied = true
                    Log.w("SDP_FLOW", "setRemoteDescription(offer) SUCCESS → ReadyToAnswer=true")
                    onReadyToAnswer?.invoke(true)

                    if (pendingAnswer) {
                        pendingAnswer = false
                        Log.w("SDP_FLOW", "pendingAnswer=true → creating answer now")
                        createAnswer()
                    }
                }

                override fun onSetFailure(error: String) {
                    Log.e("SDP_FLOW", "setRemoteDescription(offer) FAILED: $error")
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

        signaling.updateCallStatus(callId,"accepted")

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

        if (remoteAnswerApplied){
            Log.w("SDP_FLOW", "Remote answer already applied — ignoring duplicate")
            return
        }

        Log.w("SDP_FLOW", "Applying remote ANSWER")

        peerConnection.setRemoteDescription(
            object : SdpObserverImpl() {
                override fun onSetSuccess() {
                    Log.w("SDP_FLOW", "setRemoteDescription(answer) SUCCESS")
                }

                override fun onSetFailure(error: String) {
                    Log.e("SDP_FLOW", "setRemoteDescription(answer) FAILED: $error")
                }
            },
            SessionDescription(SessionDescription.Type.ANSWER, answer)
        )
    }

    private fun createAnswer() {
        peerConnection.createAnswer(object : SdpObserverImpl() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                Log.w("SDP_FLOW", "Answer created → calling setLocalDescription")

                peerConnection.setLocalDescription(object : SdpObserverImpl() {
                    override fun onSetSuccess() {
                        Log.w("SDP_FLOW", "setLocalDescription(answer) SUCCESS → ICE can start")
                        signaling.sendAnswer(callId, sdp.description)
                    }

                    override fun onSetFailure(error: String) {
                        Log.e("SDP_FLOW", "setLocalDescription(answer) FAILED: $error")
                    }
                }, sdp)
            }

            override fun onCreateFailure(error: String) {
                Log.e("SDP_FLOW", "createAnswer FAILED: $error")
            }
        }, sdpConstraints())
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

    private fun engineEnd(reason: String) {
        if (ended) return
        ended = true

        Log.w("CALL_END", "Call ending (engine): $reason")

        stopAudioMonitoring()
        cancelRingTimeout()
        releaseAudioRouting()

        try {
            signaling.stopListening()
            peerConnection.close()
            audioSource.dispose()
        } catch (e: Exception) {
            Log.e("CALL_END", "Error during engineEnd cleanup", e)
        }

        onEngineEnded?.invoke()
    }

    fun requestHangUp() {
        if (ended) return
        Log.i("CALL_ENGINE", "User requested hang up")
        signaling.updateCallStatus(callId, "ended")
        engineEnd("USER_HANGUP")
    }

    fun onRemoteEnded() {
        Log.w("CALL_ENGINE", "Remote ended call")
        engineEnd("REMOTE_ENDED")
    }
}