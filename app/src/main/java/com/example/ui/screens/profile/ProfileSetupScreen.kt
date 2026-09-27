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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.NexoApplication
import com.example.R
import com.example.core.model.AppResult
import com.example.domain.model.UserProfile
import com.example.ui.components.NexoAvatar
import com.example.ui.theme.LocalNexoExtra
import com.example.ui.theme.StatusFailed
import com.example.ui.theme.StatusOnline
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfileSetupScreen(
    onSetupCompleted: () -> Unit
) {
    val extra = LocalNexoExtra.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepo = NexoApplication.instance.authRepository
    val profileRepo = NexoApplication.instance.profileRepository

    var userId by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("Пилот узла связи NexoBeam") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    var isUsernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingUsername by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var checkJob by remember { mutableStateOf<Job?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingAvatar = true
            scope.launch {
                val currentUserId = authRepo.getActiveSessionUserId() ?: "user_me"
                val uploadRes = profileRepo.uploadAvatar(currentUserId, uri)
                isUploadingAvatar = false
                if (uploadRes is AppResult.Success) {
                    avatarUrl = uploadRes.data
                } else {
                    Toast.makeText(context, (uploadRes as? AppResult.Error)?.message ?: "Ошибка загрузки", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val currentUserId = authRepo.getActiveSessionUserId() ?: "user_me"
        userId = currentUserId
        val existing = profileRepo.getProfile(currentUserId)
        if (existing is AppResult.Success) {
            username = existing.data.username
            displayName = existing.data.displayName
            bio = existing.data.bio ?: bio
            avatarUrl = existing.data.avatarUrl
        }
    }

    fun validateUsername(input: String) {
        username = input.lowercase().filter { it.isLetterOrDigit() || it == '_' }
        checkJob?.cancel()
        usernameError = null
        isUsernameAvailable = null

        if (username.length < 3) {
            usernameError = "Позывной должен быть не менее 3 символов"
            return
        }
        if (username.length > 30) {
            usernameError = "Максимум 30 символов"
            return
        }

        isCheckingUsername = true
        checkJob = scope.launch {
            delay(400)
            val res = authRepo.checkUsernameAvailability(username)
            isCheckingUsername = false
            if (res is AppResult.Success) {
                isUsernameAvailable = res.data
                if (!res.data) {
                    usernameError = "Позывной уже занят другим узлом"
                }
            } else {
                isUsernameAvailable = true
            }
        }
    }

    fun handleSave() {
        if (displayName.trim().isEmpty()) {
            generalError = "Введите отображаемое имя"
            return
        }
        if (username.trim().length < 3) {
            generalError = "Позывной должен содержать от 3 символов"
            return
        }
        if (isUsernameAvailable == false) {
            generalError = "Выберите свободный позывной"
            return
        }

        isSaving = true
        generalError = null

        scope.launch {
            val profile = UserProfile(
                id = userId,
                username = username.trim(),
                displayName = displayName.trim(),
                avatarUrl = avatarUrl,
                bio = bio.trim(),
                lastSeen = System.currentTimeMillis(),
                isOnline = true
            )

            val res = profileRepo.saveProfile(profile)
            isSaving = false
            if (res is AppResult.Success) {
                onSetupCompleted()
            } else {
                generalError = (res as? AppResult.Error)?.message ?: "Ошибка сохранения профиля"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 40.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Interactive Avatar
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .testTag("setup_avatar_picker"),
                contentAlignment = Alignment.Center
            ) {
                NexoAvatar(
                    name = displayName.ifBlank { "Nexo" },
                    avatarUrl = avatarUrl,
                    size = 84.dp,
                    isOnline = true
                )

                if (isUploadingAvatar) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = extra.accentColor, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(extra.accentColor)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Выбрать фото",
                            tint = extra.bubbleOutgoingTextColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.profile_setup_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.profile_setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Callsign / Username input with live indicator
            OutlinedTextField(
                value = username,
                onValueChange = { validateUsername(it) },
                label = { Text(stringResource(R.string.username_label)) },
                placeholder = { Text(stringResource(R.string.username_hint)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.AlternateEmail, contentDescription = null, tint = extra.accentColor)
                },
                trailingIcon = {
                    when {
                        isCheckingUsername -> {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = extra.accentColor, strokeWidth = 2.dp)
                        }
                        isUsernameAvailable == true -> {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Доступен", tint = StatusOnline)
                        }
                        isUsernameAvailable == false -> {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Занят", tint = StatusFailed)
                        }
                    }
                },
                isError = usernameError != null,
                supportingText = {
                    if (usernameError != null) {
                        Text(text = usernameError ?: "", color = MaterialTheme.colorScheme.error)
                    } else if (isUsernameAvailable == true) {
                        Text(text = "Позывной свободен!", color = StatusOnline)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_username_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = extra.accentColor,
                    focusedLabelColor = extra.accentColor
                ),
                shape = RoundedCornerShape(extra.cornerRadius)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Display Name input
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; generalError = null },
                label = { Text(stringResource(R.string.display_name_label)) },
                placeholder = { Text(stringResource(R.string.display_name_hint)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Badge, contentDescription = null, tint = extra.accentColor)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_display_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = extra.accentColor,
                    focusedLabelColor = extra.accentColor
                ),
                shape = RoundedCornerShape(extra.cornerRadius)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bio / Node status input
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text(stringResource(R.string.bio_label)) },
                placeholder = { Text(stringResource(R.string.bio_hint)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = extra.accentColor)
                },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_bio_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = extra.accentColor,
                    focusedLabelColor = extra.accentColor
                ),
                shape = RoundedCornerShape(extra.cornerRadius)
            )

            if (generalError != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = generalError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = { handleSave() },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("setup_save_button"),
                shape = RoundedCornerShape(extra.cornerRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = extra.accentColor,
                    contentColor = extra.bubbleOutgoingTextColor
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = extra.bubbleOutgoingTextColor,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.btn_save_profile),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

