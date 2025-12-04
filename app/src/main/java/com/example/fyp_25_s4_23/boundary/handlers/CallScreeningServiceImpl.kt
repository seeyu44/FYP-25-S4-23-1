package com.example.fyp_25_s4_23.boundary.handlers

import android.os.Build
import android.telecom.Call.Details
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi

/**
 * Optional call screening hook (requires Call Screening role or default dialer).
 * Currently a no-op that allows all calls.
 */
@RequiresApi(Build.VERSION_CODES.N)
class CallScreeningServiceImpl : CallScreeningService() {
    override fun onScreenCall(p0: Details) {
        // TODO: integrate metadata checks, then allow/disallow as needed
        respondToCall(p0, CallResponse.Builder().setDisallowCall(false).build())
    }
}

