package com.example.ui.screens.chat

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.NexoApplication
import com.example.R
import com.example.domain.model.Attachment
import com.example.domain.model.AttachmentType
import com.example.domain.model.Chat
import com.example.domain.model.Message
import com.example.ui.components.MessageBubble
import com.example.ui.components.NexoAvatar
import com.example.ui.theme.LocalNexoExtra
import com.example.ui.theme.StatusFailed
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    onNavigateBack: () -> Unit
) {
    val extra = LocalNexoExtra.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val msgRepo = NexoApplication.instance.messageRepository
    val chatRepo = NexoApplication.instance.chatRepository

    var chatInfo by remember { mutableStateOf<Chat?>(null) }
    val messages by msgRepo.observeMessages(chatId).collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf("") }
    var isAttachSheetOpen by remember { mutableStateOf(false) }
    val attachSheetState = rememberModalBottomSheetState()

    // Message Action sheet state
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var editMessageText by remember { mutableStateOf("") }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    // Activity Result Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val attachment = Attachment(
                id = UUID.randomUUID().toString(),
                messageId = "",
                filePath = uri.toString(),
                fileName = "beam_photo_${System.currentTimeMillis()}.jpg",
                fileSize = 1024L * 512,
                mimeType = "image/jpeg",
                type = AttachmentType.IMAGE
            )
            scope.launch {
                msgRepo.sendMessage(chatId, inputText.trim(), attachment)
                inputText = ""
            }
        }
    }

    LaunchedEffect(chatId) {
        chatInfo = chatRepo.getChatById(chatId)
        chatRepo.markChatAsRead(chatId)
        msgRepo.syncMessages(chatId)
        msgRepo.startRealtimeUpdates(chatId)
    }

    DisposableEffect(chatId) {
        onDispose {
            msgRepo.stopRealtimeUpdates()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun handleSend() {
        val text = inputText.trim()
        if (text.isEmpty()) return
        val replyId = replyingToMessage?.id
        inputText = ""
        replyingToMessage = null
        scope.launch {
            msgRepo.sendMessage(chatId, text, null)
        }
    }

    fun handleSendVoiceBeam() {
        val audioAttachment = Attachment(
            id = UUID.randomUUID().toString(),
            messageId = "",
            filePath = "beam://audio/recording_${System.currentTimeMillis()}.m4a",
            fileName = "voice_beam.m4a",
            fileSize = 1024L * 128,
            mimeType = "audio/mp4",
            type = AttachmentType.AUDIO
        )
        scope.launch {
            msgRepo.sendMessage(chatId, "", audioAttachment)
        }
    }

    // Attachment Sheet
    if (isAttachSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isAttachSheetOpen = false },
            sheetState = attachSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.attach_file),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isAttachSheetOpen = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = extra.accentColor)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = stringResource(R.string.attach_image), style = MaterialTheme.typography.bodyLarge)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isAttachSheetOpen = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null, tint = extra.accentSecondary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = stringResource(R.string.attach_video), style = MaterialTheme.typography.bodyLarge)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isAttachSheetOpen = false
                            val docAttachment = Attachment(
                                id = UUID.randomUUID().toString(),
                                messageId = "",
                                filePath = "content://doc/report.pdf",
                                fileName = "spec_nexobeam_v1.pdf",
                                fileSize = 1024L * 1024 * 2,
                                mimeType = "application/pdf",
                                type = AttachmentType.DOCUMENT
                            )
                            scope.launch {
                                msgRepo.sendMessage(chatId, "Документ спецификации узла", docAttachment)
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = extra.accentColor)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = stringResource(R.string.attach_doc), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    // Message Context Actions BottomSheet
    if (selectedMessage != null) {
        val msg = selectedMessage!!
        ModalBottomSheet(
            onDismissRequest = { selectedMessage = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Действия с сообщением",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Copy
                if (msg.text.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(AnnotatedString(msg.text))
                                Toast.makeText(context, context.getString(R.string.message_copied), Toast.LENGTH_SHORT).show()
                                selectedMessage = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = extra.accentColor)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Скопировать текст", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // Reply
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            replyingToMessage = msg
                            selectedMessage = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = extra.accentColor)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Ответить", style = MaterialTheme.typography.bodyLarge)
                }

                // Edit (for outgoing text messages)
                if (msg.isOutgoing && msg.text.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingMessage = msg
                                editMessageText = msg.text
                                selectedMessage = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = extra.accentColor)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Редактировать", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // Delete
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val idToDelete = msg.id
                            selectedMessage = null
                            scope.launch { msgRepo.deleteMessage(idToDelete) }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = StatusFailed)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Удалить сообщение", color = StatusFailed, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    // Edit Message Dialog
    if (editingMessage != null) {
        val target = editingMessage!!
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Редактировать сообщение") },
            text = {
                OutlinedTextField(
                    value = editMessageText,
                    onValueChange = { editMessageText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newText = editMessageText.trim()
                        if (newText.isNotBlank()) {
                            editingMessage = null
                            scope.launch {
                                val updated = target.copy(text = newText)
                                NexoApplication.instance.database.messageDao()
                                    .upsertMessage(com.example.data.local.entities.MessageEntity.fromDomain(updated))
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = extra.accentColor)
                ) {
                    Text("Сохранить", color = extra.bubbleOutgoingTextColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Fullscreen Image Preview Dialog
    if (previewImageUrl != null) {
        Dialog(onDismissRequest = { previewImageUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { previewImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = previewImageUrl,
                    contentDescription = "Просмотр фото",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .navigationBarsPadding()
    ) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("chat_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            NexoAvatar(
                name = chatInfo?.title ?: "Chat",
                avatarUrl = chatInfo?.avatarUrl,
                size = 40.dp,
                isOnline = chatInfo?.otherParticipant?.isOnline ?: true
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chatInfo?.title ?: "Чат",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (chatInfo?.otherParticipant?.isOnline != false) "● В сети (Beam active)" else "Оффлайн",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = extra.accentColor
                )
            }
        }

        // Messages History List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Отправьте первое сообщение для запуска луча связи ⚡",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val replyText = if (msg.replyToMessageId != null) {
                            messages.find { it.id == msg.replyToMessageId }?.text ?: "Вложенное сообщение"
                        } else null

                        MessageBubble(
                            message = msg,
                            replyToText = replyText,
                            onRetry = {
                                scope.launch { msgRepo.retryMessage(msg.id) }
                            },
                            onLongClick = {
                                selectedMessage = msg
                            },
                            onImageClick = { url ->
                                previewImageUrl = url
                            }
                        )
                    }
                }
            }
        }

        // Replying Preview Bar
        if (replyingToMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = extra.accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ответ на сообщение",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = extra.accentColor
                    )
                    Text(
                        text = replyingToMessage?.text?.ifBlank { "Медиафайл" } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { replyingToMessage = null }, modifier = Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Отмена", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { isAttachSheetOpen = true },
                modifier = Modifier.testTag("chat_attach_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Прикрепить",
                    tint = extra.accentColor
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.type_message_hint),
                        fontSize = 14.sp
                    )
                },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { handleSend() }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input"),
                shape = RoundedCornerShape(extra.cornerRadius * 1.5f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = extra.accentColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Dynamic Action Button: Send or Voice Beam
            if (inputText.trim().isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(extra.bubbleOutgoingBrush)
                        .clickable { handleSend() }
                        .testTag("chat_send_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить",
                        tint = extra.bubbleOutgoingTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(extra.accentColor.copy(alpha = 0.18f))
                        .clickable { handleSendVoiceBeam() }
                        .testTag("chat_voice_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Голосовое сообщение",
                        tint = extra.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

