package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.Chat
import com.example.domain.model.ChatType
import com.example.domain.model.UserProfile

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val avatarUrl: String?,
    val lastMessageText: String?,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val otherUserId: String?,
    val otherUsername: String?,
    val otherDisplayName: String?,
    val otherAvatarUrl: String?
) {
    fun toDomain(): Chat = Chat(
        id = id,
        type = if (type == "group") ChatType.GROUP else ChatType.DIRECT,
        title = title,
        avatarUrl = avatarUrl,
        lastMessageText = lastMessageText,
        lastMessageAt = lastMessageAt,
        unreadCount = unreadCount,
        otherParticipant = if (otherUserId != null) {
            UserProfile(
                id = otherUserId,
                username = otherUsername ?: "",
                displayName = otherDisplayName ?: title,
                avatarUrl = otherAvatarUrl
            )
        } else null
    )

    companion object {
        fun fromDomain(chat: Chat): ChatEntity = ChatEntity(
            id = chat.id,
            type = if (chat.type == ChatType.GROUP) "group" else "direct",
            title = chat.title,
            avatarUrl = chat.avatarUrl,
            lastMessageText = chat.lastMessageText,
            lastMessageAt = chat.lastMessageAt,
            unreadCount = chat.unreadCount,
            otherUserId = chat.otherParticipant?.id,
            otherUsername = chat.otherParticipant?.username,
            otherDisplayName = chat.otherParticipant?.displayName,
            otherAvatarUrl = chat.otherParticipant?.avatarUrl
        )
    }
}
