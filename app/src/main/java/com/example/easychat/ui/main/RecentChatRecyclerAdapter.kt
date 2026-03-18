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
import com.example.easychat.model.RecentChatUiModel
import com.example.easychat.ui.chat.ChatActivity
import com.example.easychat.utils.AndroidUtil

/**
 * Adapter 100% View: recebe [RecentChatUiModel] já prontos do ViewModel.
 * Sem acesso a repositórios, banco, criptografia ou lógica de negócio.
 */
class RecentChatRecyclerAdapter(
    private val context: Context
) : ListAdapter<RecentChatUiModel, RecentChatRecyclerAdapter.ChatroomViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RecentChatUiModel>() {
            override fun areItemsTheSame(a: RecentChatUiModel, b: RecentChatUiModel) =
                a.chatroomId == b.chatroomId
            override fun areContentsTheSame(a: RecentChatUiModel, b: RecentChatUiModel) =
                a == b
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
        val usernameText: TextView    = view.findViewById(R.id.user_name_text)
        val lastMessageText: TextView = view.findViewById(R.id.last_message_text)
        val lastMessageTime: TextView = view.findViewById(R.id.last_message_time_text)
        val profilePic: ImageView     = view.findViewById(R.id.profile_pic_image_view)

        fun bind(item: RecentChatUiModel) {
            usernameText.text    = item.displayName
            lastMessageText.text = item.lastMessagePreview
            lastMessageTime.text = item.lastMessageTime

            if (!item.avatarUrl.isNullOrBlank()) {
                Glide.with(context)
                    .load(item.avatarUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(profilePic)
            }

            itemView.setOnClickListener {
                val intent = Intent(context, ChatActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                when {
                    item.isGroup -> {
                        AndroidUtil.passChatroomIdAsIntent(intent, item.chatroomId, item.displayName, item.avatarUrl)
                        context.startActivity(intent)
                    }
                    item.otherUser != null -> {
                        AndroidUtil.passUserModelAsIntent(intent, item.otherUser)
                        context.startActivity(intent)
                    }
                }
            }
        }
    }
}
