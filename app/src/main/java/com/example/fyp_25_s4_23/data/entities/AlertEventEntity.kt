package com.example.fyp_25_s4_23.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "call_id") val callId: String,
    @ColumnInfo(name = "trigger_seconds") val triggerSeconds: Long,
    @ColumnInfo(name = "severity") val severity: String,
    @ColumnInfo(name = "probability") val probability: Float,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "actions") val actions: String,
    @ColumnInfo(name = "acknowledged") val acknowledged: Boolean,
    @ColumnInfo(name = "ack_seconds") val acknowledgedSeconds: Long?
)

