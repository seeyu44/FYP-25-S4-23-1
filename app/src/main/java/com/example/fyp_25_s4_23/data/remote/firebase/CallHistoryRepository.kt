package com.example.fyp_25_s4_23.data.remote.firebase

import android.util.Log
import com.example.fyp_25_s4_23.entity.domain.entities.CallHistoryResponse
import com.example.fyp_25_s4_23.entity.domain.entities.FirebaseCallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.OtherUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.example.fyp_25_s4_23.util.DisplayNameResolver

/**
 * Repository for fetching call history from Firebase Cloud Functions
 */
class CallHistoryRepository(private val contactRepository: ContactRepository? = null) {
    private val functions = FirebaseFunctions.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Fetch call history for the current authenticated user
     * 
     * @param limit Maximum number of calls to return (default: 50)
     * @return CallHistoryResponse containing list of calls and pagination info
     * @throws Exception if the cloud function call fails
     */
    suspend fun getCallHistory(limit: Int = 50): CallHistoryResponse {
        return try {
            val getCallHistory = functions.getHttpsCallable("getCallHistory")
            val data = hashMapOf("limit" to limit)
            
            val result = getCallHistory.call(data).await()
            val response = result.data as? Map<*, *>

            if (response != null) {
                val callsData = response["calls"] as? List<*> ?: emptyList<Any>()
                val hasMore = response["hasMore"] as? Boolean ?: false

                val calls = callsData.mapNotNull { callData ->
                    parseFirebaseCallRecord(callData)
                }
                
                // Enrich with local contact information if repository is available
                val enrichedCalls = if (contactRepository != null) {
                    calls.map { call -> enrichCallWithLocalContact(call) }
                } else {
                    calls
                }

                CallHistoryResponse(calls = enrichedCalls, hasMore = hasMore)
            } else {
                CallHistoryResponse()
            }
        } catch (e: Exception) {
            Log.e("CallHistoryRepository", "Error fetching call history: ${e.message}", e)
            e.printStackTrace()
            throw e
        }
    }

    /**
     * End a call and update its status in Firebase
     * 
     * @param callId The ID of the call to end
     * @param duration Duration of the call in seconds
     * @param status Status of the call (default: "completed")
     * @throws Exception if the cloud function call fails
     */
    suspend fun endCall(
        callId: String,
        duration: Long,
        status: String = "completed"
    ): Boolean {
        return try {
            val endCall = functions.getHttpsCallable("endCall")
            val data = hashMapOf(
                "callId" to callId,
                "duration" to duration,
                "status" to status
            )

            val result = endCall.call(data).await()
            val response = result.data as? Map<*, *>
            response?.get("success") as? Boolean ?: false
        } catch (e: Exception) {
            Log.e("CallHistoryRepository", "Error ending call", e)
            throw e
        }
    }

    /**
     * Enrich a Firebase call record with local contact information
     */
    private suspend fun enrichCallWithLocalContact(call: FirebaseCallRecord): FirebaseCallRecord {
        if (contactRepository == null) return call
        
        try {
            val currentUserId = auth.currentUser?.uid ?: return call
            val userId = call.otherUser.userId
            val fallbackName = call.otherUser.displayName
            val fallbackPhone = call.otherUser.phoneNumber
            
            // Resolve display name from local contacts
            val resolvedDisplayName = DisplayNameResolver.resolveDisplayName(
                contactRepository = contactRepository,
                currentUserId = currentUserId,
                userId = userId,
                fallbackName = fallbackName,
                fallbackPhone = fallbackPhone
            )
            
            // Resolve phone number from local contacts
            val resolvedPhone = DisplayNameResolver.resolvePhoneNumber(
                contactRepository = contactRepository,
                currentUserId = currentUserId,
                userId = userId,
                fallbackPhone = fallbackPhone
            )
            
            // Update the otherUser with resolved information
            val enrichedOtherUser = call.otherUser.copy(
                displayName = resolvedDisplayName,
                phoneNumber = resolvedPhone ?: call.otherUser.phoneNumber
            )
            
            return call.copy(otherUser = enrichedOtherUser)
        } catch (e: Exception) {
            Log.e("CallHistoryRepository", "Error enriching call with local contact", e)
            return call
        }
    }
    
