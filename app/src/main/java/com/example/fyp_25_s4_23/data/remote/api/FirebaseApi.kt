package com.example.fyp_25_s4_23.data.remote.api

import com.example.fyp_25_s4_23.data.remote.dto.FirebaseTokenResponse
import retrofit2.http.Header
import retrofit2.http.POST


interface FirebaseApi {
    @POST("/firebase/token")
    suspend fun getFirebaseToken(
        @Header("Authorization") authorization: String
    ): FirebaseTokenResponse
}