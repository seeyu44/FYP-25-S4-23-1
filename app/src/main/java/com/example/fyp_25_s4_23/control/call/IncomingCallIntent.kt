package com.example.fyp_25_s4_23.control.call

import android.content.Context
import android.content.Intent
import com.example.fyp_25_s4_23.boundary.call.CallInProgressActivity

object IncomingCallIntent {

    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CALLER_ID = "extra_caller_id"
    const val EXTRA_IS_INCOMING = "extra_is_incoming"
    const val EXTRA_REMOTE_USER_ID = "extra_remote_user_id"

    const val EXTRA_DISPLAY_NAME = "extra_display_name"
    const val EXTRA_PHONE_NUMBER = "extra_phone_number"
    const val EXTRA_USERNAME = "extra_username"

    fun create(
        context: Context,
        callId: String,
        callerId: String,
        displayName: String,
        phoneNumber: String?,
        username: String? = null,
        isIncoming: Boolean
    ): Intent {
        return Intent(context, CallInProgressActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_REMOTE_USER_ID, callerId)
            putExtra(EXTRA_DISPLAY_NAME, displayName)
            if (!phoneNumber.isNullOrBlank()) {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            }
            if (!username.isNullOrBlank()) {
                putExtra(EXTRA_USERNAME, username)
            }
            putExtra(EXTRA_IS_INCOMING, isIncoming)
        }
    }
}
