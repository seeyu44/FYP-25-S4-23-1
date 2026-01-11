package com.example.fyp_25_s4_23.control.call

import android.content.Context
import android.content.Intent
import com.example.fyp_25_s4_23.boundary.call.CallInProgressActivity

object IncomingCallIntent {

    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CALLER_ID = "extra_caller_id"
    const val EXTRA_IS_INCOMING = "extra_is_incoming"

    fun create(
        context: Context,
        callId: String,
        callerId: String,
        isIncoming: Boolean
    ): Intent {
        return Intent(context, CallInProgressActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_IS_INCOMING, isIncoming)
        }
    }
}
