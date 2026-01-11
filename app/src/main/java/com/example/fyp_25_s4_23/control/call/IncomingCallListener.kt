package com.example.fyp_25_s4_23.control.call

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object IncomingCallListener {

    private var listener: ListenerRegistration? = null

    fun start(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return

        val db = FirebaseFirestore.getInstance()

        listener?.remove()

        listener = db.collection("calls")
            .whereEqualTo("callee_user_id", uid)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("IncomingCallListener", "Listen failed", error)
                    return@addSnapshotListener
                }

                snapshots?.documents?.forEach { doc ->
                    val callId = doc.id
                    val callerId = doc.getString("caller_user_id") ?: return@forEach

                    Log.d("IncomingCallListener", "Incoming call detected: $callId")

                    IncomingCallNotifier.showIncomingCall(
                        context = context.applicationContext,
                        callId = callId,
                        callerId = callerId
                    )
                }
            }
    }

    fun stop() {
        listener?.remove()
        listener = null
    }
}

