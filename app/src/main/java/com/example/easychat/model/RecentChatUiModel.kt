package com.example.easychat.model

/**
 * Modelo de apresentação para cada linha da lista de chats recentes.
 * O ViewModel monta esse objeto; o Adapter apenas exibe — sem lógica de negócio.
 */
data class RecentChatUiModel(
    val chatroomId: String,
    val displayName: String,       // username do outro usuário (ou nome do grupo)
    val avatarUrl: String?,
    val lastMessagePreview: String, // já descriptografado e formatado ("Você: Olá" / "📷 Foto")
    val lastMessageTime: String,    // já formatado ("10:45" ou "12/03")
    val isGroup: Boolean,
    val otherUser: UserModel?       // null para grupos
)
