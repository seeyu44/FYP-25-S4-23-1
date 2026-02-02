package com.example.fyp_25_s4_23.boundary.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Displays a "Leave a Review" section with star rating that opens a review dialog.
 */
@Composable
fun LeaveReviewCard(
    onReviewClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Leave us a Review",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Star rating display (non-interactive)
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                repeat(5) { index ->
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = "Star ${index + 1}",
                        modifier = Modifier
                            .size(40.dp)
                            .padding(horizontal = 4.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Text input placeholder (visual only)
            OutlinedTextField(
                value = "",
                onValueChange = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("Share your experience...") },
                readOnly = true,
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Click overlay button
            Button(
                onClick = onReviewClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Write Review")
            }
        }
    }
}
