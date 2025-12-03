package com.example.fyp_25_s4_23.data.repositories

import com.example.fyp_25_s4_23.data.dao.AlertEventDao
import com.example.fyp_25_s4_23.data.mappers.toDomain
import com.example.fyp_25_s4_23.data.mappers.toEntity
import com.example.fyp_25_s4_23.domain.entities.AlertEvent

class AlertRepository(private val alertEventDao: AlertEventDao) {
    suspend fun upsert(event: AlertEvent) = alertEventDao.upsert(event.toEntity())
    suspend fun listRecent(): List<AlertEvent> = alertEventDao.listRecent().map { it.toDomain() }
}

