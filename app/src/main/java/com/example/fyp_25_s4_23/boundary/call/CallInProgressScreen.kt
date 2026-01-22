package com.example.fyp_25_s4_23.boundary.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fyp_25_s4_23.control.webrtc.WebRtcClient
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CallInProgressScreen(
    state: StateFlow<CallUiState>,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
    onMute: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    val currentState by state.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1C1C1E) // Dark background like iOS
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Top section - Call status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 60.dp)
            ) {
                Text(
                    text = when (currentState) {
                        is CallUiState.Connecting -> (currentState as CallUiState.Connecting).handle
                        is CallUiState.Ringing -> (currentState as CallUiState.Ringing).handle
                        is CallUiState.Active -> (currentState as CallUiState.Active).handle
                        is CallUiState.Disconnected -> (currentState as CallUiState.Disconnected).handle
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = when (currentState) {
                        is CallUiState.Connecting -> "Connecting..."
                        is CallUiState.Ringing -> {
                            if ((currentState as CallUiState.Ringing).isIncoming) 
                                "Incoming Call" 
                            else 
                                "Calling..."
                        }
                        is CallUiState.Active -> {
                            val s = currentState as CallUiState.Active
                            when (s.localAudioState) {
                                WebRtcClient.AudioState.MUTED -> "Muted"
                                WebRtcClient.AudioState.SILENT -> "Connected"
                                WebRtcClient.AudioState.ACTIVE -> "Speaking"
                            }
                        }
                        is CallUiState.Disconnected -> "Call Ended"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Bottom section - Call controls
            if (currentState !is CallUiState.Disconnected) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 3-button row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 40.dp)
                    ) {
                        // Speaker button
                        val speakerEnabled = (currentState as? CallUiState.Active)?.isSpeakerOn ?: false
                        CallButton(
                            icon = Icons.Default.Phone,
                            label = "Speaker",
                            isHighlighted = speakerEnabled,
                            onClick = onToggleSpeaker,
                            enabled = currentState is CallUiState.Active
                        )
                        
                        // Accept/Decline button (changes based on state)
                        when (currentState) {
                            is CallUiState.Ringing -> {
                                val ringingState = currentState as CallUiState.Ringing
                                if (ringingState.isIncoming && ringingState.isReadyToAnswer) {
                                    // Green Accept button
                                    AcceptDeclineButton(
                                        icon = Icons.Default.Check,
                                        label = "Accept",
                                        isAccept = true,
                                        onClick = onAnswer
                                    )
                                } else {
                                    // Red Decline button (not ready yet)
                                    AcceptDeclineButton(
                                        icon = Icons.Default.Close,
                                        label = "Decline",
                                        isAccept = false,
                                        onClick = onHangUp
                                    )
                                }
                            }
                            is CallUiState.Active -> {
                                // Red Hang Up button
                                AcceptDeclineButton(
                                    icon = Icons.Default.Close,
                                    label = "End",
                                    isAccept = false,
                                    onClick = onHangUp
                                )
                            }
                            else -> {
                                // Red Decline button for other states
                                AcceptDeclineButton(
                                    icon = Icons.Default.Close,
                                    label = "Decline",
                                    isAccept = false,
                                    onClick = onHangUp
                                )
                            }
                        }
                        
                        // Mute button
                        val isMuted = (currentState as? CallUiState.Active)?.isMuted ?: false
                        CallButton(
                            icon = if (isMuted) Icons.Default.Close else Icons.Default.Person,
                            label = "Mute",
                            isHighlighted = isMuted,
                            onClick = onMute,
                            enabled = currentState is CallUiState.Active
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallButton(
    icon: ImageVector,
    label: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (isHighlighted && enabled) 
                        Color.White 
                    else if (enabled)
                        Color(0xFF3A3A3C) 
                    else 
                        Color(0xFF2C2C2E)
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isHighlighted && enabled) Color.Black else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (enabled) Color.White else Color.Gray
        )
    }
}

@Composable
fun AcceptDeclineButton(
    icon: ImageVector,
    label: String,
    isAccept: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (isAccept) 
                        Color(0xFF34C759) // Green for accept
                    else 
                        Color(0xFFFF3B30) // Red for decline/end
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White
        )
    }
}
