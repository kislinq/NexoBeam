package com.example.ui.screens.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.NexoDesignConcept
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onChangeConcept: (NexoDesignConcept) -> Unit
) {
    val extra = LocalNexoExtra.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepo = NexoApplication.instance.authRepository
    val profileRepo = NexoApplication.instance.profileRepository

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var isEditDialogOpen by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    var editUsername by remember { mutableStateOf("") }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val myId = authRepo.getActiveSessionUserId() ?: "user_me"
        val res = profileRepo.getProfile(myId)
        if (res is AppResult.Success) {
            profile = res.data
            editName = res.data.displayName
            editBio = res.data.bio ?: ""
            editUsername = res.data.username
        }
        profileRepo.updatePresence(myId)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingAvatar = true
            scope.launch {
                val myId = authRepo.getActiveSessionUserId() ?: "user_me"
                val uploadRes = profileRepo.uploadAvatar(myId, uri)
                isUploadingAvatar = false
                if (uploadRes is AppResult.Success) {
                    profile = profile?.copy(avatarUrl = uploadRes.data)
                    Toast.makeText(context, "Аватар обновлен", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, (uploadRes as? AppResult.Error)?.message ?: "Ошибка загрузки", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (isEditDialogOpen) {
        AlertDialog(
            onDismissRequest = { isEditDialogOpen = false },
            title = { Text(stringResource(R.string.edit_profile)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.display_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } },
                        label = { Text(stringResource(R.string.username_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text(stringResource(R.string.bio_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = profile
                        if (current != null) {
                            val updated = current.copy(
                                displayName = editName.trim(),
                                username = editUsername.trim(),
                                bio = editBio.trim()
                            )
                            scope.launch {
                                when (val result = profileRepo.saveProfile(updated)) {
                                    is AppResult.Success -> {
                                        profile = updated
                                        isEditDialogOpen = false
                                        Toast.makeText(context, "Профиль сохранен", Toast.LENGTH_SHORT).show()
                                    }
                                    is AppResult.Error -> {
                                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    }
                                    AppResult.Loading -> Unit
                                }
                            }
                        } else {
                            isEditDialogOpen = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = extra.accentColor)
                ) {
                    Text("Сохранить", color = extra.bubbleOutgoingTextColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditDialogOpen = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("profile_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = stringResource(R.string.my_profile),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { isEditDialogOpen = true },
                modifier = Modifier.testTag("edit_profile_button")
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Редактировать", tint = extra.accentColor)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar with camera badge
            Box(
                modifier = Modifier
                    .size(94.dp)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .testTag("profile_avatar_picker"),
                contentAlignment = Alignment.Center
            ) {
                NexoAvatar(
                    name = profile?.displayName ?: "User",
                    avatarUrl = profile?.avatarUrl,
                    size = 90.dp,
                    isOnline = true
                )

                if (isUploadingAvatar) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = extra.accentColor, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(extra.accentColor)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Сменить фото",
                            tint = extra.bubbleOutgoingTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = profile?.displayName ?: "Nexo Pilot",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "@" + (profile?.username ?: "pilot"),
                style = MaterialTheme.typography.bodyMedium,
                color = extra.accentColor,
                fontWeight = FontWeight.SemiBold
            )

            if (!profile?.bio.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = profile?.bio ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Backend Status Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(extra.cornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, extra.cardBorderColor, RoundedCornerShape(extra.cornerRadius))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "СТАТУС ПОДКЛЮЧЕНИЯ BACKEND",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.accentColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (AppConfig.isConfigured) "● Подключено к Supabase: ${AppConfig.supabaseUrl}"
                        else "● Автономный защищенный контур (Demo / Offline-first)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Theme Switcher Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(extra.cornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, extra.cardBorderColor, RoundedCornerShape(extra.cornerRadius))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = extra.accentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.appearance_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    NexoDesignConcept.values().forEach { concept ->
                        val isSelected = extra.concept == concept
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onChangeConcept(concept) }
                                .background(if (isSelected) extra.accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSelected) "◉ " + concept.title else "○ " + concept.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) extra.accentColor else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Logout Button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        authRepo.logout()
                        onLogout()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("logout_button"),
                shape = RoundedCornerShape(extra.cornerRadius)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
