package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.NexoApplication
import com.example.core.config.AppConfig
import com.example.ui.theme.LocalNexoExtra
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusFailed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

@Composable
fun ServerSettingsDialog(
    onDismiss: () -> Unit
) {
    val extra = LocalNexoExtra.current
    val scope = rememberCoroutineScope()
    val sessionManager = NexoApplication.instance.sessionManager

    var customUrlInput by remember { mutableStateOf("") }
    var pingStatus by remember { mutableStateOf<String?>(null) }
    var isPinging by remember { mutableStateOf(false) }
    var isPingSuccess by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val saved = sessionManager.getCustomSupabaseUrl() ?: ""
        customUrlInput = saved
    }

    fun isValidHttpsBaseUrl(value: String): Boolean = try {
        val uri = URI(value.trim())
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
    } catch (_: Exception) {
        false
    }

    fun testConnection(targetUrl: String) {
        val checkUrl = targetUrl.ifBlank { AppConfig.supabaseUrl }.removeSuffix("/")
        if (!isValidHttpsBaseUrl(checkUrl)) {
            isPinging = false
            isPingSuccess = false
            pingStatus = "Укажите корректный HTTPS-адрес сервера"
            return
        }

        isPinging = true
        pingStatus = "Проверка HTTPS-соединения..."
        isPingSuccess = null

        scope.launch(Dispatchers.IO) {
            val testClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            try {
                val req = Request.Builder()
                    .url("$checkUrl/auth/v1/health")
                    .get()
                    .build()

                val response = testClient.newCall(req).execute()
                val code = response.use { it.code }
                withContext(Dispatchers.Main) {
                    isPinging = false
                    isPingSuccess = code in 200..299
                    pingStatus = if (code in 200..299) {
                        "✅ Сервер доступен (HTTP $code)"
                    } else {
                        "❌ Сервер ответил с ошибкой (HTTP $code)"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isPinging = false
                    isPingSuccess = false
                    pingStatus = if (checkUrl.contains("supabase.co")) {
                        "❌ Не удалось подключиться к Supabase. Проверьте сеть или настройте доступный HTTPS-прокси."
                    } else {
                        "❌ Сбой подключения: ${e.localizedMessage ?: e.message}"
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = extra.accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Сервер и доступность (РФ)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Если сеть блокирует HTTPS-доступ к Supabase, укажите адрес доступного прокси. Для сообщений через Realtime он должен поддерживать WebSocket.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Текущий адрес: ${AppConfig.supabaseUrl}",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.accentColor,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = customUrlInput,
                    onValueChange = { customUrlInput = it },
                    label = { Text("Кастомный прокси / Mirror URL") },
                    placeholder = { Text("https://my-proxy.ru") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_proxy_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Ping check button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { testConnection(customUrlInput.trim()) },
                        enabled = !isPinging,
                        modifier = Modifier.weight(1f).testTag("ping_server_button")
                    ) {
                        if (isPinging) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Проверка...", fontSize = 12.sp)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Проверить доступ", fontSize = 12.sp)
                        }
                    }
                }

                if (pingStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pingStatus!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (isPingSuccess) {
                            true -> StatusOnline
                            false -> StatusFailed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanUrl = customUrlInput.trim()
                    if (cleanUrl.isNotEmpty() && !isValidHttpsBaseUrl(cleanUrl)) {
                        isPingSuccess = false
                        pingStatus = "Укажите корректный HTTPS-адрес без пути и параметров"
                    } else {
                        scope.launch {
                            sessionManager.setCustomSupabaseUrl(cleanUrl.ifBlank { null })
                            AppConfig.customSupabaseUrl = cleanUrl.ifBlank { null }
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = extra.accentColor),
                modifier = Modifier.testTag("save_proxy_button")
            ) {
                Text("Сохранить", color = extra.bubbleOutgoingTextColor)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    customUrlInput = ""
                    scope.launch {
                        sessionManager.setCustomSupabaseUrl(null)
                        AppConfig.customSupabaseUrl = null
                        onDismiss()
                    }
                }
            ) {
                Text("Сброс")
            }
        }
    )
}
