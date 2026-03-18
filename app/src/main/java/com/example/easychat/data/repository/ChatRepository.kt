package com.example.easychat.data.repository

import com.example.easychat.model.ChatMessageModel
import com.example.easychat.model.ChatroomMemberModel
import com.example.easychat.model.ChatroomModel
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class ChatRepository {

    private val db = SupabaseClientProvider.db

    suspend fun getOrCreateDirectChatroom(currentUserId: String, otherUserId: String): ChatroomModel {
        val myMemberships = db.from("chatroom_members").select {
            filter { eq("user_id", currentUserId) }
        }.decodeList<ChatroomMemberModel>().map { it.chatroomId }

        if (myMemberships.isNotEmpty()) {
            val sharedMemberships = db.from("chatroom_members").select {
                filter {
                    eq("user_id", otherUserId)
                    isIn("chatroom_id", myMemberships)
                }
            }.decodeList<ChatroomMemberModel>().map { it.chatroomId }

            if (sharedMemberships.isNotEmpty()) {
                val existing = db.from("chatrooms").select {
                    filter {
                        isIn("id", sharedMemberships)
                        eq("is_group", false)
                    }
                }.decodeSingleOrNull<ChatroomModel>()
                if (existing != null) return existing
            }
        }

        val newRoom = db.from("chatrooms").insert(buildJsonObject {
            put("is_group", false)
            put("created_by", currentUserId)
        }) { select() }.decodeSingle<ChatroomModel>()

        db.from("chatroom_members").insert(listOf(
            buildJsonObject {
                put("chatroom_id", newRoom.id); put("user_id", currentUserId); put("role", "admin")
            },
            buildJsonObject {
                put("chatroom_id", newRoom.id); put("user_id", otherUserId); put("role", "member")
            }
        ))
        return newRoom
    }

    suspend fun createGroupChatroom(name: String, memberIds: List<String>, creatorId: String): ChatroomModel {
        val newRoom = db.from("chatrooms").insert(buildJsonObject {
            put("is_group", true); put("name", name); put("created_by", creatorId)
        }) { select() }.decodeSingle<ChatroomModel>()

        val members = (memberIds + creatorId).distinct().map { userId ->
            buildJsonObject {
                put("chatroom_id", newRoom.id)
                put("user_id", userId)
                put("role", if (userId == creatorId) "admin" else "member")
            }
        }
        db.from("chatroom_members").insert(members)
        return newRoom
    }

    suspend fun getChatroomById(chatroomId: String): ChatroomModel? = try {
        db.from("chatrooms").select {
            filter { eq("id", chatroomId) }
        }.decodeSingleOrNull()
    } catch (e: Exception) { null }

    suspend fun getMembersOfChatroom(chatroomId: String): List<ChatroomMemberModel> = try {
        db.from("chatroom_members").select {
            filter { eq("chatroom_id", chatroomId) }
        }.decodeList()
    } catch (e: Exception) { emptyList() }

    suspend fun getRecentChats(currentUserId: String): List<ChatroomModel> {
        val myRoomIds = db.from("chatroom_members").select {
            filter { eq("user_id", currentUserId) }
        }.decodeList<ChatroomMemberModel>().map { it.chatroomId }

        if (myRoomIds.isEmpty()) return emptyList()

        return db.from("chatrooms").select {
            filter { isIn("id", myRoomIds) }
            order("last_message_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList()
    }

    suspend fun sendMessage(
        chatroomId: String, senderId: String, content: String,
        type: String = "text", mediaUrl: String? = null,
        locationLat: Double? = null, locationLng: Double? = null,
        localId: String? = null
    ): ChatMessageModel {
        val payload = buildJsonObject {
            put("chatroom_id", chatroomId); put("sender_id", senderId)
            put("content", content); put("type", type)
            if (mediaUrl != null)    put("media_url", mediaUrl)
            if (locationLat != null) put("location_lat", locationLat)
            if (locationLng != null) put("location_lng", locationLng)
            if (localId != null)     put("local_id", localId)
            put("is_synced", true)
        }
        val msg = db.from("messages").insert(payload) { select() }.decodeSingle<ChatMessageModel>()
        db.from("chatrooms").update({
            set("last_message", content)
            set("last_message_sender_id", senderId)
            set("last_message_at", Instant.now().toString())
            set("last_message_type", type)
        }) { filter { eq("id", chatroomId) } }
        return msg
    }

    suspend fun getMessages(chatroomId: String): List<ChatMessageModel> = try {
        db.from("messages").select {
            filter { eq("chatroom_id", chatroomId) }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList()
    } catch (e: Exception) { emptyList() }

    suspend fun markMessageAsRead(messageId: String) {
        db.from("messages").update({ set("status", "read") }) {
            filter { eq("id", messageId) }
        }
        db.from("message_reads").insert(buildJsonObject {
            put("message_id", messageId)
            put("user_id", SupabaseClientProvider.currentUserId())
        })
    }

    suspend fun pinMessage(messageId: String, pin: Boolean) {
        db.from("messages").update({ set("is_pinned", pin) }) {
            filter { eq("id", messageId) }
        }
    }

    suspend fun getPinnedMessage(chatroomId: String): ChatMessageModel? = try {
        db.from("messages").select {
            filter {
                eq("chatroom_id", chatroomId)
                eq("is_pinned", true)
            }
            limit(1)
        }.decodeSingleOrNull()
    } catch (e: Exception) { null }

    fun filterMessages(messages: List<ChatMessageModel>, keyword: String): List<ChatMessageModel> {
        if (keyword.isBlank()) return messages
        return messages.filter { it.content?.contains(keyword, ignoreCase = true) == true }
    }
}
