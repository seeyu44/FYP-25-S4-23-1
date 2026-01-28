package com.example.fyp_25_s4_23.boundary.callhistory

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.domain.entities.Contact
import com.example.fyp_25_s4_23.domain.entities.ContactLabel

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
    val tabs = listOf("All", "Trusted", "Blocked")

    val filteredList = when (selectedTab) {
        1 -> contactList.filter { it.label == ContactLabel.WHITE }
        2 -> contactList.filter { it.label == ContactLabel.BLACK }
        else -> contactList
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
                        onDelete = { viewModel.deleteContact(contact) }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddContactDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { targetUsername, label ->
                    viewModel.addContactByUsername(targetUsername, label) {
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
    onAdd: (String, ContactLabel) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf(ContactLabel.BLACK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add User by Username") },
        text = {
            Column {
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
                onClick = { onAdd(username, selectedLabel) },
                enabled = username.isNotBlank()
            ) {
                Text("Search & Add")
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
fun ContactItemRow(contact: Contact, onDelete: () -> Unit) {
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
            Column {
                Text(
                    text = contact.displayName ?: "Unknown User",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
                Text(
                    text = "Username: ${contact.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
                Text(
                    text = "Status: ${contact.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (contact.label == ContactLabel.BLACK) Color.Red else Color.Blue
                )
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