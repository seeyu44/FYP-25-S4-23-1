package com.example.fyp_25_s4_23.data.remote.firebase

import com.example.fyp_25_s4_23.domain.entities.AuditLog
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class CloudFunctionsHelper {
    private val functions = FirebaseFunctions.getInstance()

    suspend fun getAuditLogs(page: Int, pageSize: Int, search: String?): List<AuditLog> {
        val data = hashMapOf(
            "page" to page,
            "pageSize" to pageSize,
            "search" to (search ?: "")
        )
        val result = functions
            .getHttpsCallable("getAuditLogs")
            .call(data)
            .await()
        val logs = result.data as? List<*> ?: return emptyList()
        return logs.mapNotNull { item ->
            val map = item as? Map<String, Any> ?: return@mapNotNull null
            val rawTimestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L
            val timestampSeconds = if (rawTimestamp > 1000000000000L) rawTimestamp / 1000 else rawTimestamp
            AuditLog(
                id = map["id"] as? String ?: "",
                action = map["action"] as? String ?: "",
                actor = map["actor"] as? String ?: "",
                target = map["target"] as? String ?: "",
                timestamp = timestampSeconds,
                details = map["details"] as? Map<String, Any> ?: emptyMap()
            )
        }
    }
}
