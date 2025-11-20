package com.example.fyp_25_s4_23.control.usecases

import com.example.fyp_25_s4_23.data.repositories.UserRepository
import com.example.fyp_25_s4_23.domain.entities.UserAccount

class LoginUser(private val userRepository: UserRepository) {
    suspend operator fun invoke(username: String, password: String): UserAccount =
        userRepository.login(username, password)
}
