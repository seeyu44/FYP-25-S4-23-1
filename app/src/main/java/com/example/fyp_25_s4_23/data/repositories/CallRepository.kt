package com.example.fyp_25_s4_23.data.repositories

import android.util.Log
import com.example.fyp_25_s4_23.data.dao.CallRecordDao
import com.example.fyp_25_s4_23.data.mappers.toDomain
import com.example.fyp_25_s4_23.data.mappers.toEntity
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord

class CallRepository(private val callRecordDao: CallRecordDao) {
    suspend fun upsert(record: CallRecord) = callRecordDao.upsert(record.toEntity())
    suspend fun listRecent(): List<CallRecord> = callRecordDao.listRecent().map { it.toDomain() }

    suspend fun dailyAggregates(startMillis: Long, endMillis: Long, threshold: Double = 0.5): List<com.example.fyp_25_s4_23.data.dao.AggregateResult> {
        // Convert milliseconds to seconds for database comparison
        val startSeconds = startMillis / 1000
        val endSeconds = endMillis / 1000
        Log.i("CallRepository", "dailyAggregates: startSeconds=$startSeconds, endSeconds=$endSeconds")
        val result = callRecordDao.dailyAggregates(startSeconds, endSeconds, threshold)
        Log.i("CallRepository", "dailyAggregates result size: ${result.size}")
        return result
    }

    suspend fun weeklyAggregates(startMillis: Long, endMillis: Long, threshold: Double = 0.5): List<com.example.fyp_25_s4_23.data.dao.AggregateResult> {
        // Convert milliseconds to seconds for database comparison
        val startSeconds = startMillis / 1000
        val endSeconds = endMillis / 1000
        Log.i("CallRepository", "weeklyAggregates: startSeconds=$startSeconds, endSeconds=$endSeconds")
        val result = callRecordDao.weeklyAggregates(startSeconds, endSeconds, threshold)
        Log.i("CallRepository", "weeklyAggregates result size: ${result.size}")
        return result
    }
}

