package com.example.fyp_25_s4_23.control.call

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.fyp_25_s4_23.control.call.ActiveCallStore
import com.google.firebase.firestore.DocumentChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.fyp_25_s4_23.entity.data.db.AppDatabase
import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.example.fyp_25_s4_23.domain.entities.ContactLabel
import com.example.fyp_25_s4_23.util.DisplayNameResolver


object IncomingCallListener {

    private var listener: ListenerRegistration? = null
    private var endedListener: ListenerRegistration? = null
    private val handledCalls = mutableSetOf<String>()
    private val scope = CoroutineScope(Dispatchers.IO)


    fun start(context: Context) {
        val database = AppDatabase.getInstance(context)
        val contactRepository = ContactRepository(database.contactDao())
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

                val activeCallId = ActiveCallStore.state.value?.callId

                for (change in snapshots.documentChanges) {
                    if (change.type != DocumentChange.Type.ADDED) continue

                    val doc = change.document

                    val callId = doc.id
                    val status = doc.getString("status") ?: continue
                    val callerId = doc.getString("caller_user_id") ?: continue
                    val callerUsername = doc.getString("caller_username") ?: callerId
                    val callerPhone = doc.getString("caller_phone") // Get phone from call document

                    if (callId == activeCallId) continue

                    Log.d("INCOMING_CALL", "callId=$callId status=$status caller=$callerId")

                    if (!handledCalls.contains(callId)) {
                        handledCalls.add(callId)

                        scope.launch {
                            // Resolve display name using the DisplayNameResolver utility
                            // Priority: saved contact name > phone number > callerUsername (email)
                            val resolvedDisplayName = DisplayNameResolver.resolveDisplayName(
                                contactRepository = contactRepository,
                                userId = callerId, // Use callerId (Firebase UID) to look up contact
                                fallbackName = callerUsername, // Use callerUsername (email) as last resort
                                fallbackPhone = callerPhone // Use phone from call document

                            when (contact?.label) {
                                ContactLabel.BLACK -> {
                                    Log.w(
                                        "INCOMING_CALL",
                                        "Blocked incoming call from BLACKLISTED user=$callerId callId=$callId"
                                    )

                                    // Do NOT show notification
                                    // Optional: mark call as ended
                                    FirebaseFirestore.getInstance()
                                        .collection("calls")
                                        .document(callId)
                                        .update(
                                            mapOf(
                                                "status" to "ended",
                                                "ended_reason" to "blocked_contact",
                                                "ended_by" to "callee"
                                            )
                                        )

                                    return@launch
                                }

                                else -> {
                                    // Allowed (WHITE or unknown)
                                    IncomingCallNotifier.showIncomingCall(
                                        context = context.applicationContext,
                                        callId = callId,
                                        callerId = callerId,
                                        displayName = resolvedDisplayName
                                    )
                                }
                            }
                        }

                    }
                }
            }


        endedListener = db.collection("calls")
            .whereEqualTo("callee_user_id", uid)
            .whereEqualTo("status", "ended")
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documents?.forEach { doc ->
                    handledCalls.remove(doc.id)
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
