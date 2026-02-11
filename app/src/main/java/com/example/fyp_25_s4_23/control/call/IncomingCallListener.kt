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
import kotlinx.coroutines.tasks.await
import com.example.fyp_25_s4_23.entity.data.db.AppDatabase
import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.example.fyp_25_s4_23.util.DisplayNameResolver
import com.example.fyp_25_s4_23.data.remote.firebase.GlobalBlockRepository


object IncomingCallListener {

    private var listener: ListenerRegistration? = null
    private var endedListener: ListenerRegistration? = null
    private val handledCalls = mutableSetOf<String>()
    private val scope = CoroutineScope(Dispatchers.IO)


    fun start(context: Context) {
        val database = AppDatabase.getInstance(context)
        val contactRepository = ContactRepository(database.contactDao())
        val globalBlockRepository = GlobalBlockRepository()
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
        
        // CRITICAL: Clear old handled calls on fresh start to avoid showing old notifications
        handledCalls.clear()

        // Listen for ringing calls for this user (filter by timestamp in code to avoid composite index)
        val fiveMinutesAgo = System.currentTimeMillis() / 1000 - (5 * 60)
        
        listener = db.collection("calls")
            .whereEqualTo("callee_user_id", uid)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("INCOMING_CALL", "Listen failed (Ask Gemini)", error)
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
                    
                    // Filter by timestamp in code to avoid composite index requirement
                    val createdAtSeconds = when (val createdAtValue = doc.get("created_at")) {
                        is com.google.firebase.Timestamp -> createdAtValue.seconds
                        is Number -> {
                            val num = createdAtValue.toLong()
                            if (num >= 1_000_000_000_000L) num / 1000 else num
                        }
                        else -> null
                    } ?: continue
                    if (createdAtSeconds < fiveMinutesAgo) {
                        Log.d(
                            "INCOMING_CALL",
                            "Skipping old call (created_at=$createdAtSeconds is before $fiveMinutesAgo)"
                        )
                        continue
                    }

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
                                currentUserId = uid,
                                userId = callerId, // Use callerId (Firebase UID) to look up contact
                                fallbackName = callerUsername, // Use callerUsername (email) as last resort
                                fallbackPhone = callerPhone // Use phone from call document
                            )

                            val isGloballyBlocked = try {
                                globalBlockRepository.isGloballyBlocked(callerId)
                            } catch (e: Exception) {
                                Log.e("INCOMING_CALL", "Error checking global blocked list", e)
                                false
                            }
                            
                            // Check if contact is blocked by checking Firebase contacts collection
                            val isBlocked = try {
                                val contactsRef = db
                                    .collection("users")
                                    .document(uid)
                                    .collection("contacts")

                                // First try: match by userId (most reliable)
                                val blockedByUserId = contactsRef
                                    .whereEqualTo("userId", callerId)
                                    .get()
                                    .await()
                                    .documents
                                    .any { doc -> doc.getString("label") == "BLACK" }

                                if (blockedByUserId) {
                                    Log.d("INCOMING_CALL", "Blocked by userId match: $callerId")
                                    true
                                } else {
                                    // Fallback: match by username
                                    val actualUsername = if (!callerPhone.isNullOrBlank()) {
                                        try {
                                            val phoneLookupService = com.example.fyp_25_s4_23.data.remote.firebase.PhoneLookupService()
                                            phoneLookupService.getUserByPhoneNumber(callerPhone).username
                                        } catch (e: Exception) {
                                            Log.d("INCOMING_CALL", "Phone lookup failed, using callerUsername: $callerUsername")
                                            callerUsername
                                        }
                                    } else {
                                        callerUsername
                                    }

                                    Log.d("INCOMING_CALL", "Checking if username=$actualUsername is blocked")

                                    val blockedByUsername = contactsRef
                                        .whereEqualTo("username", actualUsername)
                                        .get()
                                        .await()
                                        .documents
                                        .any { doc -> doc.getString("label") == "BLACK" }

                                    blockedByUsername
                                }
                                
                            
                            } catch (e: Exception) {
                                Log.e("INCOMING_CALL", "Error checking Firebase contacts", e)
                                false // Default to not blocked if error
                            }

                            when {
                                isGloballyBlocked || isBlocked -> {
                                    Log.w(
                                        "INCOMING_CALL",
                                        "Blocked incoming call from user=$callerId callId=$callId"
                                    )

                                    // Do NOT show notification
                                    // Do NOT end the call - let it ring on caller's side
                                    // This way the blocked caller won't know they're blocked
                                    return@launch
                                }

                                else -> {
                                    // Allowed (not in contacts or label is WHITE)
                                    IncomingCallNotifier.showIncomingCall(
                                        context = context.applicationContext,
                                        callId = callId,
                                        callerId = callerId,
                                        displayName = resolvedDisplayName,
                                        phoneNumber = callerPhone,
                                        username = callerUsername
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
