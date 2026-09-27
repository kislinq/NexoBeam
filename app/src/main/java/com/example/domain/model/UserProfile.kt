package com.example.domain.model

data class UserProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val lastSeen: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true
)
