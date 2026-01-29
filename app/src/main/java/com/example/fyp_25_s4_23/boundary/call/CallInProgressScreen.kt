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
    onToggleSpeaker: () -> Unit,
    onPlayDemoAudio: ((String?) -> Unit)? = null,
    demoAudioFiles: List<String> = emptyList()
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
                
                // Deepfake detection status
                if (currentState is CallUiState.Active) {
                    val activeState = currentState as CallUiState.Active
                    if (activeState.isDetectionActive) {
                        Spacer(modifier = Modifier.height(16.dp))
                        DeepfakeDetectionIndicator(
                            score = activeState.detectionScore,
                            isDeepfake = activeState.isDeepfake
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Bottom section - Call controls
            if (currentState !is CallUiState.Disconnected) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (currentState) {
                        is CallUiState.Ringing -> {
                            val ringingState = currentState as CallUiState.Ringing
                            if (ringingState.isIncoming) {
                                // CALLEE: 2-button layout (Accept + Decline)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(60.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 40.dp)
                                ) {
                                    // Decline button (Red)
                                    AcceptDeclineButton(
                                        icon = Icons.Default.Close,
                                        label = "Decline",
                                        isAccept = false,
                                        onClick = onHangUp
                                    )
                                    
                                    // Accept button (Green) - only show if ready
                                    if (ringingState.isReadyToAnswer) {
                                        AcceptDeclineButton(
                                            icon = Icons.Default.Check,
                                            label = "Accept",
                                            isAccept = true,
                                            onClick = onAnswer
                                        )
                                    }
                                }
                            } else {
                                // CALLER: Single Decline button while ringing
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(bottom = 40.dp)
                                ) {
                                    AcceptDeclineButton(
                                        icon = Icons.Default.Close,
                                        label = "Cancel",
                                        isAccept = false,
                                        onClick = onHangUp
                                    )
                                }
                            }
                        }
                        
                        is CallUiState.Active -> {
                            // ACTIVE CALL: Button layout
                            val activeState = currentState as CallUiState.Active
                            
                            // Demo audio selector (if enabled)
                            if (onPlayDemoAudio != null && demoAudioFiles.isNotEmpty()) {
                                var isDemoPlaying by remember { mutableStateOf(false) }
                                var selectedFile by remember { mutableStateOf<String?>(null) }
                                var menuExpanded by remember { mutableStateOf(false) }
                                
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 24.dp)
                                ) {
                                    // Show currently playing file
                                    if (isDemoPlaying && selectedFile != null) {
                                        Text(
                                            "Playing: $selectedFile",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFFF9500),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Select audio file button
                                        Box {
                                            Button(
                                                onClick = { menuExpanded = true },
                                                enabled = !isDemoPlaying,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF007AFF)
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.List,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(selectedFile ?: "Select Audio")
                                            }
                                            
                                            DropdownMenu(
                                                expanded = menuExpanded,
                                                onDismissRequest = { menuExpanded = false }
                                            ) {
                                                demoAudioFiles.forEach { filename ->
                                                    DropdownMenuItem(
                                                        text = { Text(filename) },
                                                        onClick = {
                                                            selectedFile = filename
                                                            menuExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        
                                        // Play/Stop button
                                        if (selectedFile != null) {
                                            Button(
                                                onClick = {
                                                    if (isDemoPlaying) {
                                                        // Stop playing
                                                        isDemoPlaying = false
                                                        onPlayDemoAudio(null)
                                                    } else {
                                                        // Start playing selected file
                                                        isDemoPlaying = true
                                                        onPlayDemoAudio(selectedFile)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isDemoPlaying) Color(0xFFFF3B30) else Color(0xFF34C759)
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = if (isDemoPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(if (isDemoPlaying) "Stop" else "Play")
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(40.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 40.dp)
                            ) {
                                // Speaker button
                                CallButton(
                                    icon = Icons.Default.Phone,
                                    label = "Speaker",
                                    isHighlighted = activeState.isSpeakerOn,
                                    onClick = onToggleSpeaker,
                                    enabled = true
                                )
                                
                                // End call button (Red)
                                AcceptDeclineButton(
                                    icon = Icons.Default.Close,
                                    label = "End",
                                    isAccept = false,
                                    onClick = onHangUp
                                )
                                
                                // Mute button
                                CallButton(
                                    icon = if (activeState.isMuted) Icons.Default.Close else Icons.Default.Person,
                                    label = "Mute",
                                    isHighlighted = activeState.isMuted,
                                    onClick = onMute,
                                    enabled = true
                                )
                            }
                        }
                        
                        else -> {
                            // CONNECTING/OTHER: Single Decline button
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(bottom = 40.dp)
                            ) {
                                AcceptDeclineButton(
                                    icon = Icons.Default.Close,
                                    label = "Cancel",
                                    isAccept = false,
                                    onClick = onHangUp
                                )
                            }
                        }
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

@Composable
fun DeepfakeDetectionIndicator(
    score: Float?,
    isDeepfake: Boolean
) {
    if (score == null) {
        // Analyzing state
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(
                    Color(0xFF2C2C2E),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Analyzing audio...",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    } else {
        // Detection result
        val (bgColor, textColor, emoji, statusText) = when {
            isDeepfake -> Tuple4(
                Color(0xFFFF3B30), // Red
                Color.White,
                "🚨",
                "DEEPFAKE DETECTED"
            )
            score > 0.3f -> Tuple4(
                Color(0xFFFF9500), // Orange
                Color.White,
                "⚠️",
                "Suspicious"
            )
            else -> Tuple4(
                Color(0xFF34C759), // Green
                Color.White,
                "✅",
                "Real Voice"
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(
                    bgColor,
                    shape = MaterialTheme.shapes.medium
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = emoji,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Confidence: ${(score * 100).toInt()}%",
                color = textColor.copy(alpha = 0.9f),
                fontSize = 12.sp
            )
        }
    }
}

// Helper data class for the indicator
private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
