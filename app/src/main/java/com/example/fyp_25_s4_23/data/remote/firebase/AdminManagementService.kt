package com.example.fyp_25_s4_23.data.remote.firebase

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Service for admin-only Cloud Function calls.
 * Handles user management operations like disable, enable, and delete.
 */
class AdminManagementService {
    private val functions = FirebaseFunctions.getInstance()

    /**
     * List all Firebase Auth users (admin only)
     */
    suspend fun listAllUsers(): List<Map<String, Any>> = try {
        val result = functions.getHttpsCallable("listUsers").call().await()
        @Suppress("UNCHECKED_CAST")
        (result.data as? List<Map<String, Any>>) ?: emptyList()
    } catch (e: Exception) {
        Log.e("AdminManagement", "Error listing users", e)
        throw e
    }

    /**
     * Disable or enable a user account (admin only)
     * @param uid Firebase UID of the user
     * @param disabled true to disable, false to enable
     */
    suspend fun setUserDisabled(uid: String, disabled: Boolean): Boolean = try {
        val data = mapOf(
            "uid" to uid,
            "disabled" to disabled
        )
        val result = functions.getHttpsCallable("setUserDisabled").call(data).await()
        @Suppress("UNCHECKED_CAST")
        (result.data as? Map<String, Any>)?.get("success") as? Boolean ?: false
    } catch (e: Exception) {
        Log.e("AdminManagement", "Error setting user disabled status", e)
        throw e
    }

    /**
     * Permanently delete a user account (admin only)
     * @param uid Firebase UID of the user to delete
     */
    suspend fun deleteUser(uid: String): Boolean = try {
        val data = mapOf("uid" to uid)
        val result = functions.getHttpsCallable("deleteUser").call(data).await()
        @Suppress("UNCHECKED_CAST")
        (result.data as? Map<String, Any>)?.get("success") as? Boolean ?: false
    } catch (e: Exception) {
        Log.e("AdminManagement", "Error deleting user", e)
        throw e
    }

    /**
     * Delete a review (admin only)
     * @param reviewId Firestore document ID of the review
     */
    suspend fun deleteReview(reviewId: String): Boolean = try {
        val data = mapOf("reviewId" to reviewId)
        val result = functions.getHttpsCallable("adminDeleteReview").call(data).await()
        @Suppress("UNCHECKED_CAST")
        (result.data as? Map<String, Any>)?.get("success") as? Boolean ?: false
    } catch (e: Exception) {
        Log.e("AdminManagement", "Error deleting review", e)
        throw e
    }
}
