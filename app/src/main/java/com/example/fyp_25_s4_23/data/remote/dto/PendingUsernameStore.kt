package com.example.fyp_25_s4_23.data.remote.dto
import android.content.Context

class PendingUsernameStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("pending_username", Context.MODE_PRIVATE)

    fun save(username: String) {
        prefs.edit()
            .putString("username", username)
            .apply()
    }

    fun get(): String? =
        prefs.getString("username", null)

    fun clear() {
        prefs.edit()
            .remove("username")
            .apply()
    }
}