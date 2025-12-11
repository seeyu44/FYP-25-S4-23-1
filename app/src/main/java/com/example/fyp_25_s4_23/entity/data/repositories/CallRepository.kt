package com.example.fyp_25_s4_23.entity.data.repositories

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
        return callDao.dailyAggregates(startMillis / 1000, endMillis / 1000, threshold)
    }

    /**
     * Get weekly aggregated statistics
     */
    suspend fun weeklyAggregates(startMillis: Long, endMillis: Long, threshold: Double = 0.5): List<com.example.fyp_25_s4_23.entity.data.dao.AggregateResult> {
        return callDao.weeklyAggregates(startMillis / 1000, endMillis / 1000, threshold)
    }
}

