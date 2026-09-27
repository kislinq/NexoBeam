package com.example.core.push.fcm

import android.content.Context
import android.util.Log
import com.example.core.push.PushProvider

class FcmPushProvider(private val context: Context) : PushProvider {
    override val providerName: String = "fcm"

    override suspend fun registerDevice(userId: String): Result<String> {
        return try {
            // FCM token acquisition placeholder; when google-services.json is present,
            // FirebaseMessaging.getInstance().token.await() returns the real FCM registration token.
            val token = "fcm_token_${userId.take(8)}_${System.currentTimeMillis()}"
            Log.d("FcmPushProvider", "Device registered for user $userId with token: $token")
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unregisterDevice(userId: String): Result<Unit> {
        Log.d("FcmPushProvider", "Device unregistered for user $userId")
        return Result.success(Unit)
    }
}
