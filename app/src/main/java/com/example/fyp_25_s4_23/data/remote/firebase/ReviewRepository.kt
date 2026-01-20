package com.example.fyp_25_s4_23.data.remote.firebase

import com.example.fyp_25_s4_23.entity.domain.entities.AppReview
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing app reviews in Firebase Firestore.
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
            "updatedAt" to System.currentTimeMillis() / 1000
        )

        reviewsCollection.document(reviewId).update(reviewData as Map<String, Any>).await()
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
                    description = doc.getString("description") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0,
                    updatedAt = doc.getLong("updatedAt") ?: 0
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Get all reviews (admin only).
     */
    suspend fun getAllReviews(): List<AppReview> {
        val snapshot = reviewsCollection.get().await()

        return snapshot.documents.mapNotNull { doc ->
            try {
                AppReview(
                    id = doc.id,
                    userId = doc.getString("userId") ?: "",
                    rating = doc.getLong("rating")?.toInt() ?: 0,
                    description = doc.getString("description") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0,
                    updatedAt = doc.getLong("updatedAt") ?: 0
                )
            } catch (e: Exception) {
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
