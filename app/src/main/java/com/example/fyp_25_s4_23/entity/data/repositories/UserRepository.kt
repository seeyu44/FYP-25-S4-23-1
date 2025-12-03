package com.example.fyp_25_s4_23.data.repositories

import com.example.fyp_25_s4_23.data.dao.UserDao
import com.example.fyp_25_s4_23.data.entities.UserEntity
import com.example.fyp_25_s4_23.data.mappers.toDomain
import com.example.fyp_25_s4_23.domain.entities.UserAccount
import com.example.fyp_25_s4_23.domain.valueobjects.UserRole
import com.example.fyp_25_s4_23.util.PasswordHasher

class UserRepository(private val userDao: UserDao) {

    suspend fun ensureDefaultAdmin(): UserAccount {
        val existing = userDao.findByUsername(DEFAULT_ADMIN_USERNAME)
        if (existing != null) return existing.toDomain()
        val entity = UserEntity(
            username = DEFAULT_ADMIN_USERNAME,
            passwordHash = PasswordHasher.hash("admin"),
            displayName = "Administrator",
            role = UserRole.ADMIN.name
        )
        val id = userDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    suspend fun register(
        username: String,
        password: String,
        displayName: String,
        role: UserRole
    ): UserAccount {
        check(username.isNotBlank()) { "Username cannot be blank" }
        check(password.length >= 4) { "Password too short" }
        check(displayName.isNotBlank()) { "Display name required" }

        if (userDao.findByUsername(username) != null) {
            throw IllegalStateException("Username already exists")
        }

        val hashed = PasswordHasher.hash(password)
        val entity = UserEntity(
            username = username,
            passwordHash = hashed,
            displayName = displayName,
            role = role.name
        )
        val id = userDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    suspend fun login(username: String, password: String): UserAccount {
        val entity = userDao.findByUsername(username) ?: throw IllegalStateException("User not found")
        val hashed = PasswordHasher.hash(password)
        if (entity.passwordHash != hashed) {
            throw IllegalStateException("Invalid credentials")
        }
        return entity.toDomain()
    }

    suspend fun listUsers(): List<UserAccount> = userDao.listUsers().map { it.toDomain() }

    companion object {
        private const val DEFAULT_ADMIN_USERNAME = "admin"
    }
}

