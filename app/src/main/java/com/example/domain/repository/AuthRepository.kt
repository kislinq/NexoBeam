package com.example.domain.repository

import com.example.core.model.AppResult
import com.example.domain.model.AuthState
import com.example.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserIdFlow: Flow<String?>
    val currentUserEmailFlow: Flow<String?>
    val authStateFlow: Flow<AuthState>

    suspend fun register(email: String, password: String): AppResult<AuthState>
    suspend fun login(email: String, password: String): AppResult<AuthState>
    suspend fun logout(): AppResult<Unit>
    suspend fun restoreSession(): AuthState
    suspend fun checkUsernameAvailability(username: String): AppResult<Boolean>
    suspend fun getActiveSessionUserId(): String?
}
