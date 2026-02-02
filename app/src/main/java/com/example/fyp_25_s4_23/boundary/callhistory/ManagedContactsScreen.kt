package com.example.fyp_25_s4_23.boundary.callhistory

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.domain.entities.Contact
import com.example.fyp_25_s4_23.domain.entities.ContactLabel
import com.example.fyp_25_s4_23.boundary.call.VoipCallManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagedContactsScreen(
    viewModel: ManagedContactsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val contactList by viewModel.contacts.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Contacts", "Blocked")

    val filteredList = when (selectedTab) {
        0 -> contactList.filter { it.label == ContactLabel.WHITE }
        1 -> contactList.filter { it.label == ContactLabel.BLACK }
        else -> contactList.filter { it.label == ContactLabel.WHITE }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Managed Contacts") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        }
    ) { padding ->
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No contacts found in ${tabs[selectedTab]}.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                items(filteredList) { contact ->
                    ContactItemRow(
                        contact = contact,
                        isBlocked = selectedTab == 1,
                        onCall = {
                            viewModel.callContact(contact.phoneNumber) { username ->
                                if (username != null) {
                                    VoipCallManager.startOutgoingVoipCall(context, username)
                                }
                            }
                        },
                        onBlock = { viewModel.blockContact(contact) },
                        onUnblock = { viewModel.unblockContact(contact) },
                        onDelete = { viewModel.deleteContact(contact) }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddContactDialog(
                onDismiss = { showAddDialog = false },
                onVerifyUsername = { username ->
                    viewModel.verifyUsername(username)
                },
                onVerifyPhoneNumber = { phoneNumber ->
                    viewModel.verifyPhoneNumber(phoneNumber)
                },
                onAddByUsername = { targetUsername, label ->
                    viewModel.addContactByUsername(targetUsername, label) {
                        showAddDialog = false
                    }
                },
                onAddByPhoneNumber = { phoneNumber, contactName, label ->
                    viewModel.addContactByPhoneNumber(phoneNumber, contactName, label) {
                        showAddDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onVerifyUsername: suspend (String) -> Boolean,
    onVerifyPhoneNumber: suspend (String) -> Boolean,
    onAddByUsername: (String, ContactLabel) -> Unit,
    onAddByPhoneNumber: (String, String, ContactLabel) -> Unit
) {
    var usePhoneNumber by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var isValidUser by remember { mutableStateOf<Boolean?>(null) }
    var selectedLabel by remember { mutableStateOf(ContactLabel.BLACK) }

    LaunchedEffect(username) {
        if (!usePhoneNumber) {
            val clean = username.trim().lowercase()

            if (clean.length < 3) {
                isValidUser = null
                return@LaunchedEffect
            }

            isChecking = true
            delay(400)

            // cancel stale check
            if (clean != username.trim().lowercase()) {
                isChecking = false
                return@LaunchedEffect
            }

            isValidUser = onVerifyUsername(clean)
            isChecking = false
        }
    }

    LaunchedEffect(phoneNumber) {
        if (usePhoneNumber) {
            val clean = phoneNumber.trim()

            if (clean.length < 8) {
                isValidUser = null
                return@LaunchedEffect
            }

            isChecking = true
            delay(400)

            if (clean != phoneNumber.trim()) {
                isChecking = false
                return@LaunchedEffect
            }

            isValidUser = onVerifyPhoneNumber(clean)
            isChecking = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (usePhoneNumber) "Add Contact by Phone" else "Add Contact by Username") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add by:")
                    RadioButton(
                        selected = !usePhoneNumber,
                        onClick = { usePhoneNumber = false }
                    )
                    Text("Username")
                    RadioButton(
                        selected = usePhoneNumber,
                        onClick = { usePhoneNumber = true }
                    )
                    Text("Phone")
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (usePhoneNumber) {
                    Text(
                        text = "Enter the phone number and contact name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. 87654321 or 91234567") },
                        singleLine = true
                    )
                    when {
                        isChecking -> {
                            Text("Checking phone number…", color = Color.Gray)
                        }
                        isValidUser == true -> {
                            Text("Phone number found ✅", color = Color(0xFF4CAF50))
                        }
                        isValidUser == false -> {
                            Text("Phone number not found ❌", color = Color.Red)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Contact Name") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. John") },
                        singleLine = true
                    )
                } else {
                    Text(
                        text = "Enter the username of the person you want to add. We will verify their account in Firebase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Target Username") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. user_id_123") },
                        singleLine = true
                    )
                    when {
                        isChecking -> {
                            Text("Checking username…", color = Color.Gray)
                        }
                        isValidUser == true -> {
                            Text("User found ✅", color = Color(0xFF4CAF50))
                        }
                        isValidUser == false -> {
                            Text("User not found ❌", color = Color.Red)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Label Type:", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedLabel == ContactLabel.BLACK,
                        onClick = { selectedLabel = ContactLabel.BLACK }
                    )
                    Text("Blacklist")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = selectedLabel == ContactLabel.WHITE,
                        onClick = { selectedLabel = ContactLabel.WHITE }
                    )
                    Text("Whitelist")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (usePhoneNumber) {
                        onAddByPhoneNumber(phoneNumber, contactName, selectedLabel)
                    } else {
                        onAddByUsername(username, selectedLabel)
                    }
                },
                enabled = (isValidUser == true && !isChecking) && 
                         (if (usePhoneNumber) contactName.isNotBlank() else true)
            ) {
                Text(if (isChecking) "Checking…" else "Add Contact")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ContactItemRow(
    contact: Contact,
    isBlocked: Boolean,
    onCall: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (contact.label == ContactLabel.BLACK)
                Color(0xFFFFEBEE)
            else
                Color(0xFFE3F2FD)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName ?: "Unknown User",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
                Text(
                    text = "Phone: ${contact.phoneNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
                Text(
                    text = "Status: ${contact.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (contact.label == ContactLabel.BLACK) Color.Red else Color.Blue
                )
            }
            Row(horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onCall) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Call",
                        tint = Color(0xFF2196F3)
                    )
                }
                if (isBlocked) {
                    IconButton(onClick = onUnblock) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Unblock",
                            tint = Color(0xFF4CAF50)
                        )
                    }
                } else {
                    IconButton(onClick = onBlock) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Block",
                            tint = Color(0xFFFF9800)
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}