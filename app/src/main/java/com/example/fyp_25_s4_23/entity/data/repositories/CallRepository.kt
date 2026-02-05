package com.example.fyp_25_s4_23.entity.data.repositories

import android.util.Log
import com.example.fyp_25_s4_23.entity.data.dao.CallDao
import com.example.fyp_25_s4_23.entity.data.dao.CallMetadataDao
import com.example.fyp_25_s4_23.entity.data.dao.DetectionResultDao
import com.example.fyp_25_s4_23.entity.data.mappers.toDomain
import com.example.fyp_25_s4_23.entity.data.mappers.toEntities
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord

class CallRepository(
    private val callDao: CallDao,
    private val callMetadataDao: CallMetadataDao,
    private val detectionResultDao: DetectionResultDao
) {
    /**
     * Insert or update a call record with all related data (metadata + detections)
     */
    suspend fun upsert(record: CallRecord, userId: Long? = null) {
        val (callEntity, metadataEntity, detectionEntities) = record.toEntities(userId)
        
        callDao.insert(callEntity)
        callMetadataDao.insert(metadataEntity)
        detectionEntities.forEach { detection ->
            detectionResultDao.insert(detection)
        }
    }

    /**
     * Get all recent calls with complete data
     */
    suspend fun listRecent(): List<CallRecord> {
        return callDao.getAllCompleteCallRecords().map { it.toDomain() }
    }

    /**
     * Get a specific call by ID with all related data
     */
    suspend fun getById(callId: String): CallRecord? {
        return callDao.getCompleteCallRecord(callId)?.toDomain()
    }

    /**
     * Get calls for a specific user
     */
    suspend fun getByUserId(userId: Long): List<CallRecord> {
        return callDao.getByUserId(userId).mapNotNull { call ->
            callDao.getCompleteCallRecord(call.id)?.toDomain()
        }
    }

    /**
     * Clear all call data
     */
    suspend fun clear() {
        callDao.clear()
        callMetadataDao.clear()
        detectionResultDao.clear()
    }

    /**
     * Get daily aggregated statistics
     */
    suspend fun dailyAggregates(startMillis: Long, endMillis: Long, threshold: Double = 0.5): List<com.example.fyp_25_s4_23.entity.data.dao.AggregateResult> {
        val startSeconds = startMillis / 1000
        val endSeconds = endMillis / 1000
        
        // Debug: Check database stats
        val totalCalls = callDao.getTotalCallMetadataCount()
        val incomingCalls = callDao.getIncomingCallCount()
        val dateRange = callDao.getCallDateRange()
        
        Log.i("CallRepository", "Database stats - Total: $totalCalls, Incoming: $incomingCalls")
        Log.i("CallRepository", "Date range in DB - Min: ${dateRange?.minTime}, Max: ${dateRange?.maxTime}")
        Log.i("CallRepository", "Query range - Start: $startSeconds, End: $endSeconds")
        
        return callDao.dailyAggregates(startSeconds, endSeconds, threshold)
    }

    /**
     * Get weekly aggregated statistics
     */
    suspend fun weeklyAggregates(startMillis: Long, endMillis: Long, threshold: Double = 0.5): List<com.example.fyp_25_s4_23.entity.data.dao.AggregateResult> {
        val startSeconds = startMillis / 1000
        val endSeconds = endMillis / 1000
        
        // Debug: Check database stats
        val totalCalls = callDao.getTotalCallMetadataCount()
        val incomingCalls = callDao.getIncomingCallCount()
        val dateRange = callDao.getCallDateRange()
        
        Log.i("CallRepository", "Database stats - Total: $totalCalls, Incoming: $incomingCalls")
        Log.i("CallRepository", "Date range in DB - Min: ${dateRange?.minTime}, Max: ${dateRange?.maxTime}")
        Log.i("CallRepository", "Query range - Start: $startSeconds, End: $endSeconds")
        
        return callDao.weeklyAggregates(startSeconds, endSeconds, threshold)
    }
}

