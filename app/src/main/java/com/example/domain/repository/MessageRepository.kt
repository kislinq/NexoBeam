package com.example.domain.repository

import com.example.core.model.AppResult
import com.example.domain.model.Attachment
import com.example.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeMessages(chatId: String): Flow<List<Message>>
    suspend fun sendMessage(
        chatId: String,
        text: String,
        attachment: Attachment? = null
    ): AppResult<Message>
    suspend fun retryMessage(messageId: String): AppResult<Unit>
    suspend fun deleteMessage(messageId: String): AppResult<Unit>
    suspend fun syncMessages(chatId: String): AppResult<Unit>
    fun startRealtimeUpdates(chatId: String)
    fun stopRealtimeUpdates()
}
