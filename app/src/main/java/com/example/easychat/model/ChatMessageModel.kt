package com.example.easychat.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class ChatMessageModel(
    val id: String = "",
    @SerialName("chatroom_id")    val chatroomId: String = "",
    @SerialName("sender_id")      val senderId: String = "",
    val content: String? = null,
    val type: String? = "text",
    @SerialName("media_url")       val mediaUrl: String? = null,
    @SerialName("media_thumbnail") val mediaThumbnail: String? = null,
    @SerialName("file_name")       val fileName: String? = null,
    @SerialName("file_size")       val fileSize: Long? = null,
    @SerialName("location_lat")    val locationLat: Double? = null,
    @SerialName("location_lng")    val locationLng: Double? = null,
    @SerialName("is_pinned")       val isPinned: Boolean = false,
    val status: String = "sent",
    @SerialName("local_id")        val localId: String? = null,
    @SerialName("is_synced")       val isSynced: Boolean = true,
    @SerialName("created_at")      val createdAt: String = ""
) : Parcelable