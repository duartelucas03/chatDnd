package com.example.easychat.ui.main

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.easychat.model.RecentChatUiModel
import com.example.easychat.ui.chat.ChatActivity
import com.example.easychat.ui.compose.components.RecentChatRow
import com.example.easychat.utils.AndroidUtil

@Composable
fun ChatListScreen(viewModel: MainViewModel) {
    val context      = LocalContext.current
    val recentChats  by viewModel.recentChats.collectAsStateWithLifecycle()
    val loading      by viewModel.loading.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Recarrega ao voltar para a tela — igual ao onResume do Fragment original
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadRecentChats()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (loading && recentChats.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (recentChats.isEmpty()) {
            Text(
                text = "Nenhuma conversa ainda.\nBusque um usuário para começar!",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = recentChats,
                    key   = { it.chatroomId }
                ) { chat ->
                    RecentChatRow(
                        displayName        = chat.displayName,
                        lastMessagePreview = chat.lastMessagePreview,
                        lastMessageTime    = chat.lastMessageTime,
                        avatarUrl          = chat.avatarUrl,
                        onClick = { navigateToChat(context, chat) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 80.dp))
                }
            }
        }
    }
}

private fun navigateToChat(
    context: android.content.Context,
    item: RecentChatUiModel
) {
    val intent = Intent(context, ChatActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    when {
        item.isGroup -> {
            AndroidUtil.passChatroomIdAsIntent(intent, item.chatroomId, item.displayName, item.avatarUrl)
            context.startActivity(intent)
        }
        item.otherUser != null -> {
            AndroidUtil.passUserModelAsIntent(intent, item.otherUser)
            context.startActivity(intent)
        }
    }
}