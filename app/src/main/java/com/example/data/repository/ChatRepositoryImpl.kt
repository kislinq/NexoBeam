package com.example.data.repository

import com.example.core.config.AppConfig
import com.example.core.model.AppResult
import com.example.data.local.NexoDatabase
import com.example.data.local.SessionManager
import com.example.data.local.entities.ChatEntity
import com.example.data.local.entities.ProfileEntity
import com.example.data.remote.SupabaseApiClient
import com.example.domain.model.Chat
import com.example.domain.model.ChatType
import com.example.domain.model.UserProfile
import com.example.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

class ChatRepositoryImpl(
    private val api: SupabaseApiClient,
    private val sessionManager: SessionManager,
    private val database: NexoDatabase
) : ChatRepository {

    override fun observeChats(): Flow<List<Chat>> {
        return database.chatDao().observeAllChats().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getOrCreateDirectChat(targetUser: UserProfile): AppResult<Chat> {
        // Check if direct chat already exists in local Room
        val existing = database.chatDao().getDirectChatByUserId(targetUser.id)
        if (existing != null) {
            return AppResult.Success(existing.toDomain())
        }

        val myUserId = sessionManager.getUserId() ?: "user_me"
        val token = sessionManager.getAccessToken()

        // Create deterministic direct_key: min(u1, u2) + "_" + max(u1, u2)
        val sorted = listOf(myUserId, targetUser.id).sorted()
        val directKey = "${sorted[0]}_${sorted[1]}"
        val newChatId = UUID.randomUUID().toString()

        val chatEntity = ChatEntity(
            id = newChatId,
            type = "direct",
            title = targetUser.displayName,
            avatarUrl = targetUser.avatarUrl,
            lastMessageText = "Чат создан",
            lastMessageAt = System.currentTimeMillis(),
            unreadCount = 0,
            otherUserId = targetUser.id,
            otherUsername = targetUser.username,
            otherDisplayName = targetUser.displayName,
            otherAvatarUrl = targetUser.avatarUrl
        )

        // Sync with Supabase
        val chatJson = JSONObject().apply {
            put("id", newChatId)
            put("type", "direct")
            put("created_by", myUserId)
            put("direct_key", directKey)
        }
        val createdChat = api.insertChat(chatJson, token).getOrElse {
            return AppResult.Error(it.message ?: "Не удалось создать чат", it)
        }
        val chatId = createdChat.optString("id", newChatId).ifBlank { newChatId }

        // Add both members
        val addSelfResult = api.addChatMember(JSONObject().apply {
            put("chat_id", chatId)
            put("user_id", myUserId)
            put("role", "member")
        }, token)
        if (addSelfResult.isFailure) {
            val error = addSelfResult.exceptionOrNull()
            return AppResult.Error(error?.message ?: "Не удалось добавить вас в чат", error)
        }

        val addPeerResult = api.addChatMember(JSONObject().apply {
            put("chat_id", chatId)
            put("user_id", targetUser.id)
            put("role", "member")
        }, token)
        if (addPeerResult.isFailure) {
            val error = addPeerResult.exceptionOrNull()
            return AppResult.Error(error?.message ?: "Не удалось добавить собеседника в чат", error)
        }

        val persistedChat = chatEntity.copy(id = chatId)
        database.profileDao().upsertProfile(ProfileEntity.fromDomain(targetUser))
        database.chatDao().upsertChat(persistedChat)
        return AppResult.Success(persistedChat.toDomain())
    }

    override suspend fun getChatById(chatId: String): Chat? {
        return database.chatDao().getChatById(chatId)?.toDomain()
    }

    override suspend fun markChatAsRead(chatId: String) {
        database.chatDao().clearUnread(chatId)
    }

    override suspend fun syncChats(): AppResult<Unit> {
        if (!AppConfig.isConfigured) return AppResult.Success(Unit)

        val myUserId = sessionManager.getUserId() ?: return AppResult.Success(Unit)
        val token = sessionManager.getAccessToken()

        val chatsResult = api.getUserChats(myUserId, token)
        chatsResult.onSuccess { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val chatId = obj.optString("id")
                val directKey = obj.optString("direct_key")
                val lastText = obj.optString("last_message_text", "Чат создан")

                // Определяем ID собеседника из direct_key (формат userA_userB)
                val partnerId = if (directKey.contains("_")) {
                    val parts = directKey.split("_")
                    if (parts[0] == myUserId && parts.size > 1) parts[1] else parts[0]
                } else {
                    obj.optString("created_by")
                }

                if (partnerId.isNotBlank() && partnerId != myUserId) {
                    val partnerJson = api.getProfile(partnerId, token).getOrNull()
                    val partnerDisplayName = partnerJson?.optString("display_name")
                        ?.ifBlank { null } ?: partnerJson?.optString("username") ?: "Собеседник"
                    val partnerUsername = partnerJson?.optString("username") ?: ""
                    val partnerAvatarUrl = partnerJson?.optString("avatar_url")?.ifBlank { null }

                    val entity = ChatEntity(
                        id = chatId,
                        type = "direct",
                        title = partnerDisplayName,
                        avatarUrl = partnerAvatarUrl,
                        lastMessageText = lastText,
                        lastMessageAt = System.currentTimeMillis(),
                        unreadCount = 0,
                        otherUserId = partnerId,
                        otherUsername = partnerUsername,
                        otherDisplayName = partnerDisplayName,
                        otherAvatarUrl = partnerAvatarUrl
                    )
                    database.chatDao().upsertChat(entity)

                    // Загружаем сообщения для этого чата
                    val msgsRes = api.getMessages(chatId, 30, token)
                    msgsRes.onSuccess { msgArr ->
                        val msgEntities = mutableListOf<com.example.data.local.entities.MessageEntity>()
                        for (m in 0 until msgArr.length()) {
                            val mObj = msgArr.getJSONObject(m)
                            val senderId = mObj.optString("sender_id")
                            msgEntities.add(
                                com.example.data.local.entities.MessageEntity(
                                    id = mObj.optString("id"),
                                    chatId = chatId,
                                    senderId = senderId,
                                    text = mObj.optString("text"),
                                    status = com.example.domain.model.MessageStatus.SENT.name,
                                    createdAt = System.currentTimeMillis(),
                                    isOutgoing = senderId == myUserId,
                                    replyToMessageId = mObj.optString("reply_to_message_id").ifEmpty { null },
                                    attachmentId = null,
                                    attachmentPath = null,
                                    attachmentName = null,
                                    attachmentSize = null,
                                    attachmentMime = null,
                                    attachmentType = null,
                                    attachmentWidth = null,
                                    attachmentHeight = null
                                )
                            )
                        }
                        if (msgEntities.isNotEmpty()) {
                            database.messageDao().upsertMessages(msgEntities)
                        }
                    }
                }
            }
        }

        return AppResult.Success(Unit)
    }
}
