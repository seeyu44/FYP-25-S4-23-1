package com.example.fyp_25_s4_23.boundary.call

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

class CallManager(private val context: Context) {

    fun placeCall(number: String) {
        val uri = Uri.fromParts("tel", number, null)
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            telecom.placeCall(uri, BundleBuilder.autoAnswerExtras())
        } else {
            val intent = Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun requestDefaultDialer(activity: Activity) {
        val telecom = activity.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
        activity.startActivity(intent)
    }

    fun isDefaultDialer(): Boolean {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return telecom.defaultDialerPackage == context.packageName
    }

    fun openPhoneAccountSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        activity.startActivity(intent)
    }

    object BundleBuilder {
        fun autoAnswerExtras(): Bundle = Bundle().apply {
            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
        }
    }
}
