package com.example.fyp_25_s4_23.control.webrtc

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentSnapshot

class FirebaseSignalingManager {

    private val firestore = FirebaseFirestore.getInstance()

    private var callListener: ListenerRegistration? = null
    private var iceListener: ListenerRegistration? = null
    private var detectionListener: ListenerRegistration? = null

    private var lastStatus: String? = null
    private var lastOfferSdp: String? = null
    private var lastAnswerSdp: String? = null

    /* =========================
       CREATE CALL
       ========================= */
    fun createCall(callId: String, callerUid: String, calleeUid: String, callerUsername: String, callerPhone: String? = null) {
        firestore.collection("calls")
            .document(callId)
            .set(
                mapOf(
                    "caller_user_id" to callerUid,
                    "callee_user_id" to calleeUid,
                    "caller_username" to callerUsername,
                    "caller_phone" to callerPhone,
                    "status" to "ringing",
                    "offer_sdp" to null,
                    "answer_sdp" to null,
                    "created_at" to com.google.firebase.Timestamp.now()
                )
            )
            .addOnFailureListener { e ->
                Log.e("CALL_SIG", "createCall FAILED", e)
            }
    }

    /* =========================
       CALL LISTENER
       ========================= */
    fun listenToCall(
        callId: String,
        isCaller: Boolean,
        onOffer: (String) -> Unit,
        onAnswer: (String) -> Unit,
        onStatus: (String) -> Unit,
        onStatusWithReason: ((String, String?) -> Unit)? = null
    ) {
        stopListening()
        val ref = firestore.collection("calls").document(callId)

//        /* ---- Initial one-time sync ---- */
//        ref.get().addOnSuccessListener { snap ->
//            if (snap == null || !snap.exists()) return@addOnSuccessListener
//            applySnapshot(
//                snapshot = snap,
//                isCaller = isCaller,
//                onOffer = onOffer,
//                onAnswer = onAnswer,
//                onStatus = onStatus
//            )
//        }

        /* ---- Realtime listener ---- */
        callListener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("CALL_SIG", "listenToCall error", error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

            applySnapshot(
                snapshot = snapshot,
                isCaller = isCaller,
                onOffer = onOffer,
                onAnswer = onAnswer,
                onStatus = onStatus,
                onStatusWithReason = onStatusWithReason
            )
        }
    }

    /* =========================
       SNAPSHOT APPLY (DEDUPED)
       ========================= */
    private fun applySnapshot(
        snapshot: DocumentSnapshot,
        isCaller: Boolean,
        onOffer: (String) -> Unit,
        onAnswer: (String) -> Unit,
        onStatus: (String) -> Unit,
        onStatusWithReason: ((String, String?) -> Unit)? = null
    ) {
        /* ---- STATUS ---- */
        snapshot.getString("status")?.let { status ->
            if (status != lastStatus) {
                lastStatus = status
                Log.d("CALL_SIG", "Status → $status")
                onStatus(status)
                
                // Pass status with reason if available
                if (status == "ended") {
                    val reason = snapshot.getString("ended_reason")
                    onStatusWithReason?.invoke(status, reason)
                }
            }
        }

        /* ---- OFFER (callee only) ---- */
        if (!isCaller) {
            snapshot.getString("offer_sdp")?.let { offer ->
                if (offer != lastOfferSdp) {
                    lastOfferSdp = offer
                    Log.w("WEBRTC_FLOW", "OFFER received")
                    onOffer(offer)
                }
            }
        }

        /* ---- ANSWER (caller only) ---- */
        if (isCaller) {
            snapshot.getString("answer_sdp")?.let { answer ->
                if (answer != lastAnswerSdp) {
                    lastAnswerSdp = answer
                    Log.w("WEBRTC_FLOW", "ANSWER received")
                    onAnswer(answer)
                }
            }
        }
    }

    /* =========================
       SIGNALING SENDERS
       ========================= */
    fun sendOffer(callId: String, offerSdp: String) {
        firestore.collection("calls")
            .document(callId)
            .update(
                mapOf(
                    "offer_sdp" to offerSdp,
                    "status" to "ringing"
                )
            )
    }

    fun sendAnswer(callId: String, answerSdp: String) {
        firestore.collection("calls")
            .document(callId)
            .update(
                mapOf(
                    "answer_sdp" to answerSdp,
                    "status" to "in_call"
                )
            )
    }

