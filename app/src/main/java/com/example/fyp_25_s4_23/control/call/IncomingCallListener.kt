package com.example.fyp_25_s4_23.control.call

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object IncomingCallListener {

    private var listener: ListenerRegistration? = null
    private val handledCalls = mutableSetOf<String>()

    fun start(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid == null) {
            Log.e("INCOMING_CALL", "Cannot start listener: user not logged in")
            return
        }

        Log.d("INCOMING_CALL", "Starting listener for uid=$uid")

        val db = FirebaseFirestore.getInstance()

        // Remove old listener if any
        listener?.remove()

        listener = db.collection("calls")
            .whereEqualTo("callee_user_id", uid) // MUST be Firebase UID
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("INCOMING_CALL", "Listen failed", error)
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) {
                    Log.d("INCOMING_CALL", "No incoming calls")
                    return@addSnapshotListener
                }

                for (doc in snapshots.documents) {
                    val callId = doc.id
                    val callerId = doc.getString("caller_user_id") ?: continue

                    Log.d(
                        "INCOMING_CALL",
                        "Incoming call detected: callId=$callId caller=$callerId"
                    )

                    IncomingCallNotifier.showIncomingCall(
                        context = context.applicationContext,
                        callId = callId,
                        callerId = callerId
                    )
                }
            }
    }

    fun stop() {
        Log.d("INCOMING_CALL", "Stopping listener")
        handledCalls.clear()
        listener?.remove()
        listener = null
    }
}
