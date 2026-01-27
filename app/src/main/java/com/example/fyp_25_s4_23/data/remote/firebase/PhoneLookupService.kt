package com.example.fyp_25_s4_23.data.remote.firebase

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

    /**
     * Look up a user by their phone number.
     * Returns the user's uid, username, and display name.
     * Only returns users with verified phone numbers.
     */
    suspend fun getUserByPhoneNumber(phoneNumber: String): PhoneLookupResult {
        val getUserByPhone = functions.getHttpsCallable("getUserByPhoneNumber")
        
        val data = hashMapOf(
            "phoneNumber" to phoneNumber
        )
        
        val result = getUserByPhone.call(data).await()
        val resultData = result.data as? Map<*, *> 
            ?: throw IllegalStateException("Invalid response from server")
        
        return PhoneLookupResult(
            uid = resultData["uid"] as? String 
                ?: throw IllegalStateException("Missing uid in response"),
            username = resultData["username"] as? String 
                ?: throw IllegalStateException("Missing username in response"),
            displayName = resultData["displayName"] as? String 
                ?: throw IllegalStateException("Missing displayName in response")
        )
    }
}
