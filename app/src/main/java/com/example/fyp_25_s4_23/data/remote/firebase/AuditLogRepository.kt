package com.example.fyp_25_s4_23.data.remote.firebase

import android.util.Log
import com.example.fyp_25_s4_23.domain.entities.AuditLog
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing audit logs in Firebase Firestore.
 */
class AuditLogRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auditLogsCollection = db.collection("audit_logs")
    private val cloudFunctionsHelper = CloudFunctionsHelper()

    /**
     * Get audit logs from cloud function with pagination and search.
     */
    suspend fun getAuditLogsPaged(page: Int, pageSize: Int, search: String?): List<AuditLog> {
        return try {
            cloudFunctionsHelper.getAuditLogs(page, pageSize, search)
        } catch (e: Exception) {
            Log.e("AuditLogRepository", "Error fetching audit logs from cloud function", e)
            emptyList()
        }
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
}
