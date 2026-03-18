package com.example.easychat.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatroomMemberModel(
    @SerialName("chatroom_id")  val chatroomId: String = "",
    @SerialName("user_id")      val userId: String = "",
    val role: String = "member",
    @SerialName("last_read_at") val lastReadAt: String = "",
    @SerialName("joined_at")    val joinedAt: String = ""
)
