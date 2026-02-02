package com.example.fyp_25_s4_23.boundary.call

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.data.remote.firebase.PhoneLookupService
import com.example.fyp_25_s4_23.domain.entities.ContactLabel
import com.example.fyp_25_s4_23.entity.data.db.AppDatabase
import com.example.fyp_25_s4_23.entity.data.repositories.ContactRepository
import com.example.fyp_25_s4_23.boundary.dashboard.BottomNavigationBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    onBack: () -> Unit,
    onNavigateToCallHistory: (() -> Unit)? = null,
    onNavigateToContacts: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val phoneLookupService = remember { PhoneLookupService() }
    val contactRepository = remember {
        ContactRepository(AppDatabase.getInstance(context).contactDao())
    }
    
    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val isValidPhone = remember(phoneNumber) {
        // Singapore phone number: 8 digits starting with 6, 8, or 9
        phoneNumber.length == 8 && phoneNumber.matches(Regex("^[689]\\d{7}$"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Dialer") }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                currentRoute = "dialer",
                onNavigate = { route ->
                    when (route) {
                        "home" -> onBack()
                        "call_history" -> onNavigateToCallHistory?.invoke()
                        "dialer" -> { /* Already here */ }
                        "contacts" -> onNavigateToContacts?.invoke()
                        "logout" -> onLogout?.invoke()
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Phone number display
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "+65 ",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (phoneNumber.isEmpty()) "Enter number" else phoneNumber,
                            style = MaterialTheme.typography.headlineMedium,
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
                                    Icons.Default.Clear,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Error message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (phoneNumber.isNotEmpty() && !isValidPhone) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Must be 8 digits starting with 6, 8, or 9",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Number pad and call button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Row 4: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Spacer(modifier = Modifier.size(80.dp))
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
                        Spacer(modifier = Modifier.size(80.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Call button
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                isLoading = true
                                errorMessage = null
                                
                                val normalized = "+65$phoneNumber"
                                Log.d("DialerScreen", "Calling phone number: $normalized")

                                val existingContact = contactRepository
                                    .getContactByPhoneNumber(normalized)
                                if (existingContact?.label == ContactLabel.BLACK) {
                                    isLoading = false
                                    errorMessage = "This number is blocked"
                                    return@launch
                                }
                                
                                // Look up user by phone number
                                val result = phoneLookupService.getUserByPhoneNumber(normalized)
                                
                                Log.d("DialerScreen", "Found user: ${result.username} (${result.uid})")
                                isLoading = false
                                
                                // Initiate call with the resolved UID and available info
                                VoipCallManager.startOutgoingVoipCall(
                                    context = context,
                                    calleeUserId = result.uid,
                                    calleeDisplayName = result.username,
                                    calleePhoneNumber = normalized
                                )
                                
                                // Go back to dashboard after initiating call
                                onBack()
                            } catch (e: Exception) {
                                Log.e("DialerScreen", "Error during phone lookup", e)
                                isLoading = false
                                errorMessage = e.message ?: "Failed to look up phone number"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = isValidPhone && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Looking up...", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Start Secure Call", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
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
        modifier = Modifier.size(80.dp),
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineLarge
        )
    }
}
