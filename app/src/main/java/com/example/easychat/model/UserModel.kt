package com.example.easychat.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class UserModel(
    val id: String = "",
    val username: String = "",
    val phone: String = "",
    @SerialName("fcm_token")  val fcmToken: String = "",
    val status: String = "offline",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("last_seen")  val lastSeen: String = "",
    @SerialName("created_at") val createdAt: String = ""
) : Parcelable
