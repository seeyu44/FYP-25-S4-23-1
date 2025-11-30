package com.example.fyp_25_s4_23.entity.data.repositories

import com.example.fyp_25_s4_23.entity.data.dao.CallRecordDao
import com.example.fyp_25_s4_23.entity.data.mappers.toDomain
import com.example.fyp_25_s4_23.entity.data.mappers.toEntity
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord

class CallRepository(private val callRecordDao: CallRecordDao) {
    suspend fun upsert(record: CallRecord) = callRecordDao.upsert(record.toEntity())
    suspend fun listRecent(): List<CallRecord> = callRecordDao.listRecent().map { it.toDomain() }
}

