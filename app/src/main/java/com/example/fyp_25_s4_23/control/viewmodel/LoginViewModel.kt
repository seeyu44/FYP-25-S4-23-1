package com.example.fyp_25_s4_23.control.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fyp_25_s4_23.data.remote.firebase.FirebaseAuthManager
import com.example.fyp_25_s4_23.entity.data.repositories.UserRepository
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
private var loginViewModelInstance: LoginViewModel? = null

/**
 * LoginViewModel handles all authentication-related logic:
 * - User login with Firebase
 * - User logout
 * - Session management
 * - Admin user initialization
 */
sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data class Success(val user: UserAccount) : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(
    private val firebaseAuthManager: FirebaseAuthManager,
    private val userRepository: UserRepository
) : ViewModel() {

    companion object {
        fun getInstance(
            firebaseAuthManager: FirebaseAuthManager,
            userRepository: UserRepository
        ): LoginViewModel {
            return loginViewModelInstance ?: synchronized(this) {
                loginViewModelInstance ?: LoginViewModel(firebaseAuthManager, userRepository).also {
                    loginViewModelInstance = it
                }
            }
        }
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    /**
     * Logs in user with email and password via Firebase
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val firebaseUser = firebaseAuthManager.login(email, password)
                // Create UserAccount from Firebase user
                val userAccount = UserAccount(
                    id = 0,  // Local DB ID will be assigned on first local save
                    firebaseUid = firebaseUser.uid,
                    username = firebaseUser.email ?: email,
                    displayName = firebaseUser.displayName ?: firebaseUser.email ?: "User",
                    role = com.example.fyp_25_s4_23.entity.domain.valueobjects.UserRole.REGISTERED,
                    createdAtSeconds = System.currentTimeMillis() / 1000
                )
                _loginState.value = LoginState.Success(userAccount)
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "Unknown error during login")
            }
        }
    }

    /**
     * Logs out current user and clears session
     */
    fun logout() {
        viewModelScope.launch {
            try {
                firebaseAuthManager.logout()
                _loginState.value = LoginState.Idle
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "Logout failed")
            }
        }
    }

    /**
     * Ensures default admin user exists in the database
     */
    fun ensureDefaultAdmin() {
        viewModelScope.launch {
            try {
                userRepository.ensureDefaultAdmin()
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Failed to create default admin: ${e.message}")
            }
        }
    }

    /**
     * Gets the currently logged in user from Firebase
     */
    fun getCurrentUser(): UserAccount? {
        return firebaseAuthManager.currentUser()?.let {
            UserAccount(
                id = 0,
                firebaseUid = it.uid,
                username = it.email ?: "unknown",
                displayName = it.displayName ?: it.email ?: "User",
                role = com.example.fyp_25_s4_23.entity.domain.valueobjects.UserRole.REGISTERED,
                createdAtSeconds = System.currentTimeMillis() / 1000
            )
        }
    }

    /**
     * Checks if user is currently logged in
     */
    fun isLoggedIn(): Boolean {
        return firebaseAuthManager.currentUser() != null
    }
}
