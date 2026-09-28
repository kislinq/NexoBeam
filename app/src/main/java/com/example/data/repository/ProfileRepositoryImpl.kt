package com.example.data.repository

import android.net.Uri
import com.example.NexoApplication
import com.example.core.config.AppConfig
import com.example.core.model.AppResult
import com.example.core.util.ImageCompressor
import com.example.data.local.NexoDatabase
import com.example.data.local.SessionManager
import com.example.data.local.entities.ProfileEntity
import com.example.data.remote.SupabaseApiClient
import com.example.domain.model.UserProfile
import com.example.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ProfileRepositoryImpl(
    private val api: SupabaseApiClient,
    private val sessionManager: SessionManager,
    private val database: NexoDatabase
) : ProfileRepository {

    override fun observeProfile(userId: String): Flow<UserProfile?> {
        return database.profileDao().observeProfileById(userId).map { it?.toDomain() }
    }

    override suspend fun getProfile(userId: String): AppResult<UserProfile> {
        val cached = database.profileDao().getProfileById(userId)
        if (cached != null) return AppResult.Success(cached.toDomain())

        val token = sessionManager.getAccessToken()
        val remoteRes = api.getProfile(userId, token)
        return remoteRes.fold(
            onSuccess = { json ->
                if (json != null) {
                    val entity = ProfileEntity(
                        id = userId,
                        username = json.optString("username", "user"),
                        displayName = json.optString("display_name", "User"),
                        avatarUrl = json.optString("avatar_url").ifEmpty { null },
                        bio = json.optString("bio").ifEmpty { null },
                        lastSeen = System.currentTimeMillis()
                    )
                    database.profileDao().upsertProfile(entity)
                    AppResult.Success(entity.toDomain())
                } else {
                    AppResult.Error("Профиль не найден")
                }
            },
            onFailure = {
                AppResult.Error(it.message ?: "Ошибка получения профиля")
            }
        )
    }

    override suspend fun saveProfile(profile: UserProfile): AppResult<Unit> {
        // Save to Room immediately
        database.profileDao().upsertProfile(ProfileEntity.fromDomain(profile))

        val token = sessionManager.getAccessToken()
        val json = JSONObject().apply {
            put("id", profile.id)
            put("username", profile.username)
            put("display_name", profile.displayName)
            put("avatar_url", profile.avatarUrl)
            put("bio", profile.bio)
        }

        return api.upsertProfile(json, token).fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(it.message ?: "Не удалось сохранить профиль", it) }
        )
    }

    override suspend fun uploadAvatar(userId: String, imageUri: Uri): AppResult<String> {
        val context = NexoApplication.instance
        val compressedBytes = ImageCompressor.compressImage(context, imageUri, maxDimension = 600, quality = 85)
            ?: return AppResult.Error("Не удалось обработать изображение")

        val fileName = "avatar_${userId}_${System.currentTimeMillis()}.jpg"

        if (!AppConfig.isConfigured) {
            val localPath = ImageCompressor.saveLocally(context, compressedBytes, fileName)
            updateAvatar(userId, localPath)
            return AppResult.Success(localPath)
        }

        val token = sessionManager.getAccessToken()
        val storagePath = "$userId/$fileName"
        val uploadRes = api.uploadStorageFile(
            bucket = "avatars",
            path = storagePath,
            bytes = compressedBytes,
            contentType = "image/jpeg",
            token = token
        )

        return uploadRes.fold(
            onSuccess = { publicUrl ->
                when (val updateResult = updateAvatar(userId, publicUrl)) {
                    is AppResult.Success -> AppResult.Success(publicUrl)
                    is AppResult.Error -> updateResult
                    AppResult.Loading -> AppResult.Error("Не удалось сохранить аватар")
                }
            },
            onFailure = {
                AppResult.Error(it.message ?: "Не удалось загрузить аватар", it)
            }
        )
    }

    override suspend fun updateAvatar(userId: String, avatarUrl: String): AppResult<Unit> {
        val token = sessionManager.getAccessToken()
        val json = JSONObject().apply {
            put("id", userId)
            put("avatar_url", avatarUrl)
        }
        val remoteResult = api.upsertProfile(json, token)
        if (remoteResult.isFailure) {
            val error = remoteResult.exceptionOrNull()
            return AppResult.Error(error?.message ?: "Не удалось сохранить аватар", error)
        }

        val cached = database.profileDao().getProfileById(userId)
        if (cached != null) {
            val updated = cached.copy(avatarUrl = avatarUrl)
            database.profileDao().upsertProfile(updated)
        }

        return AppResult.Success(Unit)
    }

    override suspend fun updatePresence(userId: String): AppResult<Unit> {
        val now = System.currentTimeMillis()
        val cached = database.profileDao().getProfileById(userId)
        if (cached != null) {
            database.profileDao().upsertProfile(cached.copy(lastSeen = now))
        }

        val token = sessionManager.getAccessToken()
        val json = JSONObject().apply {
            put("id", userId)
            put(
                "last_seen",
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(Date(now))
            )
        }
        return api.upsertProfile(json, token).fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(it.message ?: "Не удалось обновить статус", it) }
        )
    }

    override suspend fun searchUsers(query: String): AppResult<List<UserProfile>> {
        val cleanQuery = query.trim().removePrefix("@")
        if (cleanQuery.isEmpty()) return AppResult.Success(emptyList())

        val currentUserId = sessionManager.getUserId() ?: ""
        val token = sessionManager.getAccessToken()

        // Local search first
        val localMatches = database.profileDao().searchProfiles(cleanQuery)
            .filter { it.id != currentUserId }
            .map { it.toDomain() }

        val remoteRes = api.searchProfiles(cleanQuery, currentUserId, token)
        return remoteRes.fold(
            onSuccess = { arr ->
                val list = mutableListOf<UserProfile>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id != currentUserId) {
                        val p = UserProfile(
                            id = id,
                            username = obj.optString("username"),
                            displayName = obj.optString("display_name"),
                            avatarUrl = obj.optString("avatar_url").ifEmpty { null },
                            bio = obj.optString("bio").ifEmpty { null }
                        )
                        database.profileDao().upsertProfile(ProfileEntity.fromDomain(p))
                        list.add(p)
                    }
                }
                // Merge distinct
                val merged = (localMatches + list).distinctBy { it.id }
                AppResult.Success(merged)
            },
            onFailure = {
                // If offline, return local matches
                AppResult.Success(localMatches)
            }
        )
    }
}
