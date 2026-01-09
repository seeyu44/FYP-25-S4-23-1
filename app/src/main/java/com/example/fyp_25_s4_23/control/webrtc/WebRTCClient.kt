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
            }
        )!!

        peerConnection.addTrack(audioTrack)
    }

    fun start() {
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
            }
        }, MediaConstraints())
    }

    fun onRemoteOfferReceived(offer: String) {
        peerConnection.setRemoteDescription(
            SdpObserverImpl(),
            SessionDescription(SessionDescription.Type.OFFER, offer)
        )
        createAnswer()
    }

    private fun createAnswer() {
        peerConnection.createAnswer(object : SdpObserverImpl() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection.setLocalDescription(this, sdp)
                signaling.sendAnswer(callId, sdp.description)
            }
        }, MediaConstraints())
    }

    fun endCall() {
        peerConnection.close()
        audioSource.dispose()
        signaling.stopListening()
        Log.i("WebRTC", "Call ended cleanly")
    }
}
