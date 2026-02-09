package com.example.fyp_25_s4_23.data.remote.firebase

import android.util.Log
import com.example.fyp_25_s4_23.entity.domain.entities.AppReview
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing app reviews in Firebase Firestore.
 * Handles both old and new review document formats.
 */
class ReviewRepository {
    private val db = FirebaseFirestore.getInstance()
    private val reviewsCollection = db.collection("reviews")

    /**
     * Submit a new review to Firebase.
     */
    suspend fun submitReview(review: AppReview): String {
        val reviewData = hashMapOf(
            "userId" to review.userId,
            "rating" to review.rating,
            "description" to review.description,
            "anonymous" to review.anonymous,
            "createdAt" to review.createdAt,
            "updatedAt" to review.updatedAt
        )

        val docRef = reviewsCollection.add(reviewData).await()
        return docRef.id
    }

    /**
     * Update an existing review.
     */
    suspend fun updateReview(reviewId: String, review: AppReview) {
        val reviewData = hashMapOf(
            "userId" to review.userId,
            "rating" to review.rating,
            "description" to review.description,
            "anonymous" to review.anonymous,
            "updatedAt" to System.currentTimeMillis() / 1000
        )

        reviewsCollection.document(reviewId).update(reviewData as Map<String, Any>).await()
    }

    /**
     * Helper function to extract timestamp from both old (Long) and new (Timestamp) formats
     */
    private fun getTimestampInSeconds(data: Any?): Long {
        return when {
            data is Long -> data // Old format: Unix timestamp in seconds
            data is Number -> data.toLong() // Fallback for other number types
            data != null -> {
                // Try to handle Firestore Timestamp by accessing seconds property
                try {
                    val secondsProperty = data.javaClass.getMethod("getSeconds").invoke(data)
                    (secondsProperty as? Number)?.toLong() ?: System.currentTimeMillis() / 1000
                } catch (e: Exception) {
                    // If it fails, use current time
                    System.currentTimeMillis() / 1000
                }
            }
            else -> System.currentTimeMillis() / 1000
        }
    }

    /**
     * Get all reviews for a specific user.
     */
    suspend fun getUserReviews(userId: String): List<AppReview> {
        val snapshot = reviewsCollection
            .whereEqualTo("userId", userId)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            try {
                AppReview(
                    id = doc.id,
                    userId = doc.getString("userId") ?: "",
                    rating = doc.getLong("rating")?.toInt() ?: 0,
                    anonymous = doc.getBoolean("anonymous") ?: false,
                    description = doc.getString("description") ?: "",
                    createdAt = getTimestampInSeconds(doc.get("createdAt")),
                    updatedAt = getTimestampInSeconds(doc.get("updatedAt"))
                )
            } catch (e: Exception) {
                Log.w("ReviewRepository", "Error parsing review ${doc.id}", e)
                null
            }
        }
    }

    /**
     * Get all reviews (admin only).
     * Handles both old and new document formats with moderation data.
     */
    suspend fun getAllReviews(): List<AppReview> {
        val snapshot = reviewsCollection.get().await()

        return snapshot.documents.mapNotNull { doc ->
            try {
                AppReview(
                    id = doc.id,
                    userId = doc.getString("userId") ?: "",
                    rating = doc.getLong("rating")?.toInt() ?: 0,
                    anonymous = doc.getBoolean("anonymous") ?: false,
                    description = doc.getString("description") ?: "",
                    createdAt = getTimestampInSeconds(doc.get("createdAt")),
                    updatedAt = getTimestampInSeconds(doc.get("updatedAt"))
                )
            } catch (e: Exception) {
                Log.w("ReviewRepository", "Error parsing review ${doc.id}", e)
                null
            }
        }
    }

    /**
     * Delete a review.
     */
    suspend fun deleteReview(reviewId: String) {
        reviewsCollection.document(reviewId).delete().await()
    }
}
