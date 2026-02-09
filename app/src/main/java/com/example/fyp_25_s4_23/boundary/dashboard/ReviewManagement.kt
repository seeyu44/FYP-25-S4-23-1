package com.example.fyp_25_s4_23.boundary.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.domain.entities.AppReview

/**
 * Data class combining review with user display information
 */
data class ReviewWithUserInfo(
    val review: AppReview,
    val userDisplayName: String
)

/**
 * Review management composable for admin dashboard.
 * Allows searching, filtering by rating, and deleting reviews.
 */
@Composable
fun ReviewManagement(
    reviews: List<ReviewWithUserInfo>,
    onDeleteReview: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedRating by remember { mutableStateOf<Int?>(null) }
    var currentPage by remember { mutableStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var reviewToDelete by remember { mutableStateOf<ReviewWithUserInfo?>(null) }

    val reviewsPerPage = 5

    // Filter reviews based on search and rating
    val filteredReviews = remember(reviews, searchQuery, selectedRating) {
        reviews.filter { item ->
            val matchesSearch = searchQuery.isBlank() || 
                item.userDisplayName.contains(searchQuery, ignoreCase = true) ||
                item.review.userId.contains(searchQuery, ignoreCase = true)
            
            val matchesRating = selectedRating == null || item.review.rating == selectedRating
            
            matchesSearch && matchesRating
        }
    }

    // Calculate pagination
    val totalPages = (filteredReviews.size + reviewsPerPage - 1) / reviewsPerPage
    val paginatedReviews = remember(filteredReviews, currentPage) {
        val startIndex = currentPage * reviewsPerPage
        val endIndex = minOf(startIndex + reviewsPerPage, filteredReviews.size)
        filteredReviews.subList(startIndex, endIndex)
    }

    // Reset page to 0 when filters change
    LaunchedEffect(filteredReviews.size) {
        currentPage = 0
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            "Review Management",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search name or UID") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
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

            // Rating Filter Dropdown
            var dropdownExpanded by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(
                        if (selectedRating != null) "★ $selectedRating" else "Rating",
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Ratings") },
                        onClick = {
                            selectedRating = null
                            dropdownExpanded = false
                        }
                    )
                    repeat(5) { index ->
                        val rating = index + 1
                        DropdownMenuItem(
                            text = { Text("★".repeat(rating)) },
                            onClick = {
                                selectedRating = rating
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Reviews List
        if (filteredReviews.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank() && selectedRating == null) 
                        "No reviews yet" 
                    else 
                        "No reviews match your filters",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = paginatedReviews,
                    key = { it.review.id }
                ) { reviewItem ->
                    ReviewCard(
                        review = reviewItem,
                        onDelete = {
                            reviewToDelete = reviewItem
                            showDeleteConfirm = true
                        }
                    )
                }
            }

            // Pagination Controls
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text("← Previous")
                    }

                    Text(
                        text = "Page ${currentPage + 1} of $totalPages",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Button(
                        onClick = { if (currentPage < totalPages - 1) currentPage++ },
                        enabled = currentPage < totalPages - 1,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text("Next →")
                    }
                }
            }
        }

        // Summary
        Text(
            text = "Showing ${paginatedReviews.size} of ${filteredReviews.size} reviews",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm && reviewToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Review") },
            text = {
                Text(
                    "Are you sure you want to delete this review? This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteReview(reviewToDelete!!.review.id)
                        showDeleteConfirm = false
                        reviewToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReviewCard(
    review: ReviewWithUserInfo,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = review.userDisplayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID: ${review.review.userId.take(12)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete review",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Rating Stars
            Row(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .background(
                        color = Color(0xFFFFA500).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(review.review.rating) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Star",
                        tint = Color(0xFFFFA500),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "${review.review.rating}/5",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Review Comment
            Text(
                text = review.review.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Metadata
            Text(
                text = "Submitted ${formatTime(review.review.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Simple time formatter for review timestamps
 */
private fun formatTime(seconds: Long): String {
    if (seconds == 0L) return "just now"
    
    val now = System.currentTimeMillis() / 1000
    val diff = now - seconds
    
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 604800 -> "${diff / 86400}d ago"
        else -> "${diff / 604800}w ago"
    }
}
