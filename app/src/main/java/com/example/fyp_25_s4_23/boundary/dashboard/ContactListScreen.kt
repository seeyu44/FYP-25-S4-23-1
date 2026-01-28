package com.example.fyp_25_s4_23.boundary.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.control.viewmodel.AppMainViewModel
import com.example.fyp_25_s4_23.domain.entities.Contact
import com.example.fyp_25_s4_23.domain.entities.ContactLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(viewModel: AppMainViewModel) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Trusted", "Blocked")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Managed Contacts") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToDashboard() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            val filteredContacts = remember(state.contacts, selectedTab) {
                when (selectedTab) {
                    1 -> state.contacts.filter { it.label == ContactLabel.WHITE }
                    2 -> state.contacts.filter { it.label == ContactLabel.BLACK }
                    else -> state.contacts
                }
            }

            if (filteredContacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No contacts found.")
                }
            } else {
                LazyColumn {
                    items(filteredContacts) { contact ->
                        ContactItemRow(
                            contact = contact,
                            onUpdateLabel = { newLabel ->
                                viewModel.updateContactLabel(contact.id, newLabel)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ContactItemRow(contact: Contact, onUpdateLabel: (ContactLabel) -> Unit) {
    val icon: ImageVector
    val color: Color

    when (contact.label) {
        ContactLabel.WHITE -> {
            icon = Icons.Default.CheckCircle
            color = Color(0xFF4CAF50)
        }
        ContactLabel.BLACK -> {
            icon = Icons.Default.Close
            color = Color(0xFFF44336)
        }
        else -> {
            icon = Icons.Default.AccountCircle
            color = Color.Gray
        }
    }

    ListItem(
        headlineContent = { Text(contact.displayName ?: "No Name") },
        supportingContent = { Text(contact.id) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = color)
        },
        trailingContent = {
            Row {
                IconButton(onClick = {
                    onUpdateLabel(if (contact.label == ContactLabel.WHITE) ContactLabel.NONE else ContactLabel.WHITE)
                }) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Trust",
                        tint = if (contact.label == ContactLabel.WHITE) Color(0xFF4CAF50) else Color.LightGray
                    )
                }
                IconButton(onClick = {
                    onUpdateLabel(if (contact.label == ContactLabel.BLACK) ContactLabel.NONE else ContactLabel.BLACK)
                }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Block",
                        tint = if (contact.label == ContactLabel.BLACK) Color(0xFFF44336) else Color.LightGray
                    )
                }
            }
        }
    )
}