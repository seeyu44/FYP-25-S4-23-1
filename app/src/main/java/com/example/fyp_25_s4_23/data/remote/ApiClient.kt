package com.example.fyp_25_s4_23.data.remote

import com.example.fyp_25_s4_23.data.remote.api.AuthApi
import com.example.fyp_25_s4_23.data.remote.api.UserApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private const val BASE_URL = "https://dg-backend-2j05.onrender.com"


    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val authApi: AuthApi by lazy {
        retrofit.create(AuthApi::class.java)
    }

    val userApi: UserApi by lazy {
        retrofit.create(UserApi::class.java)
    }
}
