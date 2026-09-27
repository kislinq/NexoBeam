package com.example.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.NexoApplication
import com.example.R
import com.example.ui.components.NexoAvatar
import com.example.ui.theme.LocalNexoExtra

@Composable
fun SplashScreen(
    onNavigateToChatList: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToProfileSetup: () -> Unit
) {
    val extra = LocalNexoExtra.current
    val scale = remember { Animatable(0.7f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1.05f, animationSpec = tween(600))
        scale.animateTo(1.0f, animationSpec = tween(200))

        val authState = NexoApplication.instance.authRepository.restoreSession()
        when (authState) {
            is com.example.domain.model.AuthState.Authenticated -> onNavigateToChatList()
            is com.example.domain.model.AuthState.RequiresProfileSetup -> onNavigateToProfileSetup()
            else -> onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale.value)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(extra.cornerRadius * 1.5f))
                    .background(extra.bubbleOutgoingBrush),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NB",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = extra.bubbleOutgoingTextColor
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = extra.accentColor
            )
        }
    }
}