    fun updateCallStatus(callId: String, status: String) {
        firestore.collection("calls")
            .document(callId)
            .update(
                mapOf(
                    "status" to status,
                    "updated_at" to com.google.firebase.Timestamp.now()
                )
            )
            .addOnFailureListener { e ->
                Log.e("CALL_SIG", "Failed to update call status", e)
            }
    }

    fun endCall(callId: String) {
        updateCallStatus(callId, "ended")
    }

    /* =========================
       ICE
       ========================= */
    fun sendIceCandidate(
        callId: String,
        userId: String,
        candidate: Map<String, Any>
    ) {
        Log.w("ICE_DB", "WRITE ICE: callId=$callId userIdDoc=$userId candidate=${candidate["candidate"]}")
        firestore.collection("calls")
            .document(callId)
            .collection(
                "ice_candidates")
            .document(userId)
            .collection("candidates")
            .add(candidate)
    }

    fun listenForIceCandidates(
        callId: String,
        remoteUserId: String,
        onCandidate: (Map<String, Any>) -> Unit
    ) {
        Log.w("ICE_DB", "LISTEN ICE: callId=$callId remoteUserIdDoc=$remoteUserId")

        iceListener = firestore.collection("calls")
            .document(callId)
            .collection("ice_candidates")
            .document(remoteUserId)
            .collection("candidates")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ICE_DB", "listenForIceCandidates ERROR callId=$callId remoteUserId=$remoteUserId", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    Log.w("ICE_DB", "listenForIceCandidates snapshot=null callId=$callId remoteUserId=$remoteUserId")
                    return@addSnapshotListener
                }

                Log.d("ICE_DB", "listenForIceCandidates docs=${snapshot.size()} changes=${snapshot.documentChanges.size}")

                for (change in snapshot.documentChanges) {
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        Log.d("ICE_DB", "ICE doc added id=${change.document.id} data=${change.document.data}")
                        change.document.data?.let(onCandidate)
                    }
                }
            }

    }

    /* =========================
       DEEPFAKE DETECTION SYNC
       ========================= */
    
    /**
     * Send detection result to Firestore so the OTHER user can see it
     */
    fun sendDetectionResult(callId: String, userId: String, score: Float, isDeepfake: Boolean) {
        firestore.collection("calls")
            .document(callId)
            .update(
                mapOf(
                    "${userId}_detection_score" to score,
                    "${userId}_is_deepfake" to isDeepfake,
                    "${userId}_detection_timestamp" to System.currentTimeMillis()
                )
            )
            .addOnFailureListener { e ->
                Log.e("DEEPFAKE_SYNC", "Failed to send detection result", e)
            }
    }
    
    /**
     * Listen for the REMOTE user's detection results
     * 
     * IMPORTANT: We only read the SCORE from Firestore and calculate isDeepfake locally
     * using OUR threshold. This ensures both devices can use different thresholds if needed
     * (e.g., 0.05 for demo testing vs 0.7 for production).
     */
    fun listenForRemoteDetection(
        callId: String,
        remoteUserId: String,
        threshold: Float = 0.7f,  // Production threshold
        onRemoteDetection: (score: Float, isDeepfake: Boolean) -> Unit
    ) {
        val ref = firestore.collection("calls").document(callId)
        
        detectionListener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("DEEPFAKE_SYNC", "listenForRemoteDetection error", error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
            
            // Read only the SCORE - we'll calculate isDeepfake ourselves with our threshold
            val score = snapshot.getDouble("${remoteUserId}_detection_score")?.toFloat()
            
            if (score != null) {
                // Calculate isDeepfake using OUR threshold (receiving device decides)
                val isDeepfake = score >= threshold
                Log.d("DEEPFAKE_SYNC", "Remote user detection: score=$score, isDeepfake=$isDeepfake (threshold=$threshold)")
                onRemoteDetection(score, isDeepfake)
            }
        }
    }
    
    /* =========================
       CLEANUP
       ========================= */
    fun stopListening() {
        callListener?.remove()
        iceListener?.remove()
        detectionListener?.remove()
        callListener = null
        iceListener = null
        detectionListener = null

        lastStatus = null
        lastOfferSdp = null
        lastAnswerSdp = null
    }
}
