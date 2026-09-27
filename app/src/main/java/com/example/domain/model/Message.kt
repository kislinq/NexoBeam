package com.example.domain.model

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val status: MessageStatus = MessageStatus.SENT,
    val createdAt: Long = System.currentTimeMillis(),
    val attachment: Attachment? = null,
    val isOutgoing: Boolean = false,
    val replyToMessageId: String? = null
)
