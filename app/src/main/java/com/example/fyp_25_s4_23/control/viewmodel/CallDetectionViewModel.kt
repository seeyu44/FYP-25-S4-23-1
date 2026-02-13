package com.example.fyp_25_s4_23.control.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.control.controllers.DetectionController
import com.example.fyp_25_s4_23.control.usecases.SaveDetectionAlertUseCase
import com.example.fyp_25_s4_23.control.webrtc.WebRtcClient
import com.example.fyp_25_s4_23.data.remote.firebase.GlobalBlockRepository
import com.example.fyp_25_s4_23.entity.data.repositories.AlertRepository
import com.example.fyp_25_s4_23.entity.data.repositories.CallRepository
import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.example.fyp_25_s4_23.entity.domain.entities.AlertEvent
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.Contact
import com.example.fyp_25_s4_23.entity.domain.entities.DetectionResult
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "CallDetectionVM"

/**
 * State for call detection and monitoring
 */
sealed class DetectionState {
    data object Idle : DetectionState()
    data class CallInProgress(
        val callId: String,
        val remoteUserId: String,
        val displayName: String,
        val isIncoming: Boolean,
        val isRemoteKnownContact: Boolean = false,
        val deepfakeScore: Float? = null,
        val isDeepfake: Boolean = false
    ) : DetectionState()

    data class CallEnded(val reason: String? = null) : DetectionState()
}

/**
 * One-shot events for UI (vibration, alerts, etc.)
 */
sealed class DetectionEvent {
    data class ShowAlert(val score: Float, val message: String) : DetectionEvent()
    data class ContactResolved(val contact: Contact?) : DetectionEvent()
    data class UserFlagged(val userId: String) : DetectionEvent()
}

/**
 * CallDetectionViewModel handles all call-related logic:
 * - Call signaling via Firebase
 * - Contact resolution
 * - Deepfake detection and flagging
 * - Call recording and monitoring
 * - WebRTC state management
 *
 * This ViewModel consolidates logic from:
 * - CallInProgressActivity (lines 73-165)
 * - AppMainViewModel (call monitoring, deepfake detection)
 * - Various callbacks and async operations
 *
 * Extracted as part of Phase 1 refactoring.
 */
