package com.example.easychat.ui.chat

import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.easychat.R
import com.example.easychat.model.ChatMessageModel
import com.example.easychat.utils.CryptoManager

class ChatRecyclerAdapter(
    private val currentUserId: String,
    private val onLongClick: (ChatMessageModel) -> Unit
) : ListAdapter<ChatMessageModel, ChatRecyclerAdapter.ChatViewHolder>(DIFF) {

    var highlightKeyword: String = ""

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatMessageModel>() {
            override fun areItemsTheSame(a: ChatMessageModel, b: ChatMessageModel) =
                a.id == b.id || (a.localId != null && a.localId == b.localId)
            override fun areContentsTheSame(a: ChatMessageModel, b: ChatMessageModel) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_message_recycler_row, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val leftLayout: LinearLayout = view.findViewById(R.id.left_chat_layout)
        val rightLayout: LinearLayout = view.findViewById(R.id.right_chat_layout)
        val leftText: TextView = view.findViewById(R.id.left_chat_textview)
        val rightText: TextView = view.findViewById(R.id.right_chat_textview)
        val leftImage: ImageView = view.findViewById(R.id.left_chat_image)
        val rightImage: ImageView = view.findViewById(R.id.right_chat_image)
        val leftAudioLayout: LinearLayout = view.findViewById(R.id.left_audio_layout)
        val rightAudioLayout: LinearLayout = view.findViewById(R.id.right_audio_layout)
        val leftLocationLayout: LinearLayout = view.findViewById(R.id.left_location_layout)
        val rightLocationLayout: LinearLayout = view.findViewById(R.id.right_location_layout)
        val leftTime: TextView = view.findViewById(R.id.left_chat_time)
        val rightTime: TextView = view.findViewById(R.id.right_chat_time)
        val leftStatus: TextView = view.findViewById(R.id.left_message_status)
        val rightStatus: TextView = view.findViewById(R.id.right_message_status)

        fun bind(msg: ChatMessageModel) {
            android.util.Log.d("MSG_TYPE", "type='${msg.type}' content='${msg.content}' mediaUrl='${msg.mediaUrl}'")
            val isMe = msg.senderId == currentUserId

            // Oculta tudo inicialmente
            leftLayout.visibility = View.GONE
            rightLayout.visibility = View.GONE

            // Long press para fixar (Req. 13)
            itemView.setOnLongClickListener {
                onLongClick(msg)
                true
            }

            val timeStr = formatTime(msg.createdAt)

            if (isMe) {
                rightLayout.visibility = View.VISIBLE
                bindContent(
                    msg = msg,
                    textView = rightText,
                    imageView = rightImage,
                    audioLayout = rightAudioLayout,
                    locationLayout = rightLocationLayout,
                    timeView = rightTime,
                    statusView = rightStatus,
                    timeStr = timeStr,
                    showStatus = true
                )
            } else {
                leftLayout.visibility = View.VISIBLE
                bindContent(
                    msg = msg,
                    textView = leftText,
                    imageView = leftImage,
                    audioLayout = leftAudioLayout,
                    locationLayout = leftLocationLayout,
                    timeView = leftTime,
                    statusView = leftStatus,
                    timeStr = timeStr,
                    showStatus = false
                )
            }
        }

        private fun bindContent(
            msg: ChatMessageModel,
            textView: TextView,
            imageView: ImageView,
            audioLayout: LinearLayout,
            locationLayout: LinearLayout,
            timeView: TextView,
            statusView: TextView,
            timeStr: String,
            showStatus: Boolean
        ) {
            textView.visibility = View.GONE
            imageView.visibility = View.GONE
            audioLayout.visibility = View.GONE
            locationLayout.visibility = View.GONE

            android.util.Log.d("BIND_TYPE", "Binding type: ${msg.type}")

            when (msg.type ?: "text") {
                "image" -> {
                    android.util.Log.d("BIND_TYPE", "Mostrando imagem: ${msg.mediaUrl}")
                    imageView.visibility = View.VISIBLE
                    if (!msg.mediaUrl.isNullOrBlank()) {
                        Glide.with(imageView.context)
                            .load(msg.mediaUrl)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .into(imageView)
                    }
                }
                "audio" -> {
                    android.util.Log.d("BIND_TYPE", "Mostrando audio")
                    audioLayout.visibility = View.VISIBLE
                    audioLayout.tag = msg.mediaUrl
                    audioLayout.setOnClickListener {
                        val url = audioLayout.tag as? String ?: return@setOnClickListener
                        val player = android.media.MediaPlayer()
                        player.setDataSource(url)
                        player.prepareAsync()
                        player.setOnPreparedListener { it.start() }
                    }
                }
                "location" -> {
                    android.util.Log.d("BIND_TYPE", "Mostrando location")
                    locationLayout.visibility = View.VISIBLE
                    val locText = locationLayout.getChildAt(0) as? TextView
                    locText?.text = if (msg.locationLat != null && msg.locationLng != null)
                        "📍 ${"%.5f".format(msg.locationLat)}, ${"%.5f".format(msg.locationLng)}"
                    else "📍 Localização"
                }
                else -> {
                    textView.visibility = View.VISIBLE
                    val decrypted = CryptoManager.decrypt(msg.content ?: "")
                    textView.text = applyHighlight(decrypted, textView)
                }
            }

            timeView.text = timeStr
            timeView.visibility = View.VISIBLE

            if (showStatus) {
                statusView.visibility = View.VISIBLE
                statusView.text = when (msg.status) {
                    "read" -> "✓✓"
                    "delivered" -> "✓✓"
                    else -> "✓"
                }
            } else {
                statusView.visibility = View.GONE
            }
        }

        // Req. 14 — destaque de texto por palavra-chave
        private fun applyHighlight(text: String, textView: TextView): SpannableString {
            val spannable = SpannableString(text)
            if (highlightKeyword.isNotBlank()) {
                val lower = text.lowercase()
                val keyword = highlightKeyword.lowercase()
                var start = lower.indexOf(keyword)
                val color = ContextCompat.getColor(textView.context, R.color.highlight_yellow)
                while (start >= 0) {
                    val end = start + keyword.length
                    spannable.setSpan(BackgroundColorSpan(color), start, end, 0)
                    spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
                    start = lower.indexOf(keyword, end)
                }
            }
            return spannable
        }

        private fun formatTime(createdAt: String): String {
            return try {
                val instant = java.time.Instant.parse(createdAt)
                val local = instant.atZone(java.time.ZoneId.systemDefault())
                String.format("%02d:%02d", local.hour, local.minute)
            } catch (e: Exception) {
                ""
            }
        }
    }
}
