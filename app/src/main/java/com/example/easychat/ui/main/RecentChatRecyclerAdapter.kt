package com.example.easychat.ui.main

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.easychat.R
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.model.ChatroomModel
import com.example.easychat.model.UserModel
import com.example.easychat.ui.chat.ChatActivity
import com.example.easychat.utils.AndroidUtil
import com.example.easychat.utils.CryptoManager
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RecentChatRecyclerAdapter(
    private val context: Context,
    private val userRepository: UserRepository = UserRepository()
) : ListAdapter<ChatroomModel, RecentChatRecyclerAdapter.ChatroomViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatroomModel>() {
            override fun areItemsTheSame(a: ChatroomModel, b: ChatroomModel) = a.id == b.id
            override fun areContentsTheSame(a: ChatroomModel, b: ChatroomModel) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatroomViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.recent_chat_recycler_row, parent, false)
        return ChatroomViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatroomViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatroomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val usernameText: TextView = view.findViewById(R.id.user_name_text)
        val lastMessageText: TextView = view.findViewById(R.id.last_message_text)
        val lastMessageTime: TextView = view.findViewById(R.id.last_message_time_text)
        val profilePic: ImageView = view.findViewById(R.id.profile_pic_image_view)

        fun bind(chatroom: ChatroomModel) {
            // Para grupos, exibe o nome diretamente
            if (chatroom.isGroup) {
                usernameText.text = chatroom.name ?: "Grupo"
                bindLastMessage(chatroom, senderId = chatroom.lastMessageSenderId ?: "")
                bindTime(chatroom.lastMessageAt)
                itemView.setOnClickListener { /* TODO: abrir GroupChatActivity */ }
                return
            }

            // Para chats diretos, busca o outro usuário
            CoroutineScope(Dispatchers.IO).launch {
                val memberIds = getMemberIds(chatroom.id)
                val otherUserId = memberIds.firstOrNull {
                    it != SupabaseClientProvider.currentUserId()
                } ?: return@launch
                val otherUser = userRepository.getUserById(otherUserId) ?: return@launch

                withContext(Dispatchers.Main) {
                    usernameText.text = otherUser.username
                    bindLastMessage(chatroom, senderId = chatroom.lastMessageSenderId ?: "")
                    bindTime(chatroom.lastMessageAt)
                    loadAvatar(otherUser)

                    itemView.setOnClickListener {
                        val intent = Intent(context, ChatActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        AndroidUtil.passUserModelAsIntent(intent, otherUser)
                        context.startActivity(intent)
                    }
                }
            }
        }

        private fun bindLastMessage(chatroom: ChatroomModel, senderId: String) {
            val decrypted = CryptoManager.decrypt(chatroom.lastMessage ?: "")
            val isMe = senderId == SupabaseClientProvider.currentUserId()
            lastMessageText.text = when (chatroom.lastMessageType) {
                "image" -> if (isMe) "Você: 📷 Foto" else "📷 Foto"
                "audio" -> if (isMe) "Você: 🎵 Áudio" else "🎵 Áudio"
                "location" -> if (isMe) "Você: 📍 Localização" else "📍 Localização"
                else -> if (isMe) "Você: $decrypted" else decrypted
            }
        }

        private fun bindTime(lastMessageAt: String?) {
            lastMessageTime.text = try {
                val instant = Instant.parse(lastMessageAt)
                val local = instant.atZone(ZoneId.systemDefault())
                val now = java.time.LocalDate.now(ZoneId.systemDefault())
                if (local.toLocalDate() == now) {
                    DateTimeFormatter.ofPattern("HH:mm").format(local)
                } else {
                    DateTimeFormatter.ofPattern("dd/MM").format(local)
                }
            } catch (e: Exception) {
                ""
            }
        }

        private fun loadAvatar(user: UserModel) {
            if (!user.avatarUrl.isNullOrBlank()) {
                Glide.with(context)
                    .load(user.avatarUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(profilePic)
            }
        }
    }

    private suspend fun getMemberIds(chatroomId: String): List<String> {
        return try {
            val db = SupabaseClientProvider.db
            db.from("chatroom_members").select {
                filter { eq("chatroom_id", chatroomId) }
            }.decodeList<com.example.easychat.model.ChatroomMemberModel>()
                .map { it.userId }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