class CallDetectionViewModel(
    private val contactRepository: ContactRepository,
    private val globalBlockRepository: GlobalBlockRepository,
    private val callRepository: CallRepository,
    private val alertRepository: AlertRepository,
    private val saveDetectionAlertUseCase: SaveDetectionAlertUseCase,
    private val modelRunner: ModelRunner,
    private val detectionController: DetectionController
) : ViewModel() {

    private val _detectionState = MutableStateFlow<DetectionState>(DetectionState.Idle)
    val detectionState: StateFlow<DetectionState> = _detectionState.asStateFlow()

    private val _events = MutableSharedFlow<DetectionEvent>()
    val events: SharedFlow<DetectionEvent> = _events.asSharedFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _isRemoteKnownContact = MutableStateFlow(false)
    val isRemoteKnownContact: StateFlow<Boolean> = _isRemoteKnownContact.asStateFlow()

    private val _hasFlaggedGlobalBlock = MutableStateFlow(false)
    val hasFlaggedGlobalBlock: StateFlow<Boolean> = _hasFlaggedGlobalBlock.asStateFlow()

    private var webRtcClient: WebRtcClient? = null

    /**
     * Resolves contact information by phone number and username
     */
    fun resolveContactInfo(
        callId: String,
        remoteUserId: String,
        phoneNumber: String?,
        incomingDisplayName: String?,
        isIncoming: Boolean
    ) {
        viewModelScope.launch {
            try {
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

                // Try to find contact by phone number
                val knownByPhone = if (!phoneNumber.isNullOrBlank()) {
                    contactRepository.getContactByPhoneNumber(currentUserId, phoneNumber)
                } else {
                    null
                }

                // Try to find contact by username
                val knownByUsername = if (!incomingDisplayName.isNullOrBlank()) {
                    contactRepository.getContactByUsername(currentUserId, incomingDisplayName)
                } else {
                    null
                }

                _isRemoteKnownContact.value = knownByPhone != null || knownByUsername != null
                val contact = knownByPhone ?: knownByUsername

                // Determine display name
                val resolvedName = contact?.displayName
                    ?: incomingDisplayName
                    ?: phoneNumber
                    ?: remoteUserId

                _displayName.value = resolvedName

                _events.emit(DetectionEvent.ContactResolved(contact))

            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve contact info", e)
            }
        }
    }

    /**
     * Initializes call state and signaling
     */
    fun initializeCall(
        callId: String,
        remoteUserId: String,
        displayName: String,
        isIncoming: Boolean
    ) {
        _detectionState.value = DetectionState.CallInProgress(
            callId = callId,
            remoteUserId = remoteUserId,
            displayName = displayName,
            isIncoming = isIncoming
        )
    }

    /**
     * Attaches WebRTC client for managing the peer connection
     */
    fun attachWebRtcClient(client: WebRtcClient) {
        webRtcClient = client
    }

    /**
     * Handles deepfake flagging when detection is high confidence
     */
    fun onDeepfakeFlagged(
        callId: String,
        remoteUserId: String,
        remoteUsername: String?,
        phoneNumber: String?,
        isIncoming: Boolean
    ) {
        viewModelScope.launch {
            if (!isIncoming || _hasFlaggedGlobalBlock.value || _isRemoteKnownContact.value) {
                return@launch
            }

            try {
                val callSnapshot = FirebaseFirestore.getInstance()
                    .collection("calls")
                    .document(callId)
                    .get()
                    .await()

                val highestKey = "${remoteUserId}_highest_is_deepfake"
                val highestIsDeepfake = callSnapshot.getBoolean(highestKey) == true

                if (!highestIsDeepfake) {
                    Log.d(TAG, "Skip global flag: $highestKey is not true")
                    return@launch
                }

                _hasFlaggedGlobalBlock.value = true
                globalBlockRepository.flagUser(
                    userId = remoteUserId,
                    username = remoteUsername,
                    phoneNumber = phoneNumber,
                    callId = callId
                )

                _events.emit(DetectionEvent.UserFlagged(remoteUserId))

                Log.d(TAG, "User flagged globally: $remoteUserId")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to flag user in global block list", e)
            }
        }
    }

    /**
     * Handles detection result from ML model
     */
    fun onDetectionResult(result: DetectionResult) {
        viewModelScope.launch {
            try {
                val currentState = _detectionState.value

                if (currentState is DetectionState.CallInProgress) {
                    // Save detection alert using the invoke operator
                    saveDetectionAlertUseCase(currentState.callId, result.probability)

                    val isDeepfake = result.probability > 0.5

                    _detectionState.value = currentState.copy(
                        deepfakeScore = result.probability,
                        isDeepfake = isDeepfake
                    )

                    if (isDeepfake) {
                        _events.emit(
                            DetectionEvent.ShowAlert(
                                score = result.probability,
                                message = "Deepfake detected with score ${(result.probability * 100).toInt()}%"
                            )
                        )
                    }
                } else {
                    Log.w(TAG, "Detection result received but no active call")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process detection result", e)
            }
        }
    }

    /**
     * Records a call with detection results
     */
    fun recordCallWithDetection(
        callRecord: CallRecord,
        detectionResults: List<DetectionResult>
    ) {
        viewModelScope.launch {
            try {
                callRepository.upsert(callRecord)

                for (detection in detectionResults) {
                    saveDetectionAlertUseCase(callRecord.id, detection.probability)
                }

                Log.d(TAG, "Call recorded with ${detectionResults.size} detection results")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to record call", e)
            }
        }
    }

    /**
     * Ends the current call
     */
    fun endCall(reason: String? = null) {
        _detectionState.value = DetectionState.CallEnded(reason)
        webRtcClient = null
    }
}
