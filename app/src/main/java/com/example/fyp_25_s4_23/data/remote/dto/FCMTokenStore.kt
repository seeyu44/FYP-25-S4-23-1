package com.example.fyp_25_s4_23.data.remote.dto
import android.content.Context

class FCMTokenStore(context: Context){
    private val prefs = context.getSharedPreferences("auth_prefs",Context.MODE_PRIVATE)

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

