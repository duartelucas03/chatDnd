package com.example.easychat.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.easychat.R
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.ui.auth.SplashActivity
import com.example.easychat.utils.SupabaseClientProvider
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FCMNotificationService : FirebaseMessagingService() {

    private val userRepository = UserRepository()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (SupabaseClientProvider.isLoggedIn()) {
            CoroutineScope(Dispatchers.IO).launch {
                try { userRepository.updateFcmToken(token) } catch (e: Exception) { /* silencioso */ }
            }
        }
    }

    // FIX: implementado para exibir notificação local quando o app está em foreground.
    // Em background, o FCM já exibe automaticamente via notification payload.
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Nova mensagem"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""

        val userId = message.data["userId"] ?: ""

        // Intent que abre o chat correto ao tocar na notificação
        val intent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (userId.isNotBlank()) putExtra("userId", userId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.chat_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "easychat_channel"
    }
}
