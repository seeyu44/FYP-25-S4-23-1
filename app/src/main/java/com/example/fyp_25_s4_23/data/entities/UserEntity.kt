package com.example.fyp_25_s4_23.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "username", index = true) val username: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long = System.currentTimeMillis()
)

