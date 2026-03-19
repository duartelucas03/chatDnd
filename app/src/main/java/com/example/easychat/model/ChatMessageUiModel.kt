package com.example.easychat.model

data class ChatMessageUiModel(
    val id: String,
    val localId: String?,
    val isFromMe: Boolean,
    val type: String,
    val text: String?,
    val mediaUrl: String?,
    val locationLat: Double?,
    val locationLng: Double?,
    val timeFormatted: String,
    val statusSymbol: String,
    val showStatus: Boolean,
    val isPinned: Boolean,
    val highlightKeyword: String = "",
    val source: ChatMessageModel
)