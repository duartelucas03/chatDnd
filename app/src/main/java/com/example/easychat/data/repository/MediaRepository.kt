package com.example.easychat.data.repository

import com.example.easychat.utils.SupabaseClientProvider
import io.github.jan.supabase.storage.upload
import java.util.UUID

class MediaRepository {

    private val storage = SupabaseClientProvider.storage

    /**
     * Faz upload de avatar e retorna a URL pública.
     */
    suspend fun uploadAvatar(userId: String, bytes: ByteArray): String {
        val path = "$userId.jpg"
        storage.from("avatars").upload(path, bytes) { upsert = true }
        return storage.from("avatars").publicUrl(path)
    }

    /**
     * Faz upload de imagem no chat e retorna a URL pública.
     */
    suspend fun uploadImage(chatroomId: String, bytes: ByteArray): String {
        val path = "$chatroomId/${UUID.randomUUID()}.jpg"
        storage.from("chat-media").upload(path, bytes) { upsert = false }
        return storage.from("chat-media").publicUrl(path)
    }

    /**
     * Faz upload de áudio e retorna a URL pública.
     */
    suspend fun uploadAudio(chatroomId: String, bytes: ByteArray): String {
        val path = "$chatroomId/${UUID.randomUUID()}.m4a"
        storage.from("audio-messages").upload(path, bytes) { upsert = false }
        return storage.from("audio-messages").publicUrl(path)
    }

    /**
     * Faz upload de vídeo e retorna a URL pública.
     */
    suspend fun uploadVideo(chatroomId: String, bytes: ByteArray): String {
        val path = "$chatroomId/${UUID.randomUUID()}.mp4"
        storage.from("chat-media").upload(path, bytes) { upsert = false }
        return storage.from("chat-media").publicUrl(path)
    }
}
