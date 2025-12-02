package com.example.fyp_25_s4_23.entity.data.mappers

import com.example.fyp_25_s4_23.entity.data.entities.UserSettingsEntity
import com.example.fyp_25_s4_23.entity.domain.entities.UserSettings

fun UserSettingsEntity.toDomain(): UserSettings = UserSettings(
    realTimeDetectionEnabled = realTimeDetectionEnabled
)

fun UserSettings.toEntity(userId: Long): UserSettingsEntity = UserSettingsEntity(
    userId = userId,
    realTimeDetectionEnabled = realTimeDetectionEnabled
)

