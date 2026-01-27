package com.example.fyp_25_s4_23.boundary.dashboard

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
        val cleaned = phoneNumber.replace(Regex("[\\s-]"), "")
        cleaned.length == 8 && cleaned.matches(Regex("^[689]\\d{7}$"))
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

            // Phone number input
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { 
                    // Only allow digits, spaces, and hyphens
                    if (it.all { char -> char.isDigit() || char == ' ' || char == '-' }) {
                        phoneNumber = it
                        errorMessage = null
                    }
                },
                label = { Text("Phone Number") },
                placeholder = { Text("98765432") },
                prefix = { Text("+65 ") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                enabled = !isLoading,
                isError = phoneNumber.isNotEmpty() && !isValidPhone,
                supportingText = {
                    if (phoneNumber.isNotEmpty() && !isValidPhone) {
                        Text("Must be 8 digits starting with 6, 8, or 9")
                    }
                }
            )

            // Error message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Call button
            Button(
                onClick = {
                    scope.launch {
                        try {
                            isLoading = true
                            errorMessage = null
                            
                            val normalized = "+65${phoneNumber.replace(Regex("[\\s-]"), "")}"
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
                enabled = isValidPhone && !isLoading
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
