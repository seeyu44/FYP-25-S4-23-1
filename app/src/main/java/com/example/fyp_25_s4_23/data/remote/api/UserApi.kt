package com.example.fyp_25_s4_23.data.remote.api

import com.example.fyp_25_s4_23.data.remote.dto.UserProfile
import com.example.fyp_25_s4_23.data.remote.dto.FCMTokenRequest
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header
import retrofit2.http.Body

interface UserApi {

    @GET("/users/me")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String
    ): UserProfile

    @POST("/users/fcm-token")
    suspend fun registerFCMToken(
        @Header("Authorization") authorization: String,
        @Body request: FCMTokenRequest)
}