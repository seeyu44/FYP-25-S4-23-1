package com.example.fyp_25_s4_23.data.remote.firebase
import com.google.firebase.ktx.Firebase
import com.google.firebase.functions.ktx.functions
import kotlinx.coroutines.tasks.await


class UsernameService {

    private val functions = Firebase.functions

    suspend fun checkUsername(username: String): Boolean {
        val result = functions
            .getHttpsCallable("checkUsernameAvailable")
            .call(mapOf("username" to username))
            .await()

        return result.data
            .let { it as Map<*, *> }
            .get("available") as Boolean
    }

    suspend fun claimUsername(username: String) {
        functions
            .getHttpsCallable("claimUsername")
            .call(mapOf("username" to username))
            .await()
    }
}