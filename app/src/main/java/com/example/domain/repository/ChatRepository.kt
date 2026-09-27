package com.example.domain.repository

import com.example.core.model.AppResult
import com.example.domain.model.Chat
import com.example.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeChats(): Flow<List<Chat>>
    suspend fun getOrCreateDirectChat(targetUser: UserProfile): AppResult<Chat>
    suspend fun getChatById(chatId: String): Chat?
    suspend fun markChatAsRead(chatId: String)
    suspend fun syncChats(): AppResult<Unit>
}
