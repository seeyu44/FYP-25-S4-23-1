package com.example.fyp_25_s4_23.control.webrtc

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class SdpObserverImpl : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) {
        Log.e("WebRTC", "SDP create error: $error")
    }

    override fun onSetFailure(error: String) {
        Log.e("WebRTC", "SDP set error: $error")
    }
}