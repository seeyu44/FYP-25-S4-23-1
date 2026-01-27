package com.example.fyp_25_s4_23.boundary.dashboard

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.boundary.call.VoipCallManager
import com.example.fyp_25_s4_23.data.remote.firebase.PhoneLookupService
import kotlinx.coroutines.launch

@Composable
fun DialerCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val phoneLookupService = remember { PhoneLookupService() }
    
    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val isValidPhone = remember(phoneNumber) {
        // Singapore phone number: 8 digits starting with 6, 8, or 9
        phoneNumber.length == 8 && phoneNumber.matches(Regex("^[689]\\d{7}$"))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Secure Internal VOIP",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )

            Text(
                text = "Enter a Singapore phone number to connect.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Phone number display
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "+65 ",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (phoneNumber.isEmpty()) "98765432" else phoneNumber,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (phoneNumber.isEmpty()) 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (phoneNumber.isNotEmpty()) {
                        IconButton(onClick = { 
                            phoneNumber = phoneNumber.dropLast(1)
                            errorMessage = null
                        }) {
                            Icon(
                                Icons.Default.Backspace,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (phoneNumber.isNotEmpty() && !isValidPhone) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Must be 8 digits starting with 6, 8, or 9",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Number pad
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Rows 1-3: digits 1-9
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 1..3) {
                            val digit = (row * 3 + col).toString()
                            DialerButton(
                                text = digit,
                                enabled = !isLoading && phoneNumber.length < 8,
                                onClick = {
                                    if (phoneNumber.length < 8) {
                                        phoneNumber += digit
                                        errorMessage = null
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Row 4: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Spacer(modifier = Modifier.size(72.dp))
                    DialerButton(
                        text = "0",
                        enabled = !isLoading && phoneNumber.length < 8,
                        onClick = {
                            if (phoneNumber.length < 8) {
                                phoneNumber += "0"
                                errorMessage = null
                            }
                        }
                    )
                    Spacer(modifier = Modifier.size(72.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Call button
            Button(
                onClick = {
                    scope.launch {
                        try {
                            isLoading = true
                            errorMessage = null
                            
                            val normalized = "+65$phoneNumber"
                            Log.d("DialerCard", "Calling phone number: $normalized")
                            
                            // Look up user by phone number
                            val result = phoneLookupService.getUserByPhoneNumber(normalized)
                            
                            Log.d("DialerCard", "Found user: ${result.username} (${result.uid})")
                            isLoading = false
                            
                            // Initiate call with the resolved UID
                            VoipCallManager.startOutgoingVoipCall(
                                context = context,
                                calleeUserId = result.uid
                            )
                            
                            // Clear the phone number after successful call initiation
                            phoneNumber = ""
                        } catch (e: Exception) {
                            Log.e("DialerCard", "Error during phone lookup", e)
                            isLoading = false
                            errorMessage = e.message ?: "Failed to look up phone number"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isValidPhone && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Looking up...")
                } else {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Secure Call")
                }
            }
        }
    }
}

@Composable
fun DialerButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
