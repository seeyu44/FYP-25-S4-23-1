package com.example.fyp_25_s4_23.data.remote.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

object FirebaseAuthManager {

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun isLoggedIn(): Boolean = auth.currentUser != null

    suspend fun login(
        email: String,
        password: String
    ): FirebaseUser {
        val result = auth
            .signInWithEmailAndPassword(email, password)
            .await()

        return result.user
            ?: throw IllegalStateException("Login failed: user is null")
    }

    suspend fun register(
        email: String,
        password: String
    ): FirebaseUser {
        val result = auth
            .createUserWithEmailAndPassword(email, password)
            .await()

        return result.user
            ?: throw IllegalStateException("Registration failed")
    }

    suspend fun sendEmailVerification() {
        val user = auth.currentUser
            ?: throw IllegalStateException("No logged-in user")

        user.sendEmailVerification().await()
    }

    suspend fun logout() {
        auth.signOut()
    }

    suspend fun getIdToken(): String {
        val user = auth.currentUser
            ?: throw IllegalStateException("User not logged in")

        return user.getIdToken(true).await().token
            ?: throw IllegalStateException("Failed to fetch ID token")
    }
}
