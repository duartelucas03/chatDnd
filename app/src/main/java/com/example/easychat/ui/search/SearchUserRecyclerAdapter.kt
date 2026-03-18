package com.example.easychat.ui.search

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
import com.example.easychat.model.UserModel
import com.example.easychat.ui.chat.ChatActivity
import com.example.easychat.utils.AndroidUtil
import com.example.easychat.utils.SupabaseClientProvider

class SearchUserRecyclerAdapter(
    private val context: Context
) : ListAdapter<UserModel, SearchUserRecyclerAdapter.UserViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UserModel>() {
            override fun areItemsTheSame(a: UserModel, b: UserModel) = a.id == b.id
            override fun areContentsTheSame(a: UserModel, b: UserModel) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.search_user_recycler_row, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val usernameText: TextView = view.findViewById(R.id.user_name_text)
        val phoneText: TextView = view.findViewById(R.id.phone_text)
        val profilePic: ImageView = view.findViewById(R.id.profile_pic_image_view)

        fun bind(user: UserModel) {
            usernameText.text =
                if (user.id == SupabaseClientProvider.currentUserId()) "${user.username} (Eu)"
                else user.username

            phoneText.text = user.phone

            if (!user.avatarUrl.isNullOrBlank()) {
                Glide.with(context)
                    .load(user.avatarUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(profilePic)
            }

            itemView.setOnClickListener {
                val intent = Intent(context, ChatActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                AndroidUtil.passUserModelAsIntent(intent, user)
                context.startActivity(intent)
            }
        }
    }
}
