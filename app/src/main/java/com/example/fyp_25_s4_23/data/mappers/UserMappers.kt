package com.example.fyp_25_s4_23.data.mappers

import com.example.fyp_25_s4_23.data.entities.UserEntity
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import com.example.fyp_25_s4_23.entity.domain.valueobjects.UserRole

fun UserEntity.toDomain(): UserAccount = UserAccount(
    id = id,
    firebaseUid = null,
    username = username,
    displayName = displayName,
    role = UserRole.valueOf(role),
    createdAtSeconds = createdAtSeconds
)

fun UserAccount.toEntity(passwordHash: String): UserEntity = UserEntity(
    id = id,
    username = username,
    passwordHash = passwordHash,
    displayName = displayName,
    role = role.name,
    createdAtSeconds = createdAtSeconds
)

