package com.example.easychat.model

/**
 * Modelo de apresentação para cada mensagem no chat.
 * O ViewModel popula este objeto; o Adapter apenas exibe — sem descriptografia
 * nem formatação de tempo na camada de View.
 */
data class ChatMessageUiModel(
    val id: String,
    val localId: String?,
    val isFromMe: Boolean,
    val type: String,           // "text" | "image" | "audio" | "location"
    val text: String?,          // já descriptografado (somente para type="text")
    val mediaUrl: String?,
    val locationLat: Double?,
    val locationLng: Double?,
    val timeFormatted: String,  // já formatado "HH:mm"
    val statusSymbol: String,   // "✓" ou "✓✓"
    val showStatus: Boolean,
    val isPinned: Boolean,
    // Referência ao modelo original para operações (pin, mark-as-read)
    val source: ChatMessageModel
)
