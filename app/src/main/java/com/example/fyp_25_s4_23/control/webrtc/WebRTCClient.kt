package com.example.fyp_25_s4_23.control.webrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import android.media.AudioManager
import org.webrtc.*
import org.webrtc.CandidatePairChangeEvent
import com.example.fyp_25_s4_23.control.detection.DeepfakeDetectionService
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    private lateinit var audioTrack: org.webrtc.AudioTrack
    private var audioDeviceModule: JavaAudioDeviceModule? = null

    private var iceListeningStarted = false

    enum class AudioState { MUTED, SILENT, ACTIVE }

    // Callbacks for UI: local audio state (MUTED/SILENT/ACTIVE) and remote audio activity (true/false)
    var onLocalAudioStateChanged: ((AudioState) -> Unit)? = null
    var onRemoteAudioStateChanged: ((Boolean) -> Unit)? = null
    var onSpeakerStateChanged: ((Boolean) -> Unit)? = null
    
    // Deepfake detection (receiver-side only)
    private var detectionService: DeepfakeDetectionService? = null
    private var incomingAudioSink: AudioTrackSink? = null
    private var remoteAudioTrack: org.webrtc.AudioTrack? = null
    var onDeepfakeDetected: ((Float, Boolean) -> Unit)? = null
    var onDetectionUpdate: ((Float) -> Unit)? = null

    // Track whether local audio was enabled via setLocalAudioEnabled()
    private var localEnabled: Boolean = true
    private var speakerEnabled: Boolean = false

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

    // Audio monitoring loop state
    private var monitoringHandler: Handler? = null
    private var monitoringRunnable: Runnable? = null
    private var monitorPollMs: Long = 300
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
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
    private var iceInProgress: Boolean = false  // Track if ICE is actively negotiating
    private var ringTimeoutHandler: Handler? = null
    private var ringTimeoutRunnable: Runnable? = null
    private val ringTimeoutMs: Long = 120_000  // Extended to 120s for slow networks + first call setup
    
    // Audio capture (legacy, not used for digital detection)
    private var audioRecord: android.media.AudioRecord? = null
    private var audioCaptureThread: Thread? = null
    
    // Demo audio playback (MediaPlayer approach - mic picks it up naturally)
    private var demoMediaPlayer: android.media.MediaPlayer? = null
    private var isDemoMode = false

    fun setOnReadyToAnswerListener(listener: (Boolean) -> Unit) { onReadyToAnswer = listener }
    fun setOnAnsweredListener(listener: () -> Unit) { onAnswered = listener }

    fun initialize() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions()
        )

        audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .createAudioDeviceModule()

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
        
        // ✅ Verification: Confirm JavaAudioDeviceModule is registered
        Log.w("AUDIO_PIPELINE_VERIFY", "✅ Factory initialized with audioDeviceModule: ${audioDeviceModule != null}")
        Log.w("AUDIO_PIPELINE_VERIFY", "✅ Audio device module class: ${audioDeviceModule?.javaClass?.simpleName}")
    }

    private fun flushPendingIce() {
        if (pendingIceCandidates.isEmpty()) return

        Log.w("ICE_FLOW", "Flushing ${pendingIceCandidates.size} queued ICE candidates")

        pendingIceCandidates.forEach { candidate ->
            Log.w(
                "ICE_FLOW",
                "addIceCandidate (flush) → ${candidate.sdp}"
            )
            val result = peerConnection.addIceCandidate(candidate)
            Log.w("ICE_FLOW", "addIceCandidate (flush) result=$result")
        }

        pendingIceCandidates.clear()
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }


    fun createAudioTrack() {
        // When audioDeviceModule is set on factory, createAudioSource will use it as the capture source.
        // This ensures a SINGLE pipeline: Mic → audioDeviceModule → setSamplesReadyCallback → encoding
        audioSource = factory.createAudioSource(MediaConstraints())
        Log.w("AUDIO_PIPELINE", "✅ AudioSource created — will use audioDeviceModule for capture")
        
        audioTrack = factory.createAudioTrack("AUDIO", audioSource)
        Log.w("AUDIO_PIPELINE", "✅ AudioTrack linked to AudioSource (unified pipeline)")
        
        try {
            audioTrack.setEnabled(true)
            Log.w("AUDIO_PIPELINE", "✅ AudioTrack enabled — ready for transmission")
        } catch (e: Exception) {
            Log.w("WebRTC", "Failed to set audioTrack enabled by default", e)
        }
    }

    /**
     * INCOMING interception: attach sink to remote AudioTrack
     */
    private fun attachIncomingDetectionSink(track: org.webrtc.AudioTrack?) {
        if (track == null) return
        if (incomingAudioSink != null) return

        incomingAudioSink = object : AudioTrackSink {
            override fun onData(
                data: ByteBuffer,
                bitsPerSample: Int,
                sampleRate: Int,
                numberOfChannels: Int,
                numberOfFrames: Int,
                absoluteCaptureTimestampMs: Long
            ) {
                if (bitsPerSample != 16) return
                val shortCount = numberOfFrames * numberOfChannels
                if (shortCount <= 0) return

                val shorts = ShortArray(shortCount)
                data.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

                // Downmix to mono if needed
                val mono = if (numberOfChannels == 1) {
                    shorts
                } else {
                    val frames = numberOfFrames
                    val out = ShortArray(frames)
                    var idx = 0
                    for (i in 0 until frames) {
                        var sum = 0
                        for (ch in 0 until numberOfChannels) {
                            sum += shorts[idx++].toInt()
                        }
                        out[i] = (sum / numberOfChannels).toShort()
                    }
                    out
                }

                // Resample to 16k if needed (simple decimation)
                val resampled = when {
                    sampleRate == 16000 -> mono
                    sampleRate % 16000 == 0 -> {
                        val ratio = sampleRate / 16000
                        val outSize = mono.size / ratio
                        val out = ShortArray(outSize)
                        var j = 0
                        var i = 0
                        while (i < mono.size && j < outSize) {
                            out[j++] = mono[i]
                            i += ratio
                        }
                        out
                    }
                    else -> {
                        Log.w("DEEPFAKE_AUDIO", "Unsupported sampleRate=$sampleRate for incoming detection")
                        return
                    }
                }

                detectionService?.feedAudioChunk(resampled)
            }
        }

        track.addSink(incomingAudioSink)
        Log.d("DEEPFAKE_AUDIO", "✅ Incoming audio sink attached for detection")
    }

    private fun detachIncomingDetectionSink() {
        val track = remoteAudioTrack
        val sink = incomingAudioSink
        if (track != null && sink != null) {
            track.removeSink(sink)
        }
        incomingAudioSink = null
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

    // Setup audio routing for WebRTC call
    private fun setupAudioRouting() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            
            // Request audio focus for voice call
            audioManager.requestAudioFocus(
                null,
                android.media.AudioManager.STREAM_VOICE_CALL,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            
            // Set mode to IN_COMMUNICATION for VoIP calls (crucial!)
            audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            
            // Set speaker state
            audioManager.isSpeakerphoneOn = speakerEnabled
            
            // Ensure microphone is not muted
            audioManager.isMicrophoneMute = false
            
            Log.d("WebRTC", "Audio routing setup - mode=${audioManager.mode}, speaker=${audioManager.isSpeakerphoneOn}, volume=${audioManager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL)}")
        } catch (e: Exception) {
            Log.w("WebRTC", "Failed to setup audio routing", e)
        }
    }

    // Toggle speaker on/off (earpiece vs speakerphone)
    fun setSpeakerEnabled(enabled: Boolean) {
        try {
            speakerEnabled = enabled
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            
            // Ensure we're in communication mode first
            if (audioManager.mode != android.media.AudioManager.MODE_IN_COMMUNICATION) {
                audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                Log.d("WebRTC", "Set mode to IN_COMMUNICATION")
            }
            
            // Toggle speaker
            audioManager.isSpeakerphoneOn = enabled
            
            // Verify it worked
            val actualSpeaker = audioManager.isSpeakerphoneOn
            val volume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL)
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL)
            
            Log.d("WebRTC", "Speaker toggle: requested=$enabled, actual=$actualSpeaker, volume=$volume/$maxVolume, mode=${audioManager.mode}")
            
            onSpeakerStateChanged?.invoke(actualSpeaker)
        } catch (e: Exception) {
            Log.e("WebRTC", "Failed to set speaker enabled", e)
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
        // STUN server
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
            .createIceServer(),

        // TURN (UDP)
        PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80")
            .setUsername("f476761c5386c5bfd0c6cd56")
            .setPassword("iLGaUaMpckATerwK")
            .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
            .createIceServer(),

        // TURN (TCP fallback)
        PeerConnection.IceServer.builder("turn:global.relay.metered.ca:443?transport=tcp")
            .setUsername("f476761c5386c5bfd0c6cd56")
            .setPassword("iLGaUaMpckATerwK")
            .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
            .createIceServer(),

        // TURN (TLS – most reliable on strict networks)
        PeerConnection.IceServer.builder("turns:global.relay.metered.ca:443")
            .setUsername("f476761c5386c5bfd0c6cd56")
            .setPassword("iLGaUaMpckATerwK")
            .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
            .createIceServer()


    )

    fun createPeerConnection() {
        val servers = iceServers()
        Log.w("ICE_CONFIG", "Configured ${servers.size} ICE servers:")
        servers.forEachIndexed { i, server -> 
            Log.w("ICE_CONFIG", "  [$i] ${server.urls}")
        }
        
        val rtcConfig = PeerConnection.RTCConfiguration(servers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            // Use ALL for production (tries direct P2P first, falls back to TURN)
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            // Increase ICE timeout for cross-network connections
            iceConnectionReceivingTimeout = 10000  // 10 seconds instead of default 5
            iceBackupCandidatePairPingInterval = 25000  // 25 seconds
        }
        
        Log.w("ICE_CONFIG", "RTCConfiguration: bundle=${rtcConfig.bundlePolicy}, " +
            "continualGathering=${rtcConfig.continualGatheringPolicy}, " +
            "transports=${rtcConfig.iceTransportsType}")
        
        peerConnection = factory.createPeerConnection(
            rtcConfig,
            object : PeerConnectionObserver() {

                override fun onIceCandidate(candidate: IceCandidate) {
                    // Detect candidate type
                    val candidateType = when {
                        candidate.sdp.contains("typ relay") -> "RELAY ✅"
                        candidate.sdp.contains("typ srflx") -> "SRFLX (STUN)"
                        candidate.sdp.contains("typ host") -> "HOST (local)"
                        else -> "UNKNOWN"
                    }
                    Log.w(
                        "ICE_FLOW",
                        "LOCAL ICE [$candidateType] → ${candidate.sdp}"
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
                        remoteAudioTrack = track
                        attachIncomingDetectionSink(track)
                        if (!currentRemoteActive) {
                            currentRemoteActive = true
                            onRemoteAudioStateChanged?.invoke(true)
                        }
                    }
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    Log.w("ICE_STATE", "ICE connection state → $state")

                    when (state) {
                        PeerConnection.IceConnectionState.CHECKING -> {
                            // ICE is trying to connect - give it time
                            iceInProgress = true
                            Log.i("ICE_STATE", "🔄 ICE negotiation in progress...")
                        }

                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            iceInProgress = false
                            logSelectedCandidatePairViaStats()
                            if (!callConnected) {
                                callConnected = true
                                cancelRingTimeout()
                                setupAudioRouting()
                                startAudioMonitoring()
                                
                                // Start deepfake detection when call connects
                                startDeepfakeDetection()
                                
                                onAnswered?.invoke()

                                if (isCaller) {
                                    signaling.updateCallStatus(callId, "in_call")
                                }

                                Log.w("ICE_STATE", "✅ ICE connected successfully")
                            }
                        }

                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            Log.w("ICE_STATE", "⚠️ ICE temporarily disconnected - attempting reconnect...")
                            // Don't end call immediately, WebRTC will try to reconnect
                        }

                        PeerConnection.IceConnectionState.FAILED -> {
                            iceInProgress = false
                            Log.e("ICE_STATE", "❌ ICE FAILED — waiting briefly before ending...")

                            Handler(Looper.getMainLooper()).postDelayed({
                                if (!callConnected && !ended) {
                                    Log.e("ICE_STATE", "❌ ICE still failed — ending call")
                                    signaling.updateCallStatus(callId, "ended")
                                    engineEnd("ICE_FAILED")
                                }
                            }, 15_000)
                        }

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

        // Start audio monitoring for local/remote audio state
        startAudioMonitoring()

        if (isCaller) createOffer()
    }

    private fun startRingTimeout() {
        if (ringTimeoutHandler != null) return
        ringTimeoutHandler = Handler(Looper.getMainLooper())
        ringTimeoutRunnable = Runnable {
            if (ended || callConnected) return@Runnable
            
            // Don't timeout if ICE is actively negotiating
            if (iceInProgress) {
                Log.i("CALL_TIMEOUT", "ICE in progress, extending timeout for another 60s...")
                ringTimeoutHandler?.postDelayed(ringTimeoutRunnable!!, 60_000)
                return@Runnable
            }
            
            Log.w("CALL_TIMEOUT", "Ring timeout: No answer within ${ringTimeoutMs}ms → ending call")
            signaling.updateCallStatus(callId, "ended")
            engineEnd("RING_TIMEOUT")
        }
        Log.d("CALL_TIMEOUT", "Ring timeout started: ${ringTimeoutMs}ms for isCaller=$isCaller")
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
                        maybeStartIceListening()
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
                    flushPendingIce()
                    maybeStartIceListening()
                    onReadyToAnswer?.invoke(true)
                    Log.d("CALL_UI","Answer enabled(offer applied)")

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

        if (remoteAnswerApplied) {
            Log.w("SDP_FLOW", "Remote answer already applied — ignoring duplicate")
            return
        }

        Log.w("SDP_FLOW", "Applying remote ANSWER")

        peerConnection.setRemoteDescription(
            object : SdpObserverImpl() {
                override fun onSetSuccess() {
                    remoteAnswerApplied = true
                    Log.w("SDP_FLOW", "setRemoteDescription(answer) SUCCESS")
                    flushPendingIce()
                    maybeStartIceListening()
                    //updates UI while waiting for ICE to connect
                    onAnswered?.invoke()
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
                        maybeStartIceListening()
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
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DEEPFAKE DETECTION METHODS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * ═══════════════════════════════════════════════════════════════════════════════
     * DEEPFAKE DETECTION ARCHITECTURE (PHONE 1 ↔ PHONE 2)
     * ═══════════════════════════════════════════════════════════════════════════════
     * 
     * ✅ HOW IT WORKS:
     * 
     * PHONE 1:
     *   1. Captures Phone 1's microphone input (what Phone 1 is saying)
     *   2. Runs deepfake detection on Phone 1's voice
     *   3. Sends Phone 1's detection result to Firestore
     *   4. Listens for Phone 2's detection result from Firestore
     *   5. Shows alert if Phone 2's voice is deepfake
     * 
     * PHONE 2:
     *   1. Captures Phone 2's microphone input (what Phone 2 is saying)
     *   2. Runs deepfake detection on Phone 2's voice
     *   3. Sends Phone 2's detection result to Firestore
     *   4. Listens for Phone 1's detection result from Firestore
     *   5. Shows alert if Phone 1's voice is deepfake
     * 
     * 📊 DATA FLOW:
     * 
     *   Phone 1 Mic → Analyze → Firestore → Phone 2 UI (sees if Phone 1 is fake)
     *   Phone 2 Mic → Analyze → Firestore → Phone 1 UI (sees if Phone 2 is fake)
     * 
     * Each phone monitors ITS OWN input and informs the OTHER user!
     * ═══════════════════════════════════════════════════════════════════════════════
     */
    fun startDeepfakeDetection() {
        Log.i("DEEPFAKE", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i("DEEPFAKE", "🎯 STARTING DEEPFAKE DETECTION")
        Log.i("DEEPFAKE", "   Mode: Receiver-side (incoming audio only)")
        Log.i("DEEPFAKE", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        if (detectionService != null) {
            Log.w("DEEPFAKE", "⚠️ Detection already running, skipping initialization")
            return
        }
        
        try {
            // ═══ STEP 1: Initialize Detection Service for INCOMING audio ═══
            Log.d("DEEPFAKE", "📦 Creating service to analyze INCOMING audio...")
            detectionService = DeepfakeDetectionService(
                context = context,
                callId = callId
            )
            Log.d("DEEPFAKE", "✅ Detection service created")
            
            // ═══ STEP 2: Receiver-side detection + Firebase sync ═══
            detectionService?.onDeepfakeDetected = { score ->
                Log.w("DEEPFAKE", "━━━ INCOMING AUDIO FLAGGED AS DEEPFAKE! ━━━")
                Log.w("DEEPFAKE", "   Score: $score")
                Log.w("DEEPFAKE", "   Sending to Firestore for remote user ($remoteUserId) to see")
                signaling.sendDetectionResult(callId, remoteUserId, score, true)
                onDeepfakeDetected?.invoke(score, true)
            }
            detectionService?.onDetectionUpdate = { result ->
                Log.d("DEEPFAKE", "📊 Incoming audio analyzed: score=${result.score}, fake=${result.isDeepfake}")
                Log.d("DEEPFAKE", "   → Sending to Firestore for remote user")
                signaling.sendDetectionResult(callId, remoteUserId, result.score, result.isDeepfake)
                onDetectionUpdate?.invoke(result.score)
                if (result.isDeepfake) {
                    onDeepfakeDetected?.invoke(result.score, true)
                }
            }

            // ═══ STEP 3: Start Monitoring INCOMING audio ═══
            detectionService?.startMonitoring()
            attachIncomingDetectionSink(remoteAudioTrack)
            
            Log.i("DEEPFAKE", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i("DEEPFAKE", "✅ DEEPFAKE DETECTION FULLY RUNNING")
            Log.i("DEEPFAKE", "   ✓ Monitoring: INCOMING audio track")
            Log.i("DEEPFAKE", "   ✓ UI updates: Receiver-side only")
            Log.i("DEEPFAKE", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            
        } catch (e: Exception) {
            Log.e("DEEPFAKE", "❌ FAILED TO START DETECTION", e)
        }
    }
    
    /**
     * Play demo audio through speaker - microphone will pick it up and transmit it
     * This is the SIMPLE approach that actually works with WebRTC
     * @param filename Name of file in assets/demo_audio/ folder, or null to stop playing
     */
    fun playDemoAudio(filename: String?) {
        if (filename != null) {
            try {
                Log.w("DEMO_AUDIO", "🎭 Starting demo audio playback: $filename")
                
                // Stop any existing playback
                demoMediaPlayer?.release()
                
                // Load audio file from demo_audio folder
                demoMediaPlayer = android.media.MediaPlayer()
                val assetPath = "demo_audio/$filename"
                
                try {
                    // Try direct file descriptor (works for uncompressed files)
                    val afd = context.assets.openFd(assetPath)
                    demoMediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    Log.d("DEMO_AUDIO", "✅ Loaded $assetPath directly")
                } catch (e: java.io.FileNotFoundException) {
                    // File is compressed - copy to temp file first
                    Log.d("DEMO_AUDIO", "File is compressed, copying to temp location...")
                    val extension = filename.substringAfterLast('.', "tmp")
                    val tempFile = java.io.File.createTempFile("demo_audio_", ".$extension", context.cacheDir)
                    
                    context.assets.open(assetPath).use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    demoMediaPlayer?.setDataSource(tempFile.absolutePath)
                    Log.d("DEMO_AUDIO", "✅ Loaded $assetPath from temp file")
                    
                    // Clean up temp file when done playing
                    demoMediaPlayer?.setOnCompletionListener {
                        tempFile.delete()
                    }
                }
                
                // ═══ CRITICAL FIX: Force speaker phone ON during demo audio playback ═══
                // This ensures speaker plays (not earpiece) and audio reaches mic
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val wasInSpeakerMode = audioManager.isSpeakerphoneOn
                audioManager.isSpeakerphoneOn = true
                Log.d("DEMO_AUDIO", "📢 Forced speaker mode ON (was=$wasInSpeakerMode)")
                
                // Set system volume to maximum for VOICE_CALL stream
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
                Log.d("DEMO_AUDIO", "🔊 Set VOICE_CALL stream to max volume: $maxVolume")
                
                // Play through VOICE_CALL stream at MAXIMUM volume
                demoMediaPlayer?.setAudioStreamType(android.media.AudioManager.STREAM_VOICE_CALL)
                demoMediaPlayer?.isLooping = true
                demoMediaPlayer?.setVolume(1.0f, 1.0f)  // ← CHANGED from 0.8f to 1.0f (MAX)
                
                demoMediaPlayer?.prepare()
                demoMediaPlayer?.start()
                
                isDemoMode = true
                
                Log.i("DEMO_AUDIO", "✅ Demo audio playing at MAX VOLUME through speaker - mic should capture it")
                
            } catch (e: Exception) {
                Log.e("DEMO_AUDIO", "Failed to play demo audio: $filename", e)
                Log.e("DEMO_AUDIO", "Make sure file exists in: app/src/main/assets/demo_audio/")
                Log.e("DEMO_AUDIO", "Supported formats: .wav, .mp3, .mp4, .m4a, .flac")
            }
        } else {
            try {
                Log.d("DEMO_AUDIO", "🛑 Stopping demo audio")
                demoMediaPlayer?.stop()
                demoMediaPlayer?.release()
                demoMediaPlayer = null
                isDemoMode = false
                
                Log.i("DEMO_AUDIO", "✅ Demo audio stopped")
            } catch (e: Exception) {
                Log.e("DEMO_AUDIO", "Error stopping demo audio", e)
            }
        }
    }
    
    /**
     * Stop deepfake detection monitoring
     */
    fun stopDeepfakeDetection() {
        try {
            detachIncomingDetectionSink()
            detectionService?.stopMonitoring()
            detectionService?.cleanup()
            detectionService = null
            
            Log.i("WebRTC", "Deepfake detection stopped")
        } catch (e: Exception) {
            Log.e("WebRTC", "Failed to stop deepfake detection", e)
        }
    }
    
    /**
     * Start capturing audio from MY microphone
     * 
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 🎤 WHAT THIS DOES:
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * 
     * 1. Captures audio from MY microphone (VOICE_COMMUNICATION source)
     * 2. Feeds the audio chunks to the detection service
     * 3. Detection service analyzes if MY voice is deepfake
     * 4. Results are sent to Firestore for the REMOTE user to see
     * 
     * This monitors what I AM SAYING, NOT what I AM HEARING from the remote user.
     * 
     * Example:
     *   - Phone 1 (Alice) runs this → Captures Alice's voice → Sends to Firestore
     *   - Phone 2 (Bob) listens to Firestore → Sees if Alice is using deepfake
     * 
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     */
    private fun startAudioCapture() {
        try {
            // 1️⃣ Permission guard (MANDATORY)
            if (!hasRecordAudioPermission()) {
                Log.e("DEEPFAKE_AUDIO", "❌ RECORD_AUDIO permission not granted")
                return
            }

            Log.d("DEEPFAKE_AUDIO", "🎤 Starting audio capture of MY microphone...")

            val minBufferSize = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            Log.d("DEEPFAKE_AUDIO", "Min buffer size: $minBufferSize")

            // VOICE_COMMUNICATION monitors YOUR microphone during calls
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                16000, // 16kHz sample rate for detection
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufferSize, 16000 * 2) // ≥ 1 second buffer
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(
                    "DEEPFAKE_AUDIO",
                    "❌ AudioRecord failed to initialize. State=${audioRecord?.state}"
                )
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            Log.d("DEEPFAKE_AUDIO", "✅ AudioRecord started successfully")

            var chunkCount = 0

            audioCaptureThread = Thread {
                val buffer = ShortArray(3200) // 200ms @ 16kHz
                Log.d("DEEPFAKE_AUDIO", "📡 Audio capture thread started")

                while (
                    detectionService != null &&
                    audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
                ) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: break

                    when {
                        read > 0 -> {
                            chunkCount++
                            if (chunkCount % 50 == 0) {
                                Log.d(
                                    "DEEPFAKE_AUDIO",
                                    "📊 Captured $chunkCount audio chunks so far"
                                )
                            }
                            detectionService?.feedAudioChunk(buffer.copyOf(read))
                        }

                        read < 0 -> {
                            Log.e("DEEPFAKE_AUDIO", "❌ AudioRecord read error: $read")
                            break
                        }
                    }
                }

                Log.d(
                    "DEEPFAKE_AUDIO",
                    "🛑 Audio capture thread stopped. Total chunks: $chunkCount"
                )

                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) { }

                audioRecord = null
            }

            audioCaptureThread?.start()
            Log.d("DEEPFAKE_AUDIO", "✅ Audio capture started for deepfake detection")

        } catch (se: SecurityException) {
            // 2️⃣ Explicit security handling (lint requirement)
            Log.e(
                "DEEPFAKE_AUDIO",
                "❌ Missing RECORD_AUDIO permission (SecurityException)",
                se
            )
        } catch (e: Exception) {
            Log.e("DEEPFAKE_AUDIO", "❌ Failed to start audio capture", e)
        }
    }


    /**
     * Stop audio capture
     */
    private fun stopAudioCapture() {
        // Cleanup handled by detection service stop
    }
    
    /**
     * Get detection statistics
     */
    fun getDetectionStatistics(): DeepfakeDetectionService.DetectionStatistics? {
        return detectionService?.getStatistics()
    }

    private fun engineEnd(reason: String) {
        if (ended) return
        ended = true

        Log.w("CALL_END", "Call ending (engine): $reason")

        stopAudioMonitoring()
        stopDeepfakeDetection()
        cancelRingTimeout()
        releaseAudioRouting()
        
        // Stop demo audio if playing
        demoMediaPlayer?.release()
        demoMediaPlayer = null
        isDemoMode = false

        try {
            signaling.stopListening()
            peerConnection.close()
            audioSource.dispose()
            audioDeviceModule?.release()
            audioDeviceModule = null
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

    fun close() {
        requestHangUp()
    }

    fun onRemoteEnded() {
        Log.w("CALL_ENGINE", "Remote ended call")
        engineEnd("REMOTE_ENDED")
    }

    private fun logSelectedCandidatePairViaStats() {
        peerConnection.getStats { report ->
            val stats = report.statsMap.values.toList()

            // 1) Best method: transport.selectedCandidatePairId
            val transport = stats.firstOrNull { it.type == "transport" }
            val selectedPairId = transport?.members?.get("selectedCandidatePairId") as? String

            // 2) Fallback: candidate-pair where selected OR nominated is true
            val selectedPair = when {
                selectedPairId != null ->
                    stats.firstOrNull { it.id == selectedPairId && it.type == "candidate-pair" }

                else ->
                    stats.firstOrNull { s ->
                        s.type == "candidate-pair" && (
                                (s.members["selected"] == true) ||
                                        (s.members["nominated"] == true) ||
                                        (s.members["state"] == "succeeded")
                                )
                    }
            }

            if (selectedPair == null) {
                Log.w("ICE_PAIR", "⚠️ Connected, but couldn't resolve selected pair from stats")
                return@getStats
            }

            val localId = selectedPair.members["localCandidateId"] as? String
            val remoteId = selectedPair.members["remoteCandidateId"] as? String

            val local = stats.firstOrNull { it.id == localId }
            val remote = stats.firstOrNull { it.id == remoteId }

            Log.i(
                "ICE_PAIR",
                """
            ✅ Selected ICE candidate pair
            ├─ pairId=${selectedPair.id}
            ├─ state=${selectedPair.members["state"]} nominated=${selectedPair.members["nominated"]} selected=${selectedPair.members["selected"]}
            ├─ Local : type=${local?.members?.get("candidateType")} protocol=${local?.members?.get("protocol")} address=${local?.members?.get("address")}:${local?.members?.get("port")}
            └─ Remote: type=${remote?.members?.get("candidateType")} protocol=${remote?.members?.get("protocol")} address=${remote?.members?.get("address")}:${remote?.members?.get("port")}
            """.trimIndent()
            )
        }
    }

    private fun maybeStartIceListening() {
        Log.d(
            "ICE_GUARD",
            "maybeStartIceListening: started=$iceListeningStarted ended=$ended " +
                    "localSDP=${peerConnection.localDescription != null} " +
                    "remoteSDP=${peerConnection.remoteDescription != null}"
        )
        if (iceListeningStarted){
            Log.d("ICE_GUARD", "ICE listener already started — skipping")
            return
        }
        if (ended){
            Log.d("ICE_GUARD", "Call ended — not starting ICE listener")
            return
        }

        // Only start once BOTH SDPs exist
        if (
            peerConnection.localDescription != null &&
            peerConnection.remoteDescription != null
        ) {
            iceListeningStarted = true

            Log.w("ICE_FLOW", "🚀 Starting ICE listener (SDP ready)")

            signaling.listenForIceCandidates(callId, remoteUserId) { itMap ->
                val candidate = IceCandidate(
                    itMap["sdpMid"] as String?,
                    (itMap["sdpMLineIndex"] as Long).toInt(),
                    itMap["candidate"] as String
                )

                Log.w(
                    "ICE_FLOW",
                    "REMOTE ICE received → sdpMid=${candidate.sdpMid}, " +
                            "mLine=${candidate.sdpMLineIndex}, candidate=${candidate.sdp}"
                )

                if (peerConnection.remoteDescription == null) {
                    Log.w("ICE_FLOW", "Remote SDP not set yet → queue ICE")
                    pendingIceCandidates.add(candidate)
                    return@listenForIceCandidates
                }

                val result = peerConnection.addIceCandidate(candidate)
                Log.w("ICE_FLOW", "addIceCandidate() result=$result")
            }
        }
    }

}