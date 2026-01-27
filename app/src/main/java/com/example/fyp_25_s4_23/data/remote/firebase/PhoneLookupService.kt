package com.example.fyp_25_s4_23.data.remote.firebase

import android.util.Log
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

data class PhoneLookupResult(
    val uid: String,
    val username: String,
    val displayName: String
)

class PhoneLookupService {
    private val functions = Firebase.functions
    private val TAG = "PhoneLookupService"

    /**
     * Look up a user by their phone number.
     * Returns the user's uid, username, and display name.
     * Only returns users with verified phone numbers.
     */
    suspend fun getUserByPhoneNumber(phoneNumber: String): PhoneLookupResult {
        try {
            Log.d(TAG, "Looking up phone number: $phoneNumber")
            
            val getUserByPhone = functions.getHttpsCallable("getUserByPhoneNumber")
            
            val data = hashMapOf(
                "phoneNumber" to phoneNumber
            )
            
            val result = getUserByPhone.call(data).await()
            val resultData = result.data as? Map<*, *> 
                ?: throw IllegalStateException("Invalid response from server")
            
            Log.d(TAG, "Successfully found user for phone number")
            
            return PhoneLookupResult(
                uid = resultData["uid"] as? String 
                    ?: throw IllegalStateException("Missing uid in response"),
                username = resultData["username"] as? String 
                    ?: throw IllegalStateException("Missing username in response"),
                displayName = resultData["displayName"] as? String 
                    ?: throw IllegalStateException("Missing displayName in response")
            )
        } catch (e: FirebaseFunctionsException) {
            Log.e(TAG, "Firebase function error: ${e.code} - ${e.message}")
            // Extract the error message from Firebase Functions
            throw Exception(e.message ?: "Failed to look up phone number")
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up phone number", e)
            throw e
        }
    }
}
