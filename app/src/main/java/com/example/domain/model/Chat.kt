package com.example.domain.model

enum class ChatType {
    DIRECT,
    GROUP
}

data class Chat(
    val id: String,
    val type: ChatType = ChatType.DIRECT,
    val title: String,
    val avatarUrl: String? = null,
    val lastMessageText: String? = null,
    val lastMessageAt: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val otherParticipant: UserProfile? = null
)
