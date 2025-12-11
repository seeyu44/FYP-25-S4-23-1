package com.example.fyp_25_s4_23.entity.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calls",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["user_id"])]
)
data class CallEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: Long?,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "created_millis") val createdMillis: Long,
    @ColumnInfo(name = "updated_millis") val updatedMillis: Long,
    @ColumnInfo(name = "notes") val notes: String? = null
)
