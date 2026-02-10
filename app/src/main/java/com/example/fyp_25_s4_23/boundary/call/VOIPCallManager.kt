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
        calleeUserId: String,
        calleeDisplayName: String? = null,
        calleePhoneNumber: String? = null
    ) {
        val caller = FirebaseAuthManager.currentUser()
            ?: error("User not logged in")

        val callId = UUID.randomUUID().toString()
        val callerUsername = caller.displayName ?: caller.email ?: caller.uid
        val callerPhone = caller.phoneNumber


        FirebaseSignalingManager().createCall(
            callId = callId,
            callerUid = caller.uid,
            calleeUid = calleeUserId,
            callerUsername = callerUsername,
            callerPhone = callerPhone
        )

        val intent = Intent(context, CallInProgressActivity::class.java).apply {
            putExtra(IncomingCallIntent.EXTRA_CALL_ID, callId)
            putExtra(IncomingCallIntent.EXTRA_IS_INCOMING, false)
            putExtra(IncomingCallIntent.EXTRA_REMOTE_USER_ID, calleeUserId)
            if (calleeDisplayName != null) {
                putExtra(IncomingCallIntent.EXTRA_DISPLAY_NAME, calleeDisplayName)
            }
            if (calleePhoneNumber != null) {
                putExtra(IncomingCallIntent.EXTRA_PHONE_NUMBER, calleePhoneNumber)
            }
        }
        context.startActivity(intent)
    }
}
