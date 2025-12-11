package com.example.fyp_25_s4_23.entity.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alerts",
    foreignKeys = [
        ForeignKey(
            entity = CallEntity::class,
            parentColumns = ["id"],
            childColumns = ["call_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DetectionResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["detection_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["call_id"]), Index(value = ["detection_id"]), Index(value = ["trigger_seconds"])]
)
data class AlertEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "call_id") val callId: String,
    @ColumnInfo(name = "detection_id") val detectionId: String? = null,
    @ColumnInfo(name = "trigger_seconds") val triggerSeconds: Long,
    @ColumnInfo(name = "severity") val severity: String,
    @ColumnInfo(name = "probability") val probability: Float,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "actions") val actions: String,
    @ColumnInfo(name = "acknowledged") val acknowledged: Boolean,
    @ColumnInfo(name = "ack_seconds") val acknowledgedSeconds: Long? = null
)

