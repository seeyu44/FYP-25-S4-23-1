package com.example.fyp_25_s4_23.data.remote.api

import com.example.fyp_25_s4_23.data.remote.dto.UserProfileResponse
import retrofit2.http.GET
import retrofit2.http.Header

interface UserApi {

    @GET("/users/me")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String
    ): UserProfileResponse
}