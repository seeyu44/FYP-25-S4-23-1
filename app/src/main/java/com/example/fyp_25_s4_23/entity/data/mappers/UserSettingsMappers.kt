package com.example.fyp_25_s4_23.entity.data.mappers

import com.example.fyp_25_s4_23.entity.data.entities.UserSettingsEntity
import com.example.fyp_25_s4_23.entity.domain.entities.UserSettings
import com.example.fyp_25_s4_23.entity.domain.valueobjects.AlertAction
import com.example.fyp_25_s4_23.entity.domain.valueobjects.AlertSeverity

fun UserSettingsEntity.toDomain(): UserSettings = UserSettings(
    threshold = detectionThreshold.toFloat(),
    realTimeDetectionEnabled = realTimeDetectionEnabled,
    allowBackgroundMonitoring = false,
    analyticsConsent = false,
    preferredAlertSeverity = AlertSeverity.WARNING,
    defaultAlertActions = setOf(AlertAction.NOTIFIED_USER),
    autoBlockUnknownNumbers = false,
    autoBlockRepeatOffenders = false
)

fun UserSettings.toEntity(userId: Long): UserSettingsEntity = UserSettingsEntity(
    userId = userId,
    realTimeDetectionEnabled = realTimeDetectionEnabled,
    detectionThreshold = threshold.toDouble()
)

