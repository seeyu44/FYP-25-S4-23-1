package com.example.fyp_25_s4_23.entity.domain.entities

enum class ContactLabel { NONE, WHITE, BLACK }

data class Contact(
    val id: String,
    val userId: String = "",
    val displayName: String? = null,
    val phoneNumber: String = "",
    val label: ContactLabel = ContactLabel.NONE
)
