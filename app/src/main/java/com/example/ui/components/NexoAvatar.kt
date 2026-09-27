package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.config.AppConfig
import com.example.ui.theme.LocalNexoExtra
import com.example.ui.theme.NexoDesignConcept

@Composable
fun NexoAvatar(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isOnline: Boolean = false
) {
    val extra = LocalNexoExtra.current
    val resolvedAvatarUrl = AppConfig.resolveSupabaseAssetUrl(avatarUrl)
    var avatarLoadFailed by remember(resolvedAvatarUrl) { mutableStateOf(false) }
    val initials = name.trim().split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { "N" }
        .uppercase()

    val shape = when (extra.concept) {
        NexoDesignConcept.LUMINESCENT_CYBER -> CircleShape
        NexoDesignConcept.SPATIAL_NEO_GLASS -> RoundedCornerShape(18.dp)
        NexoDesignConcept.INDUSTRIAL_MONOCHROME -> CutCornerShape(8.dp)
    }

    val outerRingPadding = if (extra.avatarIsNexusRing) 3.dp else 0.dp

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer Nexus Ring for Luminescent Cyber
        if (extra.avatarIsNexusRing && extra.avatarRingColor != null) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(
                        width = 1.8.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                extra.accentColor,
                                extra.accentSecondary,
                                Color(0xFF00F2FE),
                                extra.accentColor
                            )
                        ),
                        shape = CircleShape
                    )
            )
        } else if (extra.concept == NexoDesignConcept.SPATIAL_NEO_GLASS) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .border(1.dp, Color(0x55FFFFFF), shape)
            )
        }

        // Base Avatar inside ring
        Box(
            modifier = Modifier
                .size(if (extra.avatarIsNexusRing) size - 6.dp else size)
                .clip(shape)
                .background(
                    when (extra.concept) {
                        NexoDesignConcept.LUMINESCENT_CYBER -> Brush.linearGradient(
                            listOf(Color(0xFF0F1A2C), Color(0xFF0A1220))
                        )
                        NexoDesignConcept.SPATIAL_NEO_GLASS -> Brush.linearGradient(
                            listOf(Color(0x4025384B), Color(0x20152535))
                        )
                        NexoDesignConcept.INDUSTRIAL_MONOCHROME -> Brush.linearGradient(
                            listOf(Color(0xFF1E232C), Color(0xFF14171E))
                        )
                    }
                )
                .border(
                    width = if (extra.concept == NexoDesignConcept.INDUSTRIAL_MONOCHROME) 1.5.dp else 1.dp,
                    color = when (extra.concept) {
                        NexoDesignConcept.INDUSTRIAL_MONOCHROME -> Color(0xFF333B49)
                        NexoDesignConcept.LUMINESCENT_CYBER -> Color(0xFF1E2B45)
                        NexoDesignConcept.SPATIAL_NEO_GLASS -> Color(0x35FFFFFF)
                    },
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!resolvedAvatarUrl.isNullOrBlank() && !avatarLoadFailed) {
                AsyncImage(
                    model = resolvedAvatarUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                    onError = { avatarLoadFailed = true }
                )
            } else {
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.36f).sp
                )
            }
        }

        // Online status dot
        if (isOnline) {
            val dotColor = when (extra.concept) {
                NexoDesignConcept.LUMINESCENT_CYBER -> Color(0xFF00E5FF) // Glowing Cyan dot
                NexoDesignConcept.SPATIAL_NEO_GLASS -> Color(0xFF00E676) // Emerald dot
                NexoDesignConcept.INDUSTRIAL_MONOCHROME -> Color(0xFFD4FF00) // Acid Lime dot
            }
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(2.dp, Color(0xFF080B10), CircleShape)
            )
        }
    }
}
