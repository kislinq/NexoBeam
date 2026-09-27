package com.example

import com.example.domain.model.Chat
import com.example.domain.model.ChatType
import com.example.domain.model.Message
import com.example.domain.model.MessageStatus
import com.example.ui.theme.NexoDesignConcept
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testDefaultThemeConceptIsIndustrial() {
    val defaultConcept = NexoDesignConcept.INDUSTRIAL_MONOCHROME
    assertEquals("Industrial Tech-Monochrome", defaultConcept.title)
  }

  @Test
  fun testMessageCreationAndStatus() {
    val msg = Message(
      id = "test_msg_01",
      chatId = "chat_01",
      senderId = "sender_01",
      text = "Beam transmission test",
      status = MessageStatus.SENT,
      createdAt = System.currentTimeMillis(),
      isOutgoing = true
    )
    assertEquals(MessageStatus.SENT, msg.status)
    assertEquals(true, msg.isOutgoing)
  }

  @Test
  fun testChatCreation() {
    val chat = Chat(
      id = "chat_01",
      type = ChatType.DIRECT,
      title = "Echo Terminal"
    )
    assertNotNull(chat)
    assertEquals(ChatType.DIRECT, chat.type)
  }

  @Test
  fun testUsernameValidationRegex() {
    val usernameRegex = Regex("^[a-zA-Z0-9_]{3,30}$")
    assertEquals(true, usernameRegex.matches("cyber_pilot"))
    assertEquals(true, usernameRegex.matches("alex123"))
    assertEquals(false, usernameRegex.matches("al")) // too short
    assertEquals(false, usernameRegex.matches("cyber-pilot!")) // invalid characters
  }

  @Test
  fun testUserProfileModel() {
    val profile = com.example.domain.model.UserProfile(
      id = "u_001",
      username = "nova_rider",
      displayName = "Nova Rider",
      avatarUrl = "https://example.com/avatar.jpg",
      bio = "Beam network pioneer",
      isOnline = true
    )
    assertEquals("nova_rider", profile.username)
    assertEquals(true, profile.isOnline)
  }

  @Test
  fun testVoiceMessageAttachment() {
    val attachment = com.example.domain.model.Attachment(
      id = "att_01",
      messageId = "msg_01",
      filePath = "beam://audio/sample.m4a",
      fileName = "voice_beam.m4a",
      fileSize = 1024L * 128,
      mimeType = "audio/mp4",
      type = com.example.domain.model.AttachmentType.AUDIO
    )
    val msg = Message(
      id = "msg_01",
      chatId = "c_01",
      senderId = "s_01",
      text = "",
      attachment = attachment,
      isOutgoing = true
    )
    assertNotNull(msg.attachment)
    assertEquals(com.example.domain.model.AttachmentType.AUDIO, msg.attachment?.type)
  }

  @Test
  fun testMessageReplyReference() {
    val replyMsg = Message(
      id = "msg_02",
      chatId = "c_01",
      senderId = "s_02",
      text = "Принято, отвечаю на предыдущий луч",
      replyToMessageId = "msg_01"
    )
    assertEquals("msg_01", replyMsg.replyToMessageId)
  }
}

