package com.example.easychat.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.easychat.model.UserModel

/** Claude AI - início
 * Prompt: Crie o ViewModel da tela de chat. Ele carrega as mensagens do cache local e do Supabase, escuta novas mensagens em tempo real, descriptografa o conteúdo, e permite enviar texto, imagem, áudio e localização. Também suporta fixar mensagem, filtrar por palavra-chave, marcar como lida e gerenciar membros do grupo.
 */

class ChatViewModelFactory(
    private val otherUser: UserModel,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(otherUser, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
/** Claude AI - final */