    /**
     * Parse Firebase response data into FirebaseCallRecord object
     */
    private fun parseFirebaseCallRecord(data: Any?): FirebaseCallRecord? {
        return try {
            if (data !is Map<*, *>) return null

            val otherUserData = data["other_user"] as? Map<*, *>
            val otherUser = if (otherUserData != null) {
                OtherUser(
                    userId = otherUserData["userId"] as? String ?: "",
                    displayName = otherUserData["displayName"] as? String ?: "Unknown User",
                    phoneNumber = otherUserData["phoneNumber"] as? String,
                    profilePictureUrl = otherUserData["profilePictureUrl"] as? String
                )
            } else {
                OtherUser()
            }

            // Extract UID-prefixed detection fields
            // Detection fields use the callee's UID as prefix
            // For incoming calls, current user = callee, so we use current user's UID
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            val callId = data["id"] as? String ?: "unknown"
            val calleeUid = data["callee_user_id"] as? String
            val isCaller = data["is_caller"] as? Boolean ?: false
            
            Log.d("CallHistoryRepository", "=== Parsing call $callId ===")
            Log.d("CallHistoryRepository", "  currentUid: $currentUid, calleeUid: $calleeUid")
            Log.d("CallHistoryRepository", "  RAW created_at: ${data["created_at"]} (type: ${data["created_at"]?.javaClass})")
            Log.d("CallHistoryRepository", "  RAW ended_at: ${data["ended_at"]} (type: ${data["ended_at"]?.javaClass})")
            Log.d("CallHistoryRepository", "  Available keys: ${data.keys}")
            
            // For outgoing calls, detection fields are on the callee UID; for incoming, use current user.
            val uidForDetection = if (isCaller) calleeUid else currentUid
            
            val detectionScore = if (uidForDetection != null) {
                val highestScoreKey = "${uidForDetection}_highest_detection_score"
                val latestScoreKey = "${uidForDetection}_detection_score"
                val score = (data[highestScoreKey] as? Number)?.toDouble()
                    ?: (data[latestScoreKey] as? Number)?.toDouble()
                Log.d("CallHistoryRepository", "  Looking for '$highestScoreKey'/'$latestScoreKey': $score")
                score
            } else null
            
            val detectionTime = if (uidForDetection != null) {
                val highestTimestampKey = "${uidForDetection}_highest_detection_timestamp"
                val latestTimestampKey = "${uidForDetection}_detection_timestamp"
                val timestampMillis = (data[highestTimestampKey] as? Number)?.toLong()
                    ?: (data[latestTimestampKey] as? Number)?.toLong()
                Log.d("CallHistoryRepository", "  Looking for '$highestTimestampKey'/'$latestTimestampKey': $timestampMillis")
                if (timestampMillis != null) {
                    com.google.firebase.Timestamp(timestampMillis / 1000, ((timestampMillis % 1000) * 1000000).toInt())
                } else null
            } else null
            
            val isDeepfake = if (uidForDetection != null) {
                val highestDeepfakeKey = "${uidForDetection}_highest_is_deepfake"
                val latestDeepfakeKey = "${uidForDetection}_is_deepfake"
                val deepfake = (data[highestDeepfakeKey] as? Boolean)
                    ?: (data[latestDeepfakeKey] as? Boolean)
                Log.d(
                    "CallHistoryRepository",
                    "  Looking for '$highestDeepfakeKey'/'$latestDeepfakeKey': $deepfake"
                )
                deepfake
            } else null

            // Robust timestamp extraction - same logic as summary page
            // Tries: created_at -> detection_timestamp -> current time
            val createdAt = extractRobustTimestamp(data, "created_at", uidForDetection)
            
            val endedAt = extractRobustTimestamp(data, "ended_at", null)
                ?: extractRobustTimestamp(data, "updated_at", null)
                ?: detectionTime
            
            Log.d("CallHistoryRepository", "  PARSED created_at: $createdAt")
            Log.d("CallHistoryRepository", "  created_at.toDate(): ${createdAt?.toDate()}")
            Log.d("CallHistoryRepository", "  created_at millis: ${createdAt?.toDate()?.time}")
            Log.d("CallHistoryRepository", "  created_at seconds: ${createdAt?.seconds}")

            FirebaseCallRecord(
                id = data["id"] as? String ?: "",
                callerUserId = data["caller_user_id"] as? String ?: "",
                calleeUserId = data["callee_user_id"] as? String ?: "",
                createdAt = createdAt,
                endedAt = endedAt,
                status = data["status"] as? String ?: "unknown",
                duration = (data["duration"] as? Number)?.toLong() ?: 0L,
                isCaller = isCaller,
                otherUser = otherUser,
                detectionScore = detectionScore,
                detectionTime = detectionTime,
                isDeepfake = isDeepfake
            )
        } catch (e: Exception) {
            Log.e("CallHistoryRepository", "Error parsing call record", e)
            null
        }
    }
    
