package com.example.domain.model

enum class AttachmentType {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    OTHER
}

data class Attachment(
    val id: String,
    val messageId: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val type: AttachmentType,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null
)
