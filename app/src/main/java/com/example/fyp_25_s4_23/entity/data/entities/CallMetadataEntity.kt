package com.example.fyp_25_s4_23.entity.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_metadata",
    foreignKeys = [
        ForeignKey(
            entity = CallEntity::class,
            parentColumns = ["id"],
            childColumns = ["call_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["call_id"]), Index(value = ["phone_number"])]
)
data class CallMetadataEntity(
    @PrimaryKey @ColumnInfo(name = "call_id") val callId: String,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "contact_id") val contactId: String? = null,
    @ColumnInfo(name = "start_time_seconds") val startTimeSeconds: Long,
    @ColumnInfo(name = "end_time_seconds") val endTimeSeconds: Long?,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int? = null
)
