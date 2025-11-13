package com.example.fyp_25_s4_23.control.usecases

import com.example.fyp_25_s4_23.domain.entities.UserAccount

class LogoutUser {
    operator fun invoke(currentUser: UserAccount?): UserAccount? = null
}

