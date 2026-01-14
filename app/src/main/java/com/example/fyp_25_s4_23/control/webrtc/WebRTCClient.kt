package com.example.fyp_25_s4_23.control.webrtc

import android.content.Context
import android.util.Log
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

    // State guards for offer/answer ordering
    private var remoteOfferApplied: Boolean = false
    private var pendingAnswer: Boolean = false
    private var ended: Boolean = false

    // Callbacks to update UI/ViewModel
    private var onReadyToAnswer: ((Boolean) -> Unit)? = null
    private var onAnswered: (() -> Unit)? = null

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
        audioSource = factory.createAudioSource(audioConstraints())
        audioTrack = factory.createAudioTrack("AUDIO", audioSource)
        audioTrack.setEnabled(true)
    }

    private fun iceServers() = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
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
                    }
                }
            }
        )!!

        peerConnection.addTransceiver(audioTrack,RtpTransceiver.RtpTransceiverInit(
            RtpTransceiver.RtpTransceiverDirection.SEND_RECV
        ))
    }

    fun start() {
        configureAudioForCall()

        signaling.listenForIceCandidates(callId, remoteUserId) {
            peerConnection.addIceCandidate(
                IceCandidate(
                    it["sdpMid"] as String?,
                    (it["sdpMLineIndex"] as Long).toInt(),
                    it["candidate"] as String
                )
            )
        }

        if (isCaller) createOffer()
    }

    private fun createOffer() {
        peerConnection.createOffer(object : SdpObserverImpl() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection.setLocalDescription(this, sdp)
                signaling.sendOffer(callId, sdp.description)
                Log.d("WebRTC","Offer sent")
            }
        }, MediaConstraints())
    }

    //callee receive offer
    fun onRemoteOfferReceived(offer: String) {
        // Reset guard and notify that we haven't applied the offer yet
        remoteOfferApplied = false
        peerConnection.setRemoteDescription(
            object : SdpObserverImpl() {
                override fun onSetSuccess() {
                    remoteOfferApplied = true
                    onReadyToAnswer?.invoke(true)
                    Log.d("WebRTC","Remote offer applied (onSetSuccess)")
                    if (pendingAnswer) {
                        pendingAnswer = false
                        Log.d("WebRTC","Pending answer exists, creating answer now")
                        createAnswer()
                    }
                }

                override fun onSetFailure(error: String) {
                    Log.e("WebRTC","Failed to set remote offer: $error")
                    onReadyToAnswer?.invoke(false)
                }
            },
            SessionDescription(SessionDescription.Type.OFFER, offer)
        )
        Log.d("WebRTC","Remote offer set (setRemoteDescription called)")
    }

    //User tap answer
    fun answerIncomingCall(): Boolean {
        if (isCaller) return false

        if (!remoteOfferApplied) {
            pendingAnswer = true
            Log.d("WebRTC", "Answer requested but remote offer not yet applied; queuing answer")
            onReadyToAnswer?.invoke(false)
            return false
        }

        val sigState = peerConnection.signalingState()
        if (sigState != PeerConnection.SignalingState.HAVE_REMOTE_OFFER && sigState != PeerConnection.SignalingState.HAVE_LOCAL_PRANSWER) {
            Log.w("WebRTC", "PeerConnection not in correct signaling state: $sigState; queuing answer")
            pendingAnswer = true
            return false
        }

        createAnswer()
        return true
    }


    //Caller receive answer
    fun onRemoteAnswerReceived(answer: String) {
        peerConnection.setRemoteDescription(
            SdpObserverImpl(),
            SessionDescription(SessionDescription.Type.ANSWER, answer)
        )
        Log.d("WebRTC","Remote answer sent")
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
            // Mark pending so it will retry when setRemoteDescription completes
            pendingAnswer = true
        }
    }

    fun endCall() {
        if (ended) {
            Log.d("WebRTC", "endCall called but already ended; skipping")
            return
        }
        ended = true

        try {
            if (this::peerConnection.isInitialized) peerConnection.close()
        } catch (e: Exception) {
            Log.w("WebRTC", "Error closing peerConnection", e)
        }

        try {
            if (this::audioSource.isInitialized) {
                audioSource.dispose()
            } else {
                Log.d("WebRTC", "audioSource not initialized, skipping dispose")
            }
        } catch (e: Exception) {
            Log.w("WebRTC", "audioSource.dispose() failed or already disposed", e)
        }

        try {
            signaling.stopListening()
        } catch (e: Exception) {
            Log.w("WebRTC", "Failed to stop signaling listener", e)
        }

        // notify UI that call is no longer answerable
        onReadyToAnswer?.invoke(false)
        onAnswered = null
        onReadyToAnswer = null

        Log.i("WebRTC", "Call ended cleanly")
    }

    private fun audioConstraints(): MediaConstraints =
        MediaConstraints().apply{
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation","true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl","true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter","true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression","true"))
        }

    private fun configureAudioForCall(){
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        audioManager.isMicrophoneMute = false

        Log.d("WebRTC","AudioManager configured for VOIP")
    }
}
