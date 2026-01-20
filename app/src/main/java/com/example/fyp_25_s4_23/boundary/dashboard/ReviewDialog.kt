package com.example.fyp_25_s4_23.boundary.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Dialog for submitting an app review with rating and description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDialog(
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, description: String) -> Unit
) {
    var rating by remember { mutableStateOf(0) }
    var description by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Leave a Review",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "How would you rate this app?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Star Rating
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    for (i in 1..5) {
                        IconButton(onClick = { rating = i }) {
                            Icon(
                                imageVector = if (i <= rating) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = "Star $i",
                                tint = if (i <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                // Description TextField
                OutlinedTextField(
                    value = description,
                    onValueChange = { 
                        description = it
                        showError = false
                    },
                    label = { Text("Your Review") },
                    placeholder = { Text("Tell us what you think...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 5,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Please provide a rating and description") }
                    } else null
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (rating == 0 || description.isBlank()) {
                                showError = true
                            } else {
                                onSubmit(rating, description.trim())
                            }
                        }
                    ) {
                        Text("Submit Review")
                    }
                }
            }
        }
    }
}
