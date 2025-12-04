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
                Button(onClick = onAnswer, enabled = uiState.stateLabel == "Ringing") {
                    Text("Answer")
                }
                Button(onClick = onMute, modifier = Modifier.padding(top = 12.dp)) {
                    Text(if (uiState.isMuted) "Unmute" else "Mute")
                }
                Button(onClick = onHangUp, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Hang up")
                }
            }
        }
    }
}

