package com.example.easychat.ui.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.example.easychat.model.UserModel
import com.example.easychat.ui.compose.theme.EasyChatTheme
import com.example.easychat.utils.AndroidUtil

class ChatActivity : ComponentActivity() {

    private lateinit var otherUser: UserModel

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(otherUser, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val chatroomId = AndroidUtil.getChatroomIdFromIntent(intent)
        otherUser = if (chatroomId != null) {
            UserModel(
                id        = "",
                username  = intent.getStringExtra("chatroom_display_name") ?: "Grupo",
                avatarUrl = intent.getStringExtra("chatroom_avatar_url")?.takeIf { it.isNotBlank() }
            )
        } else {
            AndroidUtil.getUserModelFromIntent(intent)
        }

        setContent {
            EasyChatTheme {
                ChatScreen(
                    viewModel  = viewModel,
                    chatroomId = chatroomId,
                    onBack     = { onBackPressedDispatcher.onBackPressed() }
                )
            }
        }

        if (chatroomId != null) viewModel.setExistingChatroom(chatroomId)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshOnResume()
    }
}