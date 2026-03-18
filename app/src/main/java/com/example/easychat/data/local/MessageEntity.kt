package com.example.easychat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidade Room para armazenamento local de mensagens.
 * Espelha os campos principais de ChatMessageModel.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatroomId: String,
    val senderId: String,
    val content: String?,
    val type: String,
    val mediaUrl: String?,
    val locationLat: Double?,
    val locationLng: Double?,
    val isPinned: Boolean,
    val status: String,
    val localId: String?,
    val isSynced: Boolean,
    val createdAt: String
)
