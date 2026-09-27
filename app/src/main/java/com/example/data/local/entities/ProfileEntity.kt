package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.UserProfile

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val lastSeen: Long
) {
    fun toDomain(): UserProfile = UserProfile(
        id = id,
        username = username,
        displayName = displayName,
        avatarUrl = avatarUrl,
        bio = bio,
        lastSeen = lastSeen
    )

    companion object {
        fun fromDomain(p: UserProfile): ProfileEntity = ProfileEntity(
            id = p.id,
            username = p.username,
            displayName = p.displayName,
            avatarUrl = p.avatarUrl,
            bio = p.bio,
            lastSeen = p.lastSeen
        )
    }
}
