package com.example

import android.app.Application
import com.example.core.notifications.NotificationHelper
import com.example.core.push.fcm.FcmPushProvider
import com.example.data.local.NexoDatabase
import com.example.data.local.SessionManager
import com.example.data.remote.SupabaseApiClient
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.MessageRepositoryImpl
import com.example.data.repository.ProfileRepositoryImpl
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ChatRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.repository.ProfileRepository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.core.config.AppConfig

class NexoApplication : Application() {

    lateinit var sessionManager: SessionManager private set
    lateinit var database: NexoDatabase private set
    lateinit var apiClient: SupabaseApiClient private set
    lateinit var notificationHelper: NotificationHelper private set
    lateinit var pushProvider: FcmPushProvider private set

    lateinit var authRepository: AuthRepository private set
    lateinit var profileRepository: ProfileRepository private set
    lateinit var chatRepository: ChatRepository private set
    lateinit var messageRepository: MessageRepository private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        sessionManager = SessionManager(this)
        database = NexoDatabase.getInstance(this)
        apiClient = SupabaseApiClient()
        notificationHelper = NotificationHelper(this)
        pushProvider = FcmPushProvider(this)

        authRepository = AuthRepositoryImpl(apiClient, sessionManager, database)
        profileRepository = ProfileRepositoryImpl(apiClient, sessionManager, database)
        chatRepository = ChatRepositoryImpl(apiClient, sessionManager, database)
        messageRepository = MessageRepositoryImpl(apiClient, sessionManager, database)

        CoroutineScope(Dispatchers.IO).launch {
            AppConfig.customSupabaseUrl = sessionManager.getCustomSupabaseUrl()
        }
    }

    companion object {
        lateinit var instance: NexoApplication private set
    }
}
