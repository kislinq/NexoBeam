package com.example.data.repository

import com.example.core.config.AppConfig
import com.example.data.remote.SupabaseApiClient
import com.example.domain.model.Message
import com.example.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class MessageRepositoryImpl(
    private val api: SupabaseApiClient
) : MessageRepository {

    override suspend fun getMessages(
        chatId: String,
        limit: Int,
        token: String?
    ): Result<List<Message>> {
        return api.getMessages(
            chatId = chatId,
            limit = limit,
            token = token
        ).map { json ->
            parseMessages(json)
        }
    }

    override suspend fun sendMessage(
        message: Message,
        token: String?
    ): Result<Message> {
        val payload = JSONObject().apply {
            put("chat_id", message.chatId)
            put("sender_id", message.senderId)
            put("content", message.content)
            put("message_type", message.messageType)
            put("reply_to_id", message.replyToId)
            put("created_at", message.createdAt)
        }

        return api.insertMessage(
            msgJson = payload,
            token = token
        ).map { json ->
            parseMessage(json)
        }
    }

    override fun observeRealtimeMessages(): Flow<Message> {
        return api.realtimeEvents.mapNotNullMessage()
    }

    override fun startRealtimeSubscription(
        chatId: String,
        token: String?
    ) {
        api.startRealtimeSubscription(
            chatId = chatId,
            token = token
        )
    }

    override fun stopRealtimeSubscription() {
        api.stopRealtimeSubscription()
    }

    private fun Flow<JSONObject>.mapNotNullMessage(): Flow<Message> {
        return mapNotNull { event ->
            parseRealtimeMessage(event)
        }
    }

    private fun parseMessages(
        array: JSONArray
    ): List<Message> {
        val result = mutableListOf<Message>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            runCatching {
                result.add(parseMessage(item))
            }
        }

        return result
    }

    private fun parseMessage(
        json: JSONObject
    ): Message {
        return Message(
            id = json.optString("id"),
            chatId = json.optString("chat_id"),
            senderId = json.optString("sender_id"),
            content = json.optString("content"),
            messageType = json.optString(
                "message_type",
                "text"
            ),
            createdAt = json.optString("created_at"),
            replyToId = json.optString(
                "reply_to_id",
                null
            ),
            attachmentUrl = resolveAssetUrl(
                json.optString(
                    "attachment_url",
                    null
                )
            ),
            attachmentName = json.optString(
                "attachment_name",
                null
            ),
            attachmentMimeType = json.optString(
                "attachment_mime_type",
                null
            )
        )
    }

    private fun parseRealtimeMessage(
        event: JSONObject
    ): Message? {
        val payload = event.optJSONObject("payload")
            ?: return null

        val record = when {
            payload.has("record") ->
                payload.optJSONObject("record")

            payload.has("new") ->
                payload.optJSONObject("new")

            payload.has("data") ->
                payload.optJSONObject("data")

            else ->
                payload
        } ?: return null

        if (!record.has("chat_id")) {
            return null
        }

        return runCatching {
            parseMessage(record)
        }.getOrNull()
    }

    private fun resolveAssetUrl(
        url: String?
    ): String? {
        return AppConfig.resolveSupabaseAssetUrl(url)
    }
}
