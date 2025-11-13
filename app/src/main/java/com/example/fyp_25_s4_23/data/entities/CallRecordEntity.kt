package com.example.fyp_25_s4_23.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "start_time") val startTimeMillis: Long,
    @ColumnInfo(name = "end_time") val endTimeMillis: Long?,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "probability") val probability: Float,
    @ColumnInfo(name = "model_version") val modelVersion: String?
)

