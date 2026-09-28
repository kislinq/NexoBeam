package com.example.core.config

import com.example.BuildConfig
import java.net.URI

object AppConfig {
    @Volatile
    var customSupabaseUrl: String? = null

    val directSupabaseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim().removeSuffix("/")

    val proxySupabaseUrl: String
        get() = BuildConfig.SUPABASE_PROXY_URL.trim().removeSuffix("/")

    val supabaseUrl: String
        get() {
            val custom = customSupabaseUrl?.trim()?.removeSuffix("/")
            if (!custom.isNullOrBlank()) return custom

            if (proxySupabaseUrl.isNotBlank()) return proxySupabaseUrl

            return directSupabaseUrl
        }

    val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isProxyConfigured: Boolean
        get() = customSupabaseUrl?.trim()?.removeSuffix("/")?.isNotBlank() == true ||
                proxySupabaseUrl.isNotBlank()

    val isConfigured: Boolean
        get() = supabaseUrl.isNotBlank() &&
                !supabaseUrl.contains("your-project") &&
                supabaseAnonKey.isNotBlank() &&
                !supabaseAnonKey.contains("your-anon-key")

    fun resolveSupabaseAssetUrl(url: String?): String? {
        if (url.isNullOrBlank()) return url

        val customBase = customSupabaseUrl
            ?.trim()
            ?.removeSuffix("/")
            ?.takeIf { it.isNotBlank() }
            ?: return url

        val assetUri = try {
            URI(url)
        } catch (_: Exception) {
            return url
        }

        if (assetUri.scheme !in setOf("http", "https") ||
            assetUri.host?.endsWith(".supabase.co", ignoreCase = true) != true
        ) {
            return url
        }

        return customBase + assetUri.rawPath.orEmpty() +
                (assetUri.rawQuery?.let { "?$it" } ?: "")
    }
}
