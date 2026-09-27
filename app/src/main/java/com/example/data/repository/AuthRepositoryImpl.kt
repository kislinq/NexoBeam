package com.example.data.repository

import com.example.core.config.AppConfig
import com.example.core.model.AppResult
import com.example.data.local.NexoDatabase
import com.example.data.local.SessionManager
import com.example.data.local.entities.ProfileEntity
import com.example.data.remote.SupabaseApiClient
import com.example.domain.model.AuthState
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AuthRepositoryImpl(
    private val api: SupabaseApiClient,
    private val sessionManager: SessionManager,
    private val database: NexoDatabase
) : AuthRepository {

    override val currentUserIdFlow: Flow<String?> = sessionManager.userIdFlow
    override val currentUserEmailFlow: Flow<String?> = sessionManager.userEmailFlow

    private val _authStateFlow = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authStateFlow: Flow<AuthState> = _authStateFlow.asStateFlow()

    override suspend fun restoreSession(): AuthState = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId()
        val email = sessionManager.getUserEmail() ?: ""
        val token = sessionManager.getAccessToken()

        if (userId.isNullOrBlank() || token.isNullOrBlank()) {
            val state = AuthState.Unauthenticated
            _authStateFlow.value = state
            return@withContext state
        }

        // Check if token needs refresh
        val refreshToken = sessionManager.getRefreshToken()
        if (!refreshToken.isNullOrBlank() && AppConfig.isConfigured) {
            val refreshRes = api.refreshToken(refreshToken)
            refreshRes.onSuccess { json ->
                val newToken = json.optString("access_token")
                val newRefresh = json.optString("refresh_token")
                if (newToken.isNotBlank()) {
                    sessionManager.saveSession(newToken, newRefresh, userId, email)
                }
            }
        }

        // Verify profile
        val cachedProfile = database.profileDao().getProfileById(userId)
        val state = if (cachedProfile != null && cachedProfile.username.isNotBlank()) {
            AuthState.Authenticated(userId, email)
        } else {
            // Check remote profile
            val remoteRes = api.getProfile(userId, token)
            val remoteJson = remoteRes.getOrNull()
            if (remoteJson != null && remoteJson.optString("username").isNotBlank()) {
                val profile = ProfileEntity(
                    id = userId,
                    username = remoteJson.optString("username"),
                    displayName = remoteJson.optString("display_name"),
                    avatarUrl = remoteJson.optString("avatar_url").ifEmpty { null },
                    bio = remoteJson.optString("bio").ifEmpty { null },
                    lastSeen = System.currentTimeMillis()
                )
                database.profileDao().upsertProfile(profile)
                AuthState.Authenticated(userId, email)
            } else {
                AuthState.RequiresProfileSetup(userId, email)
            }
        }

        _authStateFlow.value = state
        state
    }

    override suspend fun register(email: String, password: String): AppResult<AuthState> = withContext(Dispatchers.IO) {
        val result = api.signUp(email, password)
        result.fold(
            onSuccess = { json ->
                val user = json.optJSONObject("user") ?: json
                val userId = user.optString("id").ifBlank { "user_" + System.currentTimeMillis().toString().takeLast(6) }
                var token = json.optString("access_token")
                var refreshToken = json.optString("refresh_token")

                // If Supabase did not return an access_token directly (e.g. Email confirmation requirement),
                // attempt immediate signIn:
                if (token.isBlank() && AppConfig.isConfigured) {
                    val loginRes = api.signIn(email, password)
                    loginRes.onSuccess { loginJson ->
                        token = loginJson.optString("access_token")
                        refreshToken = loginJson.optString("refresh_token")
                    }.onFailure { err ->
                        val msg = err.message ?: ""
                        if (msg.contains("Email not confirmed", ignoreCase = true) || msg.contains("почт", ignoreCase = true)) {
                            return@fold AppResult.Error(
                                "Учетная запись создана! В Supabase включено подтверждение почты. Подтвердите email в письме или отключите 'Confirm email' в консоли Supabase: Authentication > Providers > Email."
                            )
                        }
                    }
                }

                sessionManager.saveSession(token, refreshToken, userId, email)

                // Check if profile exists already
                val defaultUsername = email.substringBefore("@").filter { it.isLetterOrDigit() || it == '_' }
                val profile = ProfileEntity(
                    id = userId,
                    username = defaultUsername,
                    displayName = defaultUsername.replaceFirstChar { it.uppercase() },
                    avatarUrl = null,
                    bio = "Новый узел NexoBeam",
                    lastSeen = System.currentTimeMillis()
                )
                database.profileDao().upsertProfile(profile)

                val newState = AuthState.RequiresProfileSetup(userId, email)
                _authStateFlow.value = newState
                AppResult.Success(newState)
            },
            onFailure = {
                val err = it.message?.ifBlank { null } ?: "Ошибка при подключении к серверу аутентификации"
                AppResult.Error(err)
            }
        )
    }

    override suspend fun login(email: String, password: String): AppResult<AuthState> = withContext(Dispatchers.IO) {
        val result = api.signIn(email, password)
        result.fold(
            onSuccess = { json ->
                val user = json.optJSONObject("user")
                val userId = user?.optString("id") ?: "user_me"
                val token = json.optString("access_token")
                val refreshToken = json.optString("refresh_token")

                sessionManager.saveSession(token, refreshToken, userId, email)

                // Fetch remote profile if available
                val profileRes = api.getProfile(userId, token)
                val profileJson = profileRes.getOrNull()
                val profile = if (profileJson != null) {
                    ProfileEntity(
                        id = userId,
                        username = profileJson.optString("username", email.substringBefore("@")),
                        displayName = profileJson.optString("display_name", email.substringBefore("@")),
                        avatarUrl = profileJson.optString("avatar_url").ifEmpty { null },
                        bio = profileJson.optString("bio").ifEmpty { null },
                        lastSeen = System.currentTimeMillis()
                    )
                } else {
                    val defaultUsername = email.substringBefore("@").filter { it.isLetterOrDigit() || it == '_' }
                    ProfileEntity(
                        id = userId,
                        username = defaultUsername,
                        displayName = defaultUsername.replaceFirstChar { it.uppercase() },
                        avatarUrl = null,
                        bio = "Терминал активен",
                        lastSeen = System.currentTimeMillis()
                    )
                }
                database.profileDao().upsertProfile(profile)

                val newState = if (profile.username.isNotBlank() && profile.displayName.isNotBlank()) {
                    AuthState.Authenticated(userId, email)
                } else {
                    AuthState.RequiresProfileSetup(userId, email)
                }

                _authStateFlow.value = newState
                AppResult.Success(newState)
            },
            onFailure = {
                val err = it.message?.ifBlank { null } ?: "Неверный логин или пароль"
                AppResult.Error(err)
            }
        )
    }

    override suspend fun checkUsernameAvailability(username: String): AppResult<Boolean> = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: ""
        val token = sessionManager.getAccessToken()
        val res = api.checkUsernameAvailable(username, userId, token)
        res.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(it.message ?: "Ошибка проверки имени пользователя") }
        )
    }

    override suspend fun logout(): AppResult<Unit> {
        sessionManager.clearSession()
        _authStateFlow.value = AuthState.Unauthenticated
        return AppResult.Success(Unit)
    }

    override suspend fun getActiveSessionUserId(): String? {
        return sessionManager.getUserId()
    }
}
