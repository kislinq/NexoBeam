package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.AttachmentType
import com.example.domain.model.Message
import com.example.domain.model.MessageStatus
import com.example.ui.theme.LocalNexoExtra
import com.example.ui.theme.NexoDesignConcept
import com.example.ui.theme.StatusFailed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    replyToText: String? = null,
    onRetry: () -> Unit,
    onLongClick: () -> Unit,
    onImageClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val extra = LocalNexoExtra.current
    val isOut = message.isOutgoing

    val shape = if (isOut) extra.bubbleOutgoingShape else extra.bubbleIncomingShape

    val bubbleModifier = if (isOut) {
        if (extra.concept == NexoDesignConcept.SPATIAL_NEO_GLASS) {
            Modifier
                .clip(shape)
                .background(extra.bubbleOutgoingBrush)
                .border(1.dp, Color(0x6055FFAA), shape)
        } else {
            Modifier
                .clip(shape)
                .background(extra.bubbleOutgoingBrush)
        }
    } else {
        Modifier
            .clip(shape)
            .background(extra.bubbleIncomingColor)
            .border(1.dp, extra.bubbleIncomingBorder, shape)
    }

    val pattern = if (extra.showSecondsInTime) "HH:mm:ss" else "HH:mm"
    val timeFormatted = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(message.createdAt))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .then(bubbleModifier)
                .testTag("message_bubble_${message.id}")
                .combinedClickable(
                    onClick = {
                        if (message.status == MessageStatus.FAILED) onRetry()
                    },
                    onLongClick = onLongClick
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Reply quote if replied to another message
            if (!replyToText.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .border(width = 1.5.dp, color = extra.accentColor, shape = RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = replyToText,
                        style = MaterialTheme.typography.bodySmall,
                        color = (if (isOut) extra.bubbleOutgoingTextColor else extra.bubbleIncomingTextColor).copy(alpha = 0.85f),
                        maxLines = 2
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Attachment Preview with Overlay controls (matches ChatGPT mockup)
            if (message.attachment != null) {
                val att = message.attachment
                if (att.type == AttachmentType.IMAGE || att.type == AttachmentType.VIDEO) {
                    val mediaShape = when (extra.concept) {
                        NexoDesignConcept.LUMINESCENT_CYBER -> RoundedCornerShape(14.dp)
                        NexoDesignConcept.SPATIAL_NEO_GLASS -> RoundedCornerShape(20.dp)
                        NexoDesignConcept.INDUSTRIAL_MONOCHROME -> CutCornerShape(8.dp)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(mediaShape)
                            .clickable { onImageClick(att.filePath) }
                    ) {
                        AsyncImage(
                            model = att.filePath,
                            contentDescription = att.fileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )

                        // Dark gradient overlay for contrast
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                    )
                                )
                        )

                        // Bottom Overlay: Play pill, time, file size
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Play pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(
                                        if (extra.concept == NexoDesignConcept.INDUSTRIAL_MONOCHROME) CutCornerShape(4.dp)
                                        else CircleShape
                                    )
                                    .background(
                                        when (extra.concept) {
                                            NexoDesignConcept.LUMINESCENT_CYBER -> Color.Black.copy(alpha = 0.6f)
                                            NexoDesignConcept.SPATIAL_NEO_GLASS -> Color(0x60FFFFFF)
                                            NexoDesignConcept.INDUSTRIAL_MONOCHROME -> Color(0xFF151820)
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = when (extra.concept) {
                                            NexoDesignConcept.LUMINESCENT_CYBER -> extra.accentColor
                                            NexoDesignConcept.SPATIAL_NEO_GLASS -> Color(0x80FFFFFF)
                                            NexoDesignConcept.INDUSTRIAL_MONOCHROME -> extra.accentColor
                                        },
                                        shape = if (extra.concept == NexoDesignConcept.INDUSTRIAL_MONOCHROME) CutCornerShape(4.dp) else CircleShape
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Воспроизвести",
                                    tint = if (extra.concept == NexoDesignConcept.SPATIAL_NEO_GLASS) Color.White else extra.accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "0:24",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = if (extra.isMonospaceMetadata) FontFamily.Monospace else FontFamily.Default
                                )
                            }

                            // Telemetry / file size for Industrial
                            if (extra.concept == NexoDesignConcept.INDUSTRIAL_MONOCHROME) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "12.8 MB",
                                        color = extra.accentColor,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = timeFormatted,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                Text(
                                    text = timeFormatted,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else if (att.type == AttachmentType.AUDIO) {
                    // Voice message audio waveform bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(extra.accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Воспроизвести",
                                tint = extra.bubbleOutgoingTextColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Голосовая передача",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isOut) extra.bubbleOutgoingTextColor else extra.bubbleIncomingTextColor
                            )
                            Text(
                                text = "0:08 • Квантовый аудио-луч",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontFamily = if (extra.isMonospaceMetadata) FontFamily.Monospace else FontFamily.Default,
                                color = (if (isOut) extra.bubbleOutgoingTextColor else extra.bubbleIncomingTextColor).copy(alpha = 0.7f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📎 " + att.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = if (extra.isMonospaceMetadata) FontFamily.Monospace else FontFamily.Default,
                            color = if (isOut) extra.bubbleOutgoingTextColor else extra.bubbleIncomingTextColor
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Message text
            if (message.text.isNotBlank()) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isOut) extra.bubbleOutgoingTextColor else extra.bubbleIncomingTextColor,
                    fontWeight = if (isOut && extra.concept == NexoDesignConcept.INDUSTRIAL_MONOCHROME) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Show footer timestamp for text messages (media shows timestamp inside overlay)
            if (message.attachment == null || (message.attachment.type != AttachmentType.IMAGE && message.attachment.type != AttachmentType.VIDEO)) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontFamily = if (extra.isMonospaceMetadata) FontFamily.Monospace else FontFamily.Default,
                        color = (if (isOut) extra.bubbleOutgoingTextColor else extra.bubbleIncomingTextColor)
                            .copy(alpha = if (isOut && extra.concept == NexoDesignConcept.INDUSTRIAL_MONOCHROME) 0.85f else 0.65f)
                    )

                    if (isOut) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            MessageStatus.SENDING -> {
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "sending_alpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .alpha(alpha)
                                        .background(extra.bubbleOutgoingTextColor, RoundedCornerShape(4.dp))
                                )
                            }
                            MessageStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Отправлено",
                                    tint = extra.bubbleOutgoingTextColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageStatus.DELIVERED, MessageStatus.READ -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Прочитано",
                                    tint = when (extra.concept) {
                                        NexoDesignConcept.LUMINESCENT_CYBER -> Color(0xFF00E5FF)
                                        NexoDesignConcept.SPATIAL_NEO_GLASS -> Color(0xFF55FFAA)
                                        NexoDesignConcept.INDUSTRIAL_MONOCHROME -> Color(0xFF000000)
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            MessageStatus.FAILED -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { onRetry() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Ошибка",
                                        tint = StatusFailed,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Повторить",
                                        tint = StatusFailed,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Share arrow pill next to media card as in the ChatGPT mockup!
        if (message.attachment != null && (message.attachment.type == AttachmentType.IMAGE || message.attachment.type == AttachmentType.VIDEO)) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(
                        if (extra.concept == NexoDesignConcept.INDUSTRIAL_MONOCHROME) CutCornerShape(6.dp)
                        else CircleShape
                    )
                    .background(
                        when (extra.concept) {
                            NexoDesignConcept.LUMINESCENT_CYBER -> Color(0xFF141C2E)
                            NexoDesignConcept.SPATIAL_NEO_GLASS -> Color(0x3528394A)
                            NexoDesignConcept.INDUSTRIAL_MONOCHROME -> Color(0xFF1C2028)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when (extra.concept) {
                            NexoDesignConcept.LUMINESCENT_CYBER -> extra.accentColor.copy(alpha = 0.5f)
                            NexoDesignConcept.SPATIAL_NEO_GLASS -> Color(0x40FFFFFF)
                            NexoDesignConcept.INDUSTRIAL_MONOCHROME -> Color(0xFF2E3544)
                        },
                        shape = if (extra.concept == NexoDesignConcept.INDUSTRIAL_MONOCHROME) CutCornerShape(6.dp) else CircleShape
                    )
                    .clickable { onLongClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Поделиться",
                    tint = when (extra.concept) {
                        NexoDesignConcept.LUMINESCENT_CYBER -> extra.accentColor
                        NexoDesignConcept.SPATIAL_NEO_GLASS -> Color.White
                        NexoDesignConcept.INDUSTRIAL_MONOCHROME -> extra.accentColor
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

