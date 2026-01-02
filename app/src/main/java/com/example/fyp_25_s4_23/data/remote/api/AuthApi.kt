package com.example.fyp_25_s4_23.data.remote.api

import retrofit2.http.Body
import retrofit2.http.POST

import com.example.fyp_25_s4_23.data.remote.dto.LoginRequest
import com.example.fyp_25_s4_23.data.remote.dto.LoginResponse



interface AuthApi {
    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}