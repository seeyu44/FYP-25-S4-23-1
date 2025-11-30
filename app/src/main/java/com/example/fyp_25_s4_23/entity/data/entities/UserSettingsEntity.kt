package com.example.fyp_25_s4_23.entity.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "realtime_detection_enabled") val realTimeDetectionEnabled: Boolean = true
)

