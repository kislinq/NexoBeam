package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.Attachment
import com.example.domain.model.AttachmentType
import com.example.domain.model.Message
import com.example.domain.model.MessageStatus

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val status: String,
    val createdAt: Long,
    val isOutgoing: Boolean,
    val replyToMessageId: String?,
    // Embedded attachment data
    val attachmentId: String?,
    val attachmentPath: String?,
    val attachmentName: String?,
    val attachmentSize: Long?,
    val attachmentMime: String?,
    val attachmentType: String?,
    val attachmentWidth: Int?,
    val attachmentHeight: Int?
) {
    fun toDomain(): Message {
        val attachment = if (attachmentId != null && attachmentPath != null) {
            Attachment(
                id = attachmentId,
                messageId = id,
                filePath = attachmentPath,
                fileName = attachmentName ?: "file",
                fileSize = attachmentSize ?: 0L,
                mimeType = attachmentMime ?: "application/octet-stream",
                type = when (attachmentType) {
                    "image" -> AttachmentType.IMAGE
                    "video" -> AttachmentType.VIDEO
                    "audio" -> AttachmentType.AUDIO
                    "document" -> AttachmentType.DOCUMENT
                    else -> AttachmentType.OTHER
                },
                width = attachmentWidth,
                height = attachmentHeight
            )
        } else null

        return Message(
            id = id,
            chatId = chatId,
            senderId = senderId,
            text = text,
            status = when (status) {
                "SENDING" -> MessageStatus.SENDING
                "SENT" -> MessageStatus.SENT
                "DELIVERED" -> MessageStatus.DELIVERED
                "READ" -> MessageStatus.READ
                else -> MessageStatus.FAILED
            },
            createdAt = createdAt,
            attachment = attachment,
            isOutgoing = isOutgoing,
            replyToMessageId = replyToMessageId
        )
    }

    companion object {
        fun fromDomain(m: Message): MessageEntity = MessageEntity(
            id = m.id,
            chatId = m.chatId,
            senderId = m.senderId,
            text = m.text,
            status = m.status.name,
            createdAt = m.createdAt,
            isOutgoing = m.isOutgoing,
            replyToMessageId = m.replyToMessageId,
            attachmentId = m.attachment?.id,
            attachmentPath = m.attachment?.filePath,
            attachmentName = m.attachment?.fileName,
            attachmentSize = m.attachment?.fileSize,
            attachmentMime = m.attachment?.mimeType,
            attachmentType = m.attachment?.type?.name?.lowercase(),
            attachmentWidth = m.attachment?.width,
            attachmentHeight = m.attachment?.height
        )
    }
}
