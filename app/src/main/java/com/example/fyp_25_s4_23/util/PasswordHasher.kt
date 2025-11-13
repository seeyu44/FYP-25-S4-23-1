package com.example.fyp_25_s4_23.util

import java.security.MessageDigest

object PasswordHasher {
    fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = digest.digest(input.toByteArray())
        return hashed.joinToString(separator = "") { byte ->
            ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1)
        }
    }
}

