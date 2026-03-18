package com.example.easychat.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.easychat.model.UserModel

class ChatViewModelFactory(
    private val otherUser: UserModel,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(otherUser).apply { initLocalRepo(context) } as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
