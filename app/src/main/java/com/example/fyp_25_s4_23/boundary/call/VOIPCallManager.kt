package com.example.fyp_25_s4_23.boundary.call

import android.content.Context
import android.content.Intent
import com.example.fyp_25_s4_23.control.webrtc.FirebaseSignalingManager
import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseAuthManager
import com.example.fyp_25_s4_23.control.call.IncomingCallIntent
import java.util.UUID

object VoipCallManager {

    fun startOutgoingVoipCall(
        context: Context,
        calleeUserId: String
    ) {
        val caller = FirebaseAuthManager.currentUser()
            ?: error("User not logged in")

        val callId = UUID.randomUUID().toString()

        //Create Firestore call document
        FirebaseSignalingManager().createCall(
            callId = callId,
            callerUid = caller.uid,
            calleeUid = calleeUserId
        )

        //Launch VoIP call UI
        val intent = Intent(context, CallInProgressActivity::class.java).apply {
            putExtra(IncomingCallIntent.EXTRA_CALL_ID, callId)
            putExtra(IncomingCallIntent.EXTRA_IS_INCOMING, false)
            putExtra(IncomingCallIntent.EXTRA_REMOTE_USER_ID, calleeUserId)
        }
        context.startActivity(intent)
    }
}
