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
            val userId = call.otherUser.userId
            val fallbackName = call.otherUser.displayName
            val fallbackPhone = call.otherUser.phoneNumber
            
            // Resolve display name from local contacts
            val resolvedDisplayName = DisplayNameResolver.resolveDisplayName(
                contactRepository = contactRepository,
                userId = userId,
                fallbackName = fallbackName,
                fallbackPhone = fallbackPhone
            )
            
            // Resolve phone number from local contacts
            val resolvedPhone = DisplayNameResolver.resolvePhoneNumber(
                contactRepository = contactRepository,
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
            
            Log.d("CallHistoryRepository", "=== Parsing call $callId ===")
            Log.d("CallHistoryRepository", "  currentUid: $currentUid, calleeUid: $calleeUid")
            Log.d("CallHistoryRepository", "  RAW created_at: ${data["created_at"]} (type: ${data["created_at"]?.javaClass})")
            Log.d("CallHistoryRepository", "  RAW ended_at: ${data["ended_at"]} (type: ${data["ended_at"]?.javaClass})")
            Log.d("CallHistoryRepository", "  Available keys: ${data.keys}")
            
            // Use current user's UID for incoming calls (where current user IS the callee)
            val uidForDetection = currentUid ?: calleeUid
            
            val detectionScore = if (uidForDetection != null) {
                val scoreKey = "${uidForDetection}_detection_score"
                val score = (data[scoreKey] as? Number)?.toDouble()
                Log.d("CallHistoryRepository", "  Looking for '$scoreKey': $score")
                score
            } else null
            
            val detectionTime = if (uidForDetection != null) {
                val timestampKey = "${uidForDetection}_detection_timestamp"
                val timestampMillis = (data[timestampKey] as? Number)?.toLong()
                Log.d("CallHistoryRepository", "  Looking for '$timestampKey': $timestampMillis")
                if (timestampMillis != null) {
                    com.google.firebase.Timestamp(timestampMillis / 1000, ((timestampMillis % 1000) * 1000000).toInt())
                } else null
            } else null
            
            val isDeepfake = if (uidForDetection != null) {
                val deepfakeKey = "${uidForDetection}_is_deepfake"
                val deepfake = data[deepfakeKey] as? Boolean
                Log.d("CallHistoryRepository", "  Looking for '$deepfakeKey': $deepfake")
                deepfake
            } else null

            // Convert created_at - could be Timestamp object, number (milliseconds or seconds), or Map with _seconds/_nanoseconds
            val createdAt = when (val createdAtData = data["created_at"]) {
                is com.google.firebase.Timestamp -> createdAtData
                is Number -> {
                    val numValue = createdAtData.toLong()
                    Log.d("CallHistoryRepository", "  created_at is Number: $numValue")
                    // Timestamps in milliseconds are >= 1000000000000 (13 digits, year 2001+)
                    // Timestamps in seconds are < 10000000000 (11 digits, year 2286)
                    // Current time: ~1738000000 seconds or ~1738000000000 milliseconds
                    if (numValue >= 1000000000000L) {
                        // It's in milliseconds, convert to seconds
                        Log.d("CallHistoryRepository", "  Treating as milliseconds, converting to seconds")
                        com.google.firebase.Timestamp(numValue / 1000, ((numValue % 1000) * 1000000).toInt())
                    } else {
                        // It's already in seconds
                        Log.d("CallHistoryRepository", "  Treating as seconds")
                        com.google.firebase.Timestamp(numValue, 0)
                    }
                }
                is Map<*, *> -> {
                    // Handle Firestore Timestamp serialized as Map
                    val seconds = (createdAtData["_seconds"] as? Number)?.toLong()
                    val nanoseconds = (createdAtData["_nanoseconds"] as? Number)?.toInt() ?: 0
                    Log.d("CallHistoryRepository", "  created_at is Map: seconds=$seconds, nanoseconds=$nanoseconds")
                    if (seconds != null) {
                        com.google.firebase.Timestamp(seconds, nanoseconds)
                    } else {
                        Log.w("CallHistoryRepository", "  Map created_at missing _seconds: $createdAtData")
                        null
                    }
                }
                else -> {
                    Log.w("CallHistoryRepository", "  Unknown created_at type: ${createdAtData?.javaClass}, value: $createdAtData")
                    null
                }
            }
            
            val endedAt = when (val endedAtData = data["ended_at"]) {
                is com.google.firebase.Timestamp -> endedAtData
                is Number -> {
                    val numValue = endedAtData.toLong()
                    // If the number is very large, it's likely in milliseconds, convert to seconds
                    if (numValue >= 1000000000000L) {
                        com.google.firebase.Timestamp(numValue / 1000, ((numValue % 1000) * 1000000).toInt())
                    } else {
                        com.google.firebase.Timestamp(numValue, 0)
                    }
                }
                is Map<*, *> -> {
                    val seconds = (endedAtData["_seconds"] as? Number)?.toLong()
                    val nanoseconds = (endedAtData["_nanoseconds"] as? Number)?.toInt() ?: 0
                    if (seconds != null) com.google.firebase.Timestamp(seconds, nanoseconds) else null
                }
                else -> null
            }
            
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
                isCaller = data["is_caller"] as? Boolean ?: false,
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
}
