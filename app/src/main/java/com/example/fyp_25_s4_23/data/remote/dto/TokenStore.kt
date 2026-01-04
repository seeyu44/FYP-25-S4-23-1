package com.example.fyp_25_s4_23.data.remote.dto
import android.content.Context

class TokenStore(context: Context){
    private val prefs = context.getSharedPreferences("auth_prefs",Context.MODE_PRIVATE)

    fun save(token:String){
        prefs.edit()
            .putString("JWT_Token",token)
            .apply()
    }

    fun get_token() : String? {
        return prefs.getString("JWT_Token",null)
    }

    fun remove_token(){
        prefs.edit()
            .remove("JWT_Token")
            .apply()
    }

}

