package com.example.fyp_25_s4_23.domain.entities

import com.example.fyp_25_s4_23.domain.valueobjects.CallDirection
import com.example.fyp_25_s4_23.domain.valueobjects.LocationTag

/**
 * Describes contextual information about a call independent of detections.
 */
data class CallMetadata(
    val phoneNumber: String,
    val displayName: String? = null,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val direction: CallDirection = CallDirection.UNKNOWN,
    val location: LocationTag? = null,
    val carrier: String? = null,
    val callTypeLabel: String? = null
) {
    val durationMillis: Long?
        get() = endTimeMillis?.let { end ->
            val duration = end - startTimeMillis
            if (duration >= 0) duration else null
        }
}

