package com.example.fyp_25_s4_23.control.call

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object IncomingCallListener {

    private var listener: ListenerRegistration? = null
    private var endedListener: ListenerRegistration? = null
    private val handledCalls = mutableSetOf<String>()

    fun start(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid == null) {
            Log.e("INCOMING_CALL", "Cannot start listener: user not logged in")
            return
        }

        Log.d("INCOMING_CALL", "Starting listener for uid=$uid")

        val db = FirebaseFirestore.getInstance()

        // Remove old listeners if any
        listener?.remove()
        endedListener?.remove()

        // Listen for ringing calls for this user
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

                    // Defensive logging: dump full snapshot data
                    Log.d("CALL_SIG", "Snapshot for callId=$callId data=${doc.data}")

                    // If fields are missing, attempt an explicit fetch (one-time) to help debugging
                    val callerId = doc.getString("caller_user_id")
                    if (callerId.isNullOrBlank()) {
                        Log.w("INCOMING_CALL", "Snapshot missing caller_user_id for $callId, fetching doc for debugging")
                        db.collection("calls").document(callId).get()
                            .addOnSuccessListener { fetched ->
                                Log.d("INCOMING_CALL", "Fetched doc $callId => ${fetched.data}")
                            }
                        continue
                    }

                    // Deduplicate notifications
                    if (handledCalls.contains(callId)) {
                        Log.d("INCOMING_CALL", "Call $callId already handled, skipping notification")
                        continue
                    }

                    Log.d(
                        "INCOMING_CALL",
                        "Incoming call detected: callId=$callId caller=$callerId"
                    )

                    // Mark as handled and show notification
                    handledCalls.add(callId)

                    IncomingCallNotifier.showIncomingCall(
                        context = context.applicationContext,
                        callId = callId,
                        callerId = callerId
                    )
                }
            }

        // Also listen for ended calls so we can cancel notifications and cleanup
        endedListener = db.collection("calls")
            .whereEqualTo("callee_user_id", uid)
            .whereEqualTo("status", "ended")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("INCOMING_CALL", "Ended listen failed", error)
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                for (doc in snapshots.documents) {
                    val callId = doc.id
                    Log.d("INCOMING_CALL", "Call ended observed for $callId, cancelling notification")
                    handledCalls.remove(callId)
                    IncomingCallNotifier.cancelNotification(context.applicationContext, callId)
                }
            }
    }

    fun stop() {
        Log.d("INCOMING_CALL", "Stopping listener")
        handledCalls.forEach { id ->
            try {
                IncomingCallNotifier.cancelNotification(null, id)
            } catch (e: Exception) {
                // ignore
            }
        }
        handledCalls.clear()
        listener?.remove()
        listener = null
        endedListener?.remove()
        endedListener = null
    }
}
