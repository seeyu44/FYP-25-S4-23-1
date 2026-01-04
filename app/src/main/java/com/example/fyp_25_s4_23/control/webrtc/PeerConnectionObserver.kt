package com.example.fyp_25_s4_23.control.webrtc

import android.util.Log
import org.webrtc.*

open class PeerConnectionObserver : PeerConnection.Observer {

    override fun onIceCandidate(candidate: IceCandidate) {}

    override fun onTrack(transceiver: RtpTransceiver?) {
        val track = transceiver?.receiver?.track()
        if (track is AudioTrack) {
            track.setEnabled(true)
            Log.i("WebRTC", "Remote audio track received")
        }
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState) {}

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        Log.d("WebRTC", "ICE receiving: $receiving")
    }

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}

    override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}

    override fun onAddStream(stream: MediaStream) {}

    override fun onRemoveStream(stream: MediaStream) {}

    override fun onDataChannel(channel: DataChannel) {}

    override fun onRenegotiationNeeded() {}
}
