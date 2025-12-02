package com.example.fyp_25_s4_23.entity.data.repositories

import com.example.fyp_25_s4_23.entity.data.dao.UserSettingsDao
import com.example.fyp_25_s4_23.entity.data.mappers.toDomain
import com.example.fyp_25_s4_23.entity.data.mappers.toEntity
import com.example.fyp_25_s4_23.entity.domain.entities.UserSettings

class SettingsRepository(private val dao: UserSettingsDao) {
    suspend fun get(userId: Long): UserSettings =
        dao.get(userId)?.toDomain() ?: UserSettings()

    suspend fun update(userId: Long, settings: UserSettings) {
        dao.upsert(settings.toEntity(userId))
    }
}

