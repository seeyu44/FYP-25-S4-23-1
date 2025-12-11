package com.example.fyp_25_s4_23.entity.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "detection_results",
    foreignKeys = [
        ForeignKey(
            entity = CallEntity::class,
            parentColumns = ["id"],
            childColumns = ["call_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["call_id"]), Index(value = ["timestamp_millis"])]
)
data class DetectionResultEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "call_id") val callId: String,
    @ColumnInfo(name = "probability") val probability: Float,
    @ColumnInfo(name = "is_deepfake") val isDeepfake: Boolean,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long,
    @ColumnInfo(name = "model_version") val modelVersion: String,
    @ColumnInfo(name = "confidence_level") val confidenceLevel: String = "MEDIUM"
)
