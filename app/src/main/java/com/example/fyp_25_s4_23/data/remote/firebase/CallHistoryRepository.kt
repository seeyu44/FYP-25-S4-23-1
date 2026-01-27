package com.example.fyp_25_s4_23.data.remote.firebase

import android.util.Log
import com.example.fyp_25_s4_23.entity.domain.entities.CallHistoryResponse
import com.example.fyp_25_s4_23.entity.domain.entities.FirebaseCallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.OtherUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.httpsCallable
import kotlinx.coroutines.tasks.await

/**
 * Repository for fetching call history from Firebase Cloud Functions
 */
class CallHistoryRepository {
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

                CallHistoryResponse(calls = calls, hasMore = hasMore)
            } else {
                CallHistoryResponse()
            }
        } catch (e: Exception) {
            Log.e("CallHistoryRepository", "Error fetching call history", e)
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

            FirebaseCallRecord(
                id = data["id"] as? String ?: "",
                callerUserId = data["caller_user_id"] as? String ?: "",
                calleeUserId = data["callee_user_id"] as? String ?: "",
                createdAt = data["created_at"] as? com.google.firebase.Timestamp,
                endedAt = data["ended_at"] as? com.google.firebase.Timestamp,
                status = data["status"] as? String ?: "unknown",
                duration = (data["duration"] as? Number)?.toLong() ?: 0L,
                isCaller = data["is_caller"] as? Boolean ?: false,
                otherUser = otherUser
            )
        } catch (e: Exception) {
            Log.e("CallHistoryRepository", "Error parsing call record", e)
            null
        }
    }
}
