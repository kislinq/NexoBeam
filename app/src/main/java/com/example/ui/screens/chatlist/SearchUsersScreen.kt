package com.example.ui.screens.chatlist

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.NexoApplication
import com.example.R
import com.example.core.config.AppConfig
import com.example.core.model.AppResult
import com.example.domain.model.UserProfile
import com.example.ui.components.NexoAvatar
import com.example.ui.theme.LocalNexoExtra
import kotlinx.coroutines.launch

@Composable
fun SearchUsersScreen(
    onNavigateBack: () -> Unit,
    onChatOpened: (String) -> Unit
) {
    val extra = LocalNexoExtra.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Preloaded suggestions
    val suggestions = remember {
        listOf(
            UserProfile("node_01", "eva_quantum", "Eva Quantum", null, "Исследователь квантовых сетей", isOnline = true),
            UserProfile("node_02", "marcus_cyber", "Marcus Void", null, "Инженер космической связи", isOnline = true),
            UserProfile("node_03", "aurora_beam", "Aurora Beam", null, "Архитектор протокола Nexo", isOnline = true)
        )
    }

    LaunchedEffect(query) {
        if (query.trim().isEmpty()) {
            results = if (AppConfig.isConfigured) emptyList() else suggestions
            return@LaunchedEffect
        }
        isLoading = true
        val res = NexoApplication.instance.profileRepository.searchUsers(query.trim())
        isLoading = false
        if (res is AppResult.Success) {
            results = if (res.data.isEmpty() && !AppConfig.isConfigured) {
                suggestions.filter {
                    it.username.contains(query, ignoreCase = true) ||
                    it.displayName.contains(query, ignoreCase = true)
                }
            } else res.data
        }
    }

    fun startChatWithUser(user: UserProfile) {
        scope.launch {
            val res = NexoApplication.instance.chatRepository.getOrCreateDirectChat(user)
            if (res is AppResult.Success) {
                onChatOpened(res.data.id)
            } else if (res is AppResult.Error) {
                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("search_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_users_hint)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = extra.accentColor)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .testTag("search_query_input"),
                shape = RoundedCornerShape(extra.cornerRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = extra.accentColor
                )
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = extra.accentColor)
            }
        }

        Text(
            text = if (query.isEmpty()) "РЕКОМЕНДУЕМЫЕ УЗЛЫ СВЯЗИ" else "РЕЗУЛЬТАТЫ ПОИСКА",
            style = MaterialTheme.typography.labelSmall,
            color = extra.accentColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )

        if (results.isEmpty() && !isLoading) {
            Text(
                text = if (query.isBlank()) "Введите имя пользователя для поиска" else "Пользователи не найдены",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results, key = { it.id }) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { startChatWithUser(user) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexoAvatar(
                        name = user.displayName,
                        avatarUrl = user.avatarUrl,
                        size = 46.dp,
                        isOnline = user.isOnline
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "@" + user.username,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { startChatWithUser(user) },
                        shape = RoundedCornerShape(extra.cornerRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extra.accentColor,
                            contentColor = extra.bubbleOutgoingTextColor
                        ),
                        modifier = Modifier.testTag("start_chat_button_${user.id}")
                    ) {
                        Text(
                            text = "Связаться",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
