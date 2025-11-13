package com.example.fyp_25_s4_23.control.usecases

import com.example.fyp_25_s4_23.data.repositories.UserRepository
import com.example.fyp_25_s4_23.domain.entities.UserAccount
import com.example.fyp_25_s4_23.domain.valueobjects.UserRole

class RegisterUser(private val userRepository: UserRepository) {
    suspend operator fun invoke(
        username: String,
        password: String,
        displayName: String,
        role: UserRole
    ): UserAccount = userRepository.register(username, password, displayName, role)
}

