package com.example.easychat.service

import com.example.easychat.data.repository.UserRepository
import com.example.easychat.utils.SupabaseClientProvider
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FCMNotificationService : FirebaseMessagingService() {

    private val userRepository = UserRepository()

    // Chamado quando o token FCM é atualizado
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (SupabaseClientProvider.isLoggedIn()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    userRepository.updateFcmToken(token)
                } catch (e: Exception) { /* silencioso */ }
            }
        }
    }

    // Req. 4 — tratamento de mensagem recebida em foreground
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Notificações em background são tratadas automaticamente pelo FCM.
        // Para foreground, você pode criar uma notificação local aqui se necessário.
    }
}
