package com.example.fyp_25_s4_23.entity.domain.entities

import android.util.Log
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Represents user information enriched from Firebase
 */
data class OtherUser(
    val userId: String = "",
    val displayName: String = "Unknown User",
    val phoneNumber: String? = null,
    val profilePictureUrl: String? = null
)

/**
 * Represents a call record from Firebase Cloud Function
 * Enriched with user details and metadata
 */
data class FirebaseCallRecord(
    val id: String = "",
    val callerUserId: String = "",
    val calleeUserId: String = "",
    val createdAt: Timestamp? = null,
    val endedAt: Timestamp? = null,
    val status: String = "unknown", // "ongoing", "completed", "missed", "declined"
    val duration: Long = 0L, // Duration in seconds
    val isCaller: Boolean = false, // Whether current user is the caller
    val otherUser: OtherUser = OtherUser(),
    val detectionScore: Double? = null, // Detection score for the callee
    val detectionTime: Timestamp? = null, // When detection was performed
    val isDeepfake: Boolean? = null // Whether deepfake was detected
) {
    /**
     * Check if this call is outgoing (current user is caller)
     */
    val isOutgoing: Boolean
        get() = isCaller

    /**
     * Get display name for the call contact
     */
    fun getContactName(): String = otherUser.displayName

    /**
     * Get the timestamp in milliseconds
     */
    fun getCreatedAtMillis(): Long = createdAt?.toDate()?.time ?: 0

    /**
     * Format created timestamp for display
     */
    fun getCreatedAtFormatted(): String {
        // Use same extraction method as SummaryScreen
        val firebaseDate = createdAt?.toDate()
        if (firebaseDate == null) {
            Log.d("FirebaseCallRecord", "getCreatedAtFormatted: createdAt is null for call $id")
            return "Unknown"
        }
        
        // Extract date using Calendar like summary page does
        val cal = java.util.Calendar.getInstance()
        cal.time = firebaseDate
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) // 0-indexed
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        
        // Reconstruct date from calendar
        cal.set(year, month, day, hour, minute, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val date = cal.time
        
        Log.d("FirebaseCallRecord", "getCreatedAtFormatted for call $id: year=$year, month=$month, day=$day, hour=$hour, minute=$minute")
        Log.d("FirebaseCallRecord", "  firebaseDate=$firebaseDate, reconstructed date=$date, millis=${date.time}")
        
        val mills = date.time
        val now = System.currentTimeMillis()
        val diffMs = now - mills
        val diffMins = diffMs / (1000 * 60)
        val diffHours = diffMs / (1000 * 60 * 60)
        val diffDays = diffMs / (1000 * 60 * 60 * 24)
        
        return when {
            diffMins < 1 -> "Just now"
            diffMins < 60 -> "$diffMins min ago"
            diffHours < 24 -> "$diffHours hour${if (diffHours > 1) "s" else ""} ago"
            diffDays < 7 -> "$diffDays day${if (diffDays > 1) "s" else ""} ago"
            else -> {
                val format = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                format.format(date)
            }
        }
    }

    /**
     * Check if call is completed
     */
    fun isCompleted(): Boolean = status == "completed"

    /**
     * Format duration as readable string
     */
    fun getDurationString(): String {
        if (duration <= 0) return "0s"
        
        val hours = duration / 3600
        val minutes = (duration % 3600) / 60
        val seconds = duration % 60

        return when {
            hours > 0 -> String.format("%dh %dm", hours, minutes)
            minutes > 0 -> String.format("%dm %ds", minutes, seconds)
            else -> String.format("%ds", seconds)
        }
    }
}

/**
 * Response from getCallHistory Cloud Function
 */
data class CallHistoryResponse(
    val calls: List<FirebaseCallRecord> = emptyList(),
    val hasMore: Boolean = false
)
