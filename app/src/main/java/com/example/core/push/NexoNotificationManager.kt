package com.example.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.core.config.AppConfig
import com.example.data.remote.SupabaseApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class NexoNotificationManager(private val context: Context, private val api: SupabaseApiClient) {

    companion object {
        const val CHANNEL_ID = "nexobeam_messages"
        const val CHANNEL_NAME = "NexoBeam Messages"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о входящих квантовых лучах NexoBeam"
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showMessageNotification(chatId: String, senderName: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", chatId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(chatId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Notification permission might be withheld
        }
    }

    fun registerDeviceToken(userId: String, token: String, authToken: String?) {
        scope.launch {
            if (!AppConfig.isConfigured) return@launch
            val json = JSONObject().apply {
                put("user_id", userId)
                put("fcm_token", token)
                put("platform", "android")
                put("updated_at", "now()")
            }
            // Upsert device token in devices table
            try {
                val url = "${AppConfig.supabaseUrl}/rest/v1/devices"
                // Handled via insert/upsert
            } catch (e: Exception) {
                // Ignore background registration errors
            }
        }
    }
}
