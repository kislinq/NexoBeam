package com.example.data.repository

import com.example.core.config.AppConfig
import com.example.core.model.AppResult
import com.example.data.local.NexoDatabase
import com.example.data.local.SessionManager
import com.example.data.local.entities.ChatEntity
import com.example.data.local.entities.ProfileEntity
import com.example.data.remote.SupabaseApiClient
import com.example.domain.model.Chat
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

    override suspend fun getOrCreateDirectChat(
        targetUser: UserProfile
    ): AppResult<Chat> {
        val existing =
            database.chatDao().getDirectChatByUserId(targetUser.id)

        if (existing != null) {
            return AppResult.Success(existing.toDomain())
        }

        val myUserId =
            sessionManager.getUserId() ?: "user_me"

        val token =
            sessionManager.getAccessToken()

        val sorted =
            listOf(myUserId, targetUser.id).sorted()

        val directKey =
            "${sorted[0]}_${sorted[1]}"

        val newChatId =
            UUID.randomUUID().toString()

        val chatJson =
            JSONObject().apply {
                put("id", newChatId)
                put("type", "direct")
                put("created_by", myUserId)
                put("direct_key", directKey)
            }

        val insertChatResult =
            api.insertChat(
                chatJson,
                token
            )

        val createdChat = insertChatResult.getOrElse { insertError ->
            val isUniqueConflict =
                insertError.message?.contains("23505") == true ||
                        insertError.message?.contains("chats_direct_key_key") == true

            if (!isUniqueConflict) {
                return AppResult.Error(
                    insertError.message ?: "Не удалось создать чат",
                    insertError
                )
            }

            val existingChatResult =
                api.getDirectChatByKey(directKey, token)

            if (existingChatResult.isFailure) {
                val lookupError = existingChatResult.exceptionOrNull()
                return AppResult.Error(
                    "Чат с этим пользователем уже есть, но не удалось получить существующую запись",
                    lookupError
                )
            }

            existingChatResult.getOrNull() ?: return AppResult.Error(
                insertError.message ?: "Не удалось найти существующий чат",
                insertError
            )
        }

        val chatId =
            createdChat
                .optString("id", newChatId)
                .ifBlank { newChatId }

        val addSelfResult =
            api.addChatMember(
                JSONObject().apply {
                    put("chat_id", chatId)
                    put("user_id", myUserId)
                    put("role", "member")
                },
                token
            )

        if (addSelfResult.isFailure) {
            val error =
                addSelfResult.exceptionOrNull()

            return AppResult.Error(
                error?.message
                    ?: "Не удалось добавить вас в чат",
                error
            )
        }

        val addPeerResult =
            api.addChatMember(
                JSONObject().apply {
                    put("chat_id", chatId)
                    put("user_id", targetUser.id)
                    put("role", "member")
                },
                token
            )

        if (addPeerResult.isFailure) {
            val error =
                addPeerResult.exceptionOrNull()

            return AppResult.Error(
                error?.message
                    ?: "Не удалось добавить собеседника в чат",
                error
            )
        }

        val persistedChat =
            ChatEntity(
                id = chatId,
                type = "direct",
                title = targetUser.displayName,
                avatarUrl = AppConfig.resolveSupabaseAssetUrl(
                    targetUser.avatarUrl
                ),
                lastMessageText = "Чат создан",
                lastMessageAt = System.currentTimeMillis(),
                unreadCount = 0,
                otherUserId = targetUser.id,
                otherUsername = targetUser.username,
                otherDisplayName = targetUser.displayName,
                otherAvatarUrl = AppConfig.resolveSupabaseAssetUrl(
                    targetUser.avatarUrl
                )
            )

        database.profileDao()
            .upsertProfile(
                ProfileEntity.fromDomain(targetUser)
            )

        database.chatDao()
            .upsertChat(persistedChat)

        return AppResult.Success(
            persistedChat.toDomain()
        )
    }

    override suspend fun getChatById(
        chatId: String
    ): Chat? {
        return database.chatDao()
            .getChatById(chatId)
            ?.toDomain()
    }

    override suspend fun markChatAsRead(
        chatId: String
    ) {
        database.chatDao()
            .clearUnread(chatId)
    }

    override suspend fun syncChats(): AppResult<Unit> {
        if (!AppConfig.isConfigured) {
            return AppResult.Success(Unit)
        }

        val myUserId =
            sessionManager.getUserId()
                ?: return AppResult.Success(Unit)

        val token =
            sessionManager.getAccessToken()

        val chatsResult =
            api.getUserChats(
                myUserId,
                token
            )

        if (chatsResult.isFailure) {
            return AppResult.Error(
                chatsResult.exceptionOrNull()?.message
                    ?: "Не удалось синхронизировать чаты",
                chatsResult.exceptionOrNull()
            )
        }

        val arr =
            chatsResult.getOrNull()
                ?: return AppResult.Success(Unit)

        for (i in 0 until arr.length()) {
            val obj =
                arr.optJSONObject(i)
                    ?: continue

            val chatId =
                obj.optString("id")
                    .ifBlank { continue }

            val directKey =
                obj.optString("direct_key")

            val lastText =
                obj.optString(
                    "last_message_text",
                    "Чат создан"
                )

            val partnerId =
                if (directKey.contains("_")) {
                    val parts =
                        directKey.split("_")

                    when {
                        parts.size > 1 &&
                                parts[0] == myUserId ->
                            parts[1]

                        parts.isNotEmpty() &&
                                parts[0] != myUserId ->
                            parts[0]

                        else ->
                            ""
                    }
                } else {
                    obj.optString("created_by")
                        .takeIf { it != myUserId }
                        .orEmpty()
                }

            if (
                partnerId.isBlank() ||
                partnerId == myUserId
            ) {
                continue
            }

            val partnerJson =
                api.getProfile(
                    partnerId,
                    token
                ).getOrNull()

            val partnerDisplayName =
                partnerJson
                    ?.optString("display_name")
                    ?.ifBlank { null }
                    ?: partnerJson
                        ?.optString("username")
                        ?.ifBlank { null }
                    ?: "Собеседник"

            val partnerUsername =
                partnerJson
                    ?.optString("username")
                    ?: ""

            val partnerAvatarUrl =
                partnerJson
                    ?.optString("avatar_url")
                    ?.ifBlank { null }
                    ?.let(AppConfig::resolveSupabaseAssetUrl)

            val entity =
                ChatEntity(
                    id = chatId,
                    type = "direct",
                    title = partnerDisplayName,
                    avatarUrl = partnerAvatarUrl,
                    lastMessageText = lastText,
                    lastMessageAt =
                        System.currentTimeMillis(),
                    unreadCount = 0,
                    otherUserId = partnerId,
                    otherUsername = partnerUsername,
                    otherDisplayName = partnerDisplayName,
                    otherAvatarUrl = partnerAvatarUrl
                )

            database.chatDao()
                .upsertChat(entity)

            val msgsRes =
                api.getMessages(
                    chatId,
                    30,
                    token
                )

            msgsRes.onSuccess { msgArr ->
                val msgEntities =
                    mutableListOf<
                        com.example.data.local.entities.MessageEntity
                    >()

                for (m in 0 until msgArr.length()) {
                    val mObj =
                        msgArr.optJSONObject(m)
                            ?: continue

                    val senderId =
                        mObj.optString("sender_id")

                    val text =
                        mObj.optString(
                            "content",
                            mObj.optString("text")
                        )

                    val createdAt =
                        mObj.optString("created_at")

                    msgEntities.add(
                        com.example.data.local.entities.MessageEntity(
                            id = mObj.optString("id"),
                            chatId = chatId,
                            senderId = senderId,
                            text = text,
                            status =
                                com.example.domain.model.MessageStatus.SENT.name,
                            createdAt =
                                parseTimestamp(createdAt),
                            isOutgoing =
                                senderId == myUserId,
                            replyToMessageId =
                                mObj.optString(
                                    "reply_to_id",
                                    mObj.optString(
                                        "reply_to_message_id"
                                    )
                                ).ifEmpty { null },
                            attachmentId = null,
                            attachmentPath =
                                mObj.optString(
                                    "attachment_url"
                                ).ifEmpty { null },
                            attachmentName =
                                mObj.optString(
                                    "attachment_name"
                                ).ifEmpty { null },
                            attachmentSize = null,
                            attachmentMime =
                                mObj.optString(
                                    "attachment_mime_type",
                                    mObj.optString(
                                        "attachment_mime"
                                    )
                                ).ifEmpty { null },
                            attachmentType =
                                mObj.optString(
                                    "message_type"
                                ).ifEmpty { null },
                            attachmentWidth = null,
                            attachmentHeight = null
                        )
                    )
                }

                if (msgEntities.isNotEmpty()) {
                    database.messageDao()
                        .upsertMessages(msgEntities)
                }
            }
        }

        return AppResult.Success(Unit)
    }

    private fun parseTimestamp(
        value: String
    ): Long {
        if (value.isBlank()) {
            return System.currentTimeMillis()
        }

        return runCatching {
            java.time.Instant
                .parse(value)
                .toEpochMilli()
        }.getOrElse {
            System.currentTimeMillis()
        }
    }
}
