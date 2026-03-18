package com.example.easychat.data.local

import android.content.Context
import com.example.easychat.model.ChatMessageModel

/**
 * Repositório local (Room) para mensagens.
 * Usado para:
 *   1. Exibir mensagens offline (sem internet)
 *   2. Cache para evitar flickering ao reabrir o chat
 *   3. Fila de sync: mensagens marcadas isSynced=false são reenviadas ao voltar online
 */
class LocalMessageRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).messageDao()

    suspend fun getMessages(chatroomId: String): List<ChatMessageModel> =
        dao.getMessages(chatroomId).map { it.toDomain() }

    suspend fun saveMessages(messages: List<ChatMessageModel>) {
        dao.insertAll(messages.map { it.toEntity() })
    }

    suspend fun saveMessage(message: ChatMessageModel) {
        dao.insert(message.toEntity())
    }

    suspend fun updateStatus(id: String, status: String) {
        dao.updateStatus(id, status)
    }

    suspend fun getUnsyncedMessages(): List<ChatMessageModel> =
        dao.getUnsyncedMessages().map { it.toDomain() }

    suspend fun markSynced(id: String) {
        dao.markSynced(id)
    }

    // --- Extensões de conversão ---

    private fun MessageEntity.toDomain() = ChatMessageModel(
        id          = id,
        chatroomId  = chatroomId,
        senderId    = senderId,
        content     = content,
        type        = type,
        mediaUrl    = mediaUrl,
        locationLat = locationLat,
        locationLng = locationLng,
        isPinned    = isPinned,
        status      = status,
        localId     = localId,
        isSynced    = isSynced,
        createdAt   = createdAt
    )

    private fun ChatMessageModel.toEntity() = MessageEntity(
        id          = id,
        chatroomId  = chatroomId,
        senderId    = senderId,
        content     = content,
        type        = type ?: "text",
        mediaUrl    = mediaUrl,
        locationLat = locationLat,
        locationLng = locationLng,
        isPinned    = isPinned,
        status      = status,
        localId     = localId,
        isSynced    = isSynced,
        createdAt   = createdAt
    )
}