    /**
     * Robustly extract timestamp from Firebase data - same logic as summary page
     * Handles: Timestamp objects, Numbers (millis/seconds), Maps with _seconds/seconds
     * Falls back to detection_timestamp if available and primary field is invalid
     * 
     * Mimics TypeScript getMillis() and timestampToSeconds() functions
     */
    private fun extractRobustTimestamp(
        data: Map<*, *>,
        fieldName: String,
        uidForDetection: String?
    ): com.google.firebase.Timestamp? {
        return try {
            // Try primary field first
            val primaryValue = data[fieldName]
            val primaryMillis = getMillisFromValue(primaryValue)
            
            // For answered calls: Validate timestamp is reasonable and use detection_timestamp as fallback
            // For missed calls: Use whatever timestamp we have (even if invalid)
            if (uidForDetection != null) {
                // Check if detection_timestamp exists (indicates answered call)
                val detectionKey = "${uidForDetection}_detection_timestamp"
                val detectionValue = data[detectionKey]
                
                if (detectionValue != null) {
                    // Answered call - prefer detection_timestamp if primary is invalid
                    // Valid timestamps should be > 946684800000 (Jan 1, 2000)
                    if (primaryMillis > 946684800000L) {
                        Log.d("CallHistoryRepository", "  ✓ Valid $fieldName: $primaryMillis ms")
                        return com.google.firebase.Timestamp(
                            primaryMillis / 1000,
                            ((primaryMillis % 1000) * 1000000).toInt()
                        )
                    } else {
                        // Primary invalid, use detection_timestamp
                        val detectionMillis = getMillisFromValue(detectionValue)
                        Log.w("CallHistoryRepository", "  ✗ Invalid $fieldName ($primaryMillis ms), using $detectionKey ($detectionMillis ms)")
                        return com.google.firebase.Timestamp(
                            detectionMillis / 1000,
                            ((detectionMillis % 1000) * 1000000).toInt()
                        )
                    }
                }
            }
            
            // Missed call or no detection timestamp: use primary value as-is
            // Even if it's wrong (1970s), it's better than showing current time
            if (primaryMillis > 0) {
                Log.d("CallHistoryRepository", "  Using $fieldName as-is: $primaryMillis ms")
                return com.google.firebase.Timestamp(
                    primaryMillis / 1000,
                    ((primaryMillis % 1000) * 1000000).toInt()
                )
            }
            
            Log.w("CallHistoryRepository", "  ✗ No timestamp data found for $fieldName")
            null
        } catch (e: Exception) {
            Log.e("CallHistoryRepository", "Error extracting timestamp for $fieldName", e)
            null
        }
    }
    
    /**
     * Extract milliseconds from various timestamp formats
     * Matches TypeScript getMillis() function logic
     */
    private fun getMillisFromValue(value: Any?): Long {
        if (value == null) return 0L
        
        return when (value) {
            // Native Firebase Timestamp
            is com.google.firebase.Timestamp -> value.toDate().time
            
            // Number (could be milliseconds or seconds)
            is Number -> {
                val numValue = value.toLong()
                // Timestamps >= 1000000000000 are milliseconds (13+ digits)
                // Timestamps < 1000000000000 are seconds (< 13 digits)
                if (numValue >= 1000000000000L) {
                    numValue // Already milliseconds
                } else {
                    numValue * 1000 // Convert seconds to milliseconds
                }
            }
            
            // Map with seconds/nanoseconds (serialized Timestamp)
            is Map<*, *> -> {
                val seconds = (value["seconds"] as? Number)?.toLong()
                    ?: (value["_seconds"] as? Number)?.toLong()
                    ?: 0L
                seconds * 1000
            }
            
            // Try parsing as date string
            else -> {
                try {
                    java.util.Date(value.toString()).time
                } catch (e: Exception) {
                    0L
                }
            }
        }
    }
}
