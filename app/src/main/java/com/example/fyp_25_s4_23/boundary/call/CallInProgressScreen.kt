package com.example.fyp_25_s4_23.boundary.call

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.control.webrtc.WebRtcClient
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CallInProgressScreen(
    state: StateFlow<CallUiState>,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
    onMute: () -> Unit
) {
    val currentState by state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = when (currentState) {
                    is CallUiState.Connecting -> "Connecting"
                    is CallUiState.Ringing -> "Ringing"
                    is CallUiState.Active -> "Active"
                    is CallUiState.Disconnected -> "Disconnected"
                },
                style = MaterialTheme.typography.titleLarge
            )

            if (currentState is CallUiState.Ringing) {
                val s = currentState as CallUiState.Ringing
                if (s.isIncoming && s.isReadyToAnswer) {
                    Button(onClick = onAnswer) {
                        Text("Answer")
                    }
                }
            }

            if (currentState is CallUiState.Active) {
                val s = currentState as CallUiState.Active

                Button(onClick = onMute) {
                    Text(if (s.isMuted) "Unmute" else "Mute")
                }

                Text(
                    text = when (s.localAudioState) {
                        WebRtcClient.AudioState.MUTED -> "Mic: Muted"
                        WebRtcClient.AudioState.SILENT -> "Mic: Silent"
                        WebRtcClient.AudioState.ACTIVE -> "Mic: Speaking"
                    }
                )
            }

            if (currentState !is CallUiState.Disconnected) {
                Button(onClick = onHangUp) {
                    Text("Hang Up")
                }
            }
        }
    }
}
