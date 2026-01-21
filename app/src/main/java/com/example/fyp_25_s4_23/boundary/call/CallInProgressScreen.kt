package com.example.fyp_25_s4_23.boundary.call

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.control.webrtc.WebRtcClient
import com.example.fyp_25_s4_23.boundary.call.CallUiState
import kotlinx.coroutines.flow.StateFlow


@Composable
fun CallInProgressScreen(
    state: StateFlow<CallUiState>,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
    onMute: () -> Unit
) {
    val uiState by state.collectAsState()
    val currentState = uiState // REQUIRED for smart casting

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            /* ================= HEADER ================= */
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Text(
                    text = when (currentState) {
                        is CallUiState.Ringing -> currentState.handle
                        is CallUiState.Active -> currentState.handle
                        is CallUiState.Connecting -> currentState.handle
                        is CallUiState.Disconnected -> currentState.handle
                    },
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = when (currentState) {
                        is CallUiState.Connecting -> "Connecting"
                        is CallUiState.Ringing -> "Ringing"
                        is CallUiState.Active -> "Active"
                        is CallUiState.Disconnected -> "Disconnected"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }

            /* ================= ACTIONS ================= */
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                /* ANSWER */
                if (currentState is CallUiState.Ringing) {
                    Button(
                        onClick = onAnswer,
                        enabled = currentState.isReadyToAnswer
                    ) {
                        Text("Answer")
                    }
                }

                /* ACTIVE CONTROLS */
                if (currentState is CallUiState.Active) {

                    Button(
                        onClick = onMute,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(if (currentState.isMuted) "Unmute" else "Mute")
                    }

                    Text(
                        text = when (currentState.localAudioState) {
                            WebRtcClient.AudioState.MUTED -> "Mic: Muted"
                            WebRtcClient.AudioState.SILENT -> "Mic: On (silent)"
                            WebRtcClient.AudioState.ACTIVE -> "Mic: On (speaking)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Text(
                        text = if (currentState.remoteAudioActive)
                            "Remote: Audio"
                        else
                            "Remote: Silent",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                /* HANG UP */
                if (currentState !is CallUiState.Disconnected) {
                    Button(
                        onClick = onHangUp,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text("Hang up")
                    }
                }
            }
        }
    }
}

