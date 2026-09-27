package com.example.data.repository

import com.example.core.config.AppConfig
import com.example.core.model.AppResult
import com.example.data.local.NexoDatabase
import com.example.data.local.SessionManager
import com.example.data.local.entities.MessageEntity
import com.example.data.remote.SupabaseApiClient
import com.example.domain.model.Attachment
import com.example.domain.model.Message
import com.example.domain.model.MessageStatus
import com.example.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.util.Log
import org.json.JSONObject
import java.util.UUID

class MessageRepositoryImpl(
    private val api: SupabaseApiClient,
    private val sessionManager: SessionManager,
    private val database: NexoDatabase
) : MessageRepository {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var realtimeJob: Job? = null

    override fun observeMessages(chatId: String): Flow<List<Message>> {
        return database.messageDao().observeMessagesForChat(chatId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun sendMessage(
        chatId: String,
        text: String,
        attachment: Attachment?
    ): AppResult<Message> {
        val myUserId = sessionManager.getUserId() ?: "user_me"
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val message = Message(
            id = messageId,
            chatId = chatId,
            senderId = myUserId,
            text = text,
            status = MessageStatus.SENDING,
            createdAt = now,
            attachment = attachment,
            isOutgoing = true
        )

        // 1. Optimistic write to Room
        database.messageDao().upsertMessage(MessageEntity.fromDomain(message))
        database.chatDao().updateLastMessage(chatId, if (text.isNotBlank()) text else "Медиафайл", now)

        // 2. Background send to Supabase
        scope.launch {
            val token = sessionManager.getAccessToken()
            val msgJson = JSONObject().apply {
                put("id", messageId)
                put("chat_id", chatId)
                put("sender_id", myUserId)
                put("text", text)
                put("client_message_id", messageId)
            }

            if (AppConfig.isConfigured) {
                val sendResult = api.insertMessage(msgJson, token)
                sendResult.fold(
                    onSuccess = {
                        database.messageDao().updateMessageStatus(messageId, MessageStatus.SENT.name)
                    },
                    onFailure = { error ->
                        Log.e("MessageRepository", "Message send failed", error)
                        database.messageDao().updateMessageStatus(messageId, MessageStatus.FAILED.name)
                    }
                )
            } else {
                // Interactive Demo / Offline mode
                delay(400)
                database.messageDao().updateMessageStatus(messageId, MessageStatus.SENT.name)
                delay(300)
                database.messageDao().updateMessageStatus(messageId, MessageStatus.READ.name)

                // Simulate peer response in demo mode so user sees incoming bubble & sound/realtime
                simulatePeerReply(chatId, text)
            }
        }

        return AppResult.Success(message)
    }

    override suspend fun retryMessage(messageId: String): AppResult<Unit> {
        val msg = database.messageDao().getMessageById(messageId) ?: return AppResult.Error("Not found")
        database.messageDao().updateMessageStatus(messageId, MessageStatus.SENDING.name)

        scope.launch {
            if (AppConfig.isConfigured) {
                val token = sessionManager.getAccessToken()
                val msgJson = JSONObject().apply {
                    put("id", msg.id)
                    put("chat_id", msg.chatId)
                    put("sender_id", msg.senderId)
                    put("text", msg.text)
                    put("client_message_id", msg.id)
                }
                api.insertMessage(msgJson, token).fold(
                    onSuccess = { database.messageDao().updateMessageStatus(msg.id, MessageStatus.SENT.name) },
                    onFailure = {
                        Log.e("MessageRepository", "Message retry failed", it)
                        database.messageDao().updateMessageStatus(msg.id, MessageStatus.FAILED.name)
                    }
                )
            } else {
                delay(400)
                database.messageDao().updateMessageStatus(msg.id, MessageStatus.SENT.name)
            }
        }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteMessage(messageId: String): AppResult<Unit> {
        database.messageDao().deleteMessage(messageId)
        return AppResult.Success(Unit)
    }

    override suspend fun syncMessages(chatId: String): AppResult<Unit> {
        if (!AppConfig.isConfigured) return AppResult.Success(Unit)

        val token = sessionManager.getAccessToken()
        val myUserId = sessionManager.getUserId() ?: ""
        val res = api.getMessages(chatId, 50, token)
        res.onSuccess { arr ->
            val list = mutableListOf<MessageEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val senderId = obj.optString("sender_id")
                val entity = MessageEntity(
                    id = obj.optString("id"),
                    chatId = obj.optString("chat_id"),
                    senderId = senderId,
                    text = obj.optString("text"),
                    status = MessageStatus.SENT.name,
                    createdAt = System.currentTimeMillis(),
                    isOutgoing = senderId == myUserId,
                    replyToMessageId = obj.optString("reply_to_message_id").ifEmpty { null },
                    attachmentId = null,
                    attachmentPath = null,
                    attachmentName = null,
                    attachmentSize = null,
                    attachmentMime = null,
                    attachmentType = null,
                    attachmentWidth = null,
                    attachmentHeight = null
                )
                list.add(entity)
            }
            database.messageDao().upsertMessages(list)
        }
        return AppResult.Success(Unit)
    }

    override fun startRealtimeUpdates(chatId: String) {
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            val token = sessionManager.getAccessToken()
            api.startRealtimeSubscription(token)

            api.realtimeEvents.collect { eventJson ->
                val myUserId = sessionManager.getUserId() ?: ""
                val payload = eventJson.optJSONObject("payload")
                val record = eventJson.optJSONObject("record")
                    ?: payload?.optJSONObject("record")
                    ?: payload?.optJSONObject("data")?.optJSONObject("record")
                if (record != null && record.optString("chat_id") == chatId) {
                    val senderId = record.optString("sender_id")
                    val newMsg = MessageEntity(
                        id = record.optString("id", UUID.randomUUID().toString()),
                        chatId = chatId,
                        senderId = senderId,
                        text = record.optString("text"),
                        status = MessageStatus.SENT.name,
                        createdAt = System.currentTimeMillis(),
                        isOutgoing = senderId == myUserId,
                        replyToMessageId = record.optString("reply_to_message_id").ifEmpty { null },
                        attachmentId = null,
                        attachmentPath = null,
                        attachmentName = null,
                        attachmentSize = null,
                        attachmentMime = null,
                        attachmentType = null,
                        attachmentWidth = null,
                        attachmentHeight = null
                    )
                    database.messageDao().upsertMessage(newMsg)
                    database.chatDao().updateLastMessage(chatId, newMsg.text, newMsg.createdAt)
                }
            }
        }
    }

    override fun stopRealtimeUpdates() {
        realtimeJob?.cancel()
        realtimeJob = null
        api.stopRealtimeSubscription()
    }

    private suspend fun simulatePeerReply(chatId: String, userText: String) {
        val chat = database.chatDao().getChatById(chatId) ?: return
        val peerId = chat.otherUserId ?: "peer_nexus"
        delay(1200)

        val replyOptions = listOf(
            "Принято по защищенному каналу NexoBeam ⚡",
            "Связь отличная. Квантовый луч стабилен!",
            "Узел на связи! Готов к передаче данных.",
            "Сигнал подтвержден: «$userText» доставлено.",
            "Проверяю телеметрию... Все системы в норме 🚀"
        )
        val replyText = replyOptions.random()
        val now = System.currentTimeMillis()

        val peerMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = peerId,
            text = replyText,
            status = MessageStatus.SENT.name,
            createdAt = now,
            isOutgoing = false,
            replyToMessageId = null,
            attachmentId = null,
            attachmentPath = null,
            attachmentName = null,
            attachmentSize = null,
            attachmentMime = null,
            attachmentType = null,
            attachmentWidth = null,
            attachmentHeight = null
        )

        database.messageDao().upsertMessage(peerMsg)
        database.chatDao().updateLastMessage(chatId, replyText, now)
    }
}
