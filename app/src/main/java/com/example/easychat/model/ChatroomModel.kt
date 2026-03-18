package com.example.easychat.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class ChatroomModel(
    val id: String = "",
    @SerialName("is_group")               val isGroup: Boolean = false,
    val name: String? = null,
    @SerialName("avatar_url")             val avatarUrl: String? = null,
    @SerialName("last_message")           val lastMessage: String? = null,
    @SerialName("last_message_sender_id") val lastMessageSenderId: String? = null,
    @SerialName("last_message_at")        val lastMessageAt: String? = null,
    @SerialName("last_message_type")      val lastMessageType: String? = null,
    @SerialName("created_by")             val createdBy: String? = null,
    @SerialName("created_at")             val createdAt: String = ""
) : Parcelable
