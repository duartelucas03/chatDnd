package com.example.easychat.model


data class RecentChatUiModel(
    val chatroomId: String,
    val displayName: String,
    val avatarUrl: String?,
    val lastMessagePreview: String,
    val lastMessageTime: String,
    val isGroup: Boolean,
    val otherUser: UserModel?
)
