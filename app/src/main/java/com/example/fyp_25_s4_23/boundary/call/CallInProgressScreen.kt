package com.example.fyp_25_s4_23.boundary.call

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CallInProgressScreen(
    state: kotlinx.coroutines.flow.StateFlow<CallUiState>,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
    onMute: () -> Unit
) {
    val uiState by state.collectAsState()
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = uiState.handle, style = MaterialTheme.typography.titleLarge)
                Text(text = uiState.stateLabel, style = MaterialTheme.typography.titleMedium)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = uiState.handle, style = MaterialTheme.typography.titleLarge)
                Text(text = uiState.stateLabel, style = MaterialTheme.typography.titleMedium)

                //DEBUG — REMOVE AFTER FIXING
                Text(
                    text = "DEBUG isReadyToAnswer=${uiState.isReadyToAnswer}",
                    color = MaterialTheme.colorScheme.error
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onAnswer, enabled = uiState.stateLabel == "Ringing" && uiState.isReadyToAnswer) {
                    Text("Answer")
                }
                Button(onClick = onMute, modifier = Modifier.padding(top = 12.dp)) {
                    Text(if (uiState.isMuted) "Unmute" else "Mute")
                }

                // Local mic state indicator (Muted / Silent / Active)
                androidx.compose.material3.Text(
                    text = when (uiState.localAudioState) {
                        com.example.fyp_25_s4_23.control.webrtc.WebRtcClient.AudioState.MUTED -> "Mic: Muted"
                        com.example.fyp_25_s4_23.control.webrtc.WebRtcClient.AudioState.SILENT -> "Mic: On (silent)"
                        com.example.fyp_25_s4_23.control.webrtc.WebRtcClient.AudioState.ACTIVE -> "Mic: On (speaking)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // Remote audio presence indicator (only for WebRTC calls)
                if (uiState.call == null) { // WebRTC call
                    androidx.compose.material3.Text(
                        text = if (uiState.remoteAudioActive) "Remote: Audio" else "Remote: Silent",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Button(onClick = onHangUp, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Hang up")
                }
            }
        }
    }
}

