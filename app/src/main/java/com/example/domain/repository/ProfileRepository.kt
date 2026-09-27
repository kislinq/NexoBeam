package com.example.domain.repository

import android.net.Uri
import com.example.core.model.AppResult
import com.example.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfile(userId: String): Flow<UserProfile?>
    suspend fun getProfile(userId: String): AppResult<UserProfile>
    suspend fun saveProfile(profile: UserProfile): AppResult<Unit>
    suspend fun uploadAvatar(userId: String, imageUri: Uri): AppResult<String>
    suspend fun updateAvatar(userId: String, avatarUrl: String): AppResult<Unit>
    suspend fun updatePresence(userId: String): AppResult<Unit>
    suspend fun searchUsers(query: String): AppResult<List<UserProfile>>
}
