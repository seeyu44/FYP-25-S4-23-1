package com.example.fyp_25_s4_23.entity.data.mappers

import com.example.fyp_25_s4_23.entity.data.entities.ContactEntity
import com.example.fyp_25_s4_23.domain.entities.Contact
import com.example.fyp_25_s4_23.domain.entities.ContactLabel

fun ContactEntity.toDomain(): Contact {
    return Contact(
        id = this.id.toString(),
        displayName = this.displayName,
        phoneNumber = this.phoneNumber,
        label = try {
            ContactLabel.valueOf(this.label)
        } catch (e: Exception) {
            ContactLabel.NONE
        }
    )
}

fun Contact.toEntity(phoneNumber: String = ""): ContactEntity {
    return ContactEntity(
        id = this.id.toIntOrNull() ?: 0,
        displayName = this.displayName,
        phoneNumber = if (phoneNumber.isEmpty()) this.phoneNumber else phoneNumber,
        label = this.label.name
    )
}