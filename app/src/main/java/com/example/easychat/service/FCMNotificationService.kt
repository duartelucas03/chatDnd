package com.example.easychat.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.easychat.R
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.ui.auth.SplashActivity
import com.example.easychat.utils.CryptoManager
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

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.data["title"]
            ?: message.notification?.title
            ?: "Nova mensagem"

        // FIX: o corpo da notificação vem do campo "body" do data payload,
        // que contém o conteúdo cru do banco — potencialmente criptografado.
        // Tentamos descriptografar; se não for texto criptografado (ex: "📷 Foto"),
        // o CryptoManager retorna o texto original sem alteração.
        val rawBody = message.data["body"]
            ?: message.notification?.body
            ?: ""

        val body = resolveNotificationBody(
            rawBody  = rawBody,
            type     = message.data["type"] ?: "text",
            senderName = message.data["senderName"] ?: ""
        )

        val userId = message.data["userId"] ?: ""

        val intent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (userId.isNotBlank()) putExtra("userId", userId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.chat_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Resolve o texto a exibir na notificação de acordo com o tipo da mensagem.
     *
     * A Edge Function do Supabase envia no campo "body" o conteúdo cru do banco
     * (já que ela não tem como saber a chave AES do app). Por isso:
     * - Para "text": descriptografa com CryptoManager.decrypt(). Se o texto não
     *   estiver criptografado (mensagem antiga, emoji, etc.), decrypt() retorna
     *   o valor original sem lançar exceção.
     * - Para outros tipos: usa um texto amigável fixo, pois não há texto a descriptografar.
     */
    private fun resolveNotificationBody(
        rawBody: String,
        type: String,
        senderName: String
    ): String {
        val content = when (type) {
            "image"    -> "📷 Foto"
            "audio"    -> "🎵 Áudio"
            "location" -> "📍 Localização"
            "video"    -> "🎬 Vídeo"
            else       -> CryptoManager.decrypt(rawBody) // "text" ou desconhecido
        }
        // Se a Edge Function enviar o nome do remetente, exibe "Nome: mensagem"
        return if (senderName.isNotBlank()) "$senderName: $content" else content
    }

    companion object {
        const val CHANNEL_ID = "easychat_channel"
    }
}
