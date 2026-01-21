package com.example.fyp_25_s4_23.entity.domain.entities

/**
 * Represents a user's review of the application.
 */
data class AppReview(
    val id: String = "",
    val userId: String,
    val rating: Int, // 1-5 stars
    val description: String,
    val anonymous: Boolean = false,
    val createdAt: Long = System.currentTimeMillis() / 1000,
    val updatedAt: Long = System.currentTimeMillis() / 1000
) {
    init {
        require(rating in 1..5) { "Rating must be between 1 and 5" }
        require(description.isNotBlank()) { "Description cannot be blank" }
    }
}
