package com.example.fyp_25_s4_23.data.remote.dto
import android.content.Context

class TokenStore(context: Context){
    private val prefs = context.getSharedPreferences("auth_prefs",Context.MODE_PRIVATE)

    fun saveJWT(token:String){
        prefs.edit()
            .putString("JWT_Token",token)
            .apply()
    }

    fun get_JWTToken() : String? {
        return prefs.getString("JWT_Token",null)
    }

    fun remove_JWTToken(){
        prefs.edit()
            .remove("JWT_Token")
            .apply()
    }

    fun saveFCMToken(token:String){
        prefs.edit()
            .putString("FCM_Token",token)
            .apply()
    }

    fun get_FCMToken() : String? {
        return prefs.getString("FCM_Token",null)
    }

    fun remove_FCMToken(){
        prefs.edit()
            .remove("FCM_Token")
            .apply()
    }
}

