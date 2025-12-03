package com.example.fyp_25_s4_23.data.mappers

import com.example.fyp_25_s4_23.data.entities.UserSettingsEntity
import com.example.fyp_25_s4_23.domain.entities.UserSettings

fun UserSettingsEntity.toDomain(): UserSettings = UserSettings(
    realTimeDetectionEnabled = realTimeDetectionEnabled
)

fun UserSettings.toEntity(userId: Long): UserSettingsEntity = UserSettingsEntity(
    userId = userId,
    realTimeDetectionEnabled = realTimeDetectionEnabled
)

