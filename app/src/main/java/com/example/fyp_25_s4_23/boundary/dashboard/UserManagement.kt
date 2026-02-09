package com.example.fyp_25_s4_23.boundary.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount

/**
 * User management composable for admin dashboard.
 * Allows searching, filtering, and managing user accounts.
 */
@Composable
fun UserManagement(
    users: List<UserAccount>,
    onDisableUser: (String) -> Unit,
    onDeleteUser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedUserUid by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<Pair<String, String>?>(null) } // action type to uid
    var actionType by remember { mutableStateOf("") } // "disable" or "delete"

    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) {
            users
        } else {
            users.filter { user ->
                user.displayName.contains(searchQuery, ignoreCase = true) ||
                (user.firebaseUid?.contains(searchQuery, ignoreCase = true) ?: false)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            "User Management",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by name or UID") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        if (filteredUsers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "No users found" else "No users match \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredUsers,
                    key = { it.firebaseUid ?: it.id }
                ) { user ->
                    UserListItem(
                        user = user,
                        isSelected = user.firebaseUid == selectedUserUid,
                        onSelect = {
                            selectedUserUid = if (selectedUserUid == user.firebaseUid) null else user.firebaseUid
                        },
                        onDisable = {
                            pendingAction = Pair("disable", user.firebaseUid ?: "")
                            actionType = "disable"
                            showConfirmDialog = true
                        },
                        onDelete = {
                            pendingAction = Pair("delete", user.firebaseUid ?: "")
                            actionType = "delete"
                            showConfirmDialog = true
                        }
                    )
                }
            }
        }

        Text(
            text = "Showing ${filteredUsers.size} of ${users.size} users",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    if (showConfirmDialog && pendingAction != null) {
        val (action, uid) = pendingAction!!
        val displayInfo = filteredUsers.find { it.firebaseUid == uid }?.displayName ?: uid

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { 
                Text(if (action == "disable") "Disable User Account" else "Delete User Account")
            },
            text = {
                Text(
                    if (action == "disable")
                        "Are you sure you want to disable $displayInfo's account? They will not be able to log in."
                    else
                        "Are you sure you want to permanently delete $displayInfo's account? This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (action == "disable") {
                            onDisableUser(uid)
                        } else {
                            onDeleteUser(uid)
                        }
                        showConfirmDialog = false
                        pendingAction = null
                        selectedUserUid = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action == "disable") 
                            Color(0xFFFFA500) else MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UserListItem(
    user: UserAccount,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDisable: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onSelect)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isSelected) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "Role: ${user.role}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!user.firebaseUid.isNullOrBlank()) {
                    Text(
                        text = "UID: ${user.firebaseUid.take(16)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(text = if (isSelected) "▼" else "▶")
        }

        if (isSelected) {
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDisable,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFA500)
                    )
                ) {
                    Text("Disable", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
