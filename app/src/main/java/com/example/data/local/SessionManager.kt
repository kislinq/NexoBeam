package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.NexoDesignConcept
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nexobeam_prefs")

class SessionManager(private val context: Context) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_DESIGN_CONCEPT = stringPreferencesKey("design_concept")
        private val KEY_CUSTOM_SUPABASE_URL = stringPreferencesKey("custom_supabase_url")
    }

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val userIdFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }
    val userEmailFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_EMAIL] }
    val customSupabaseUrlFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CUSTOM_SUPABASE_URL] }

    val designConceptFlow: Flow<NexoDesignConcept> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_DESIGN_CONCEPT]
        try {
            if (name != null) NexoDesignConcept.valueOf(name) else NexoDesignConcept.INDUSTRIAL_MONOCHROME
        } catch (e: Exception) {
            NexoDesignConcept.INDUSTRIAL_MONOCHROME
        }
    }

    suspend fun setCustomSupabaseUrl(url: String?) {
        context.dataStore.edit { prefs ->
            if (url.isNullOrBlank()) {
                prefs.remove(KEY_CUSTOM_SUPABASE_URL)
            } else {
                prefs[KEY_CUSTOM_SUPABASE_URL] = url.trim()
            }
        }
    }

    suspend fun getCustomSupabaseUrl(): String? = context.dataStore.data.map { it[KEY_CUSTOM_SUPABASE_URL] }.first()

    suspend fun saveSession(token: String, refreshToken: String?, userId: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = token
            if (refreshToken != null) prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_USER_ID] = userId
            prefs[KEY_USER_EMAIL] = email
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_EMAIL)
        }
    }

    suspend fun setDesignConcept(concept: NexoDesignConcept) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DESIGN_CONCEPT] = concept.name
        }
    }

    suspend fun getAccessToken(): String? = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }.first()
    suspend fun getRefreshToken(): String? = context.dataStore.data.map { it[KEY_REFRESH_TOKEN] }.first()
    suspend fun getUserId(): String? = context.dataStore.data.map { it[KEY_USER_ID] }.first()
    suspend fun getUserEmail(): String? = context.dataStore.data.map { it[KEY_USER_EMAIL] }.first()
}
