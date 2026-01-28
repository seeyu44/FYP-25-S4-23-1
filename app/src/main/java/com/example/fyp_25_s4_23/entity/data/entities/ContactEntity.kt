package com.example.fyp_25_s4_23.entity.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val displayName: String?,
    val phoneNumber: String,
    val label: String
)