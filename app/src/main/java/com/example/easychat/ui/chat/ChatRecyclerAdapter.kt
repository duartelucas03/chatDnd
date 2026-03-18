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
import com.example.easychat.model.ChatMessageUiModel

/**
 * Adapter 100% View: recebe [ChatMessageUiModel] já prontos do ViewModel.
 * Sem descriptografia, sem formatação de tempo, sem lógica de negócio.
 */
class ChatRecyclerAdapter(
    private val onLongClick: (ChatMessageUiModel) -> Unit
) : ListAdapter<ChatMessageUiModel, ChatRecyclerAdapter.ChatViewHolder>(DIFF) {

    var highlightKeyword: String = ""

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatMessageUiModel>() {
            override fun areItemsTheSame(a: ChatMessageUiModel, b: ChatMessageUiModel) =
                a.id == b.id || (a.localId != null && a.localId == b.localId)
            override fun areContentsTheSame(a: ChatMessageUiModel, b: ChatMessageUiModel) =
                a == b
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
        val leftLayout: LinearLayout  = view.findViewById(R.id.left_chat_layout)
        val rightLayout: LinearLayout = view.findViewById(R.id.right_chat_layout)
        val leftText: TextView        = view.findViewById(R.id.left_chat_textview)
        val rightText: TextView       = view.findViewById(R.id.right_chat_textview)
        val leftImage: ImageView      = view.findViewById(R.id.left_chat_image)
        val rightImage: ImageView     = view.findViewById(R.id.right_chat_image)
        val leftAudio: LinearLayout   = view.findViewById(R.id.left_audio_layout)
        val rightAudio: LinearLayout  = view.findViewById(R.id.right_audio_layout)
        val leftLoc: LinearLayout     = view.findViewById(R.id.left_location_layout)
        val rightLoc: LinearLayout    = view.findViewById(R.id.right_location_layout)
        val leftTime: TextView        = view.findViewById(R.id.left_chat_time)
        val rightTime: TextView       = view.findViewById(R.id.right_chat_time)
        val leftStatus: TextView      = view.findViewById(R.id.left_message_status)
        val rightStatus: TextView     = view.findViewById(R.id.right_message_status)

        fun bind(item: ChatMessageUiModel) {
            leftLayout.visibility  = View.GONE
            rightLayout.visibility = View.GONE

            itemView.setOnLongClickListener { onLongClick(item); true }

            if (item.isFromMe) {
                rightLayout.visibility = View.VISIBLE
                bindContent(item, rightText, rightImage, rightAudio, rightLoc, rightTime, rightStatus)
            } else {
                leftLayout.visibility = View.VISIBLE
                bindContent(item, leftText, leftImage, leftAudio, leftLoc, leftTime, leftStatus)
            }
        }

        private fun bindContent(
            item: ChatMessageUiModel,
            textView: TextView, imageView: ImageView,
            audioLayout: LinearLayout, locationLayout: LinearLayout,
            timeView: TextView, statusView: TextView
        ) {
            textView.visibility       = View.GONE
            imageView.visibility      = View.GONE
            audioLayout.visibility    = View.GONE
            locationLayout.visibility = View.GONE

            when (item.type) {
                "image" -> {
                    imageView.visibility = View.VISIBLE
                    if (!item.mediaUrl.isNullOrBlank()) {
                        Glide.with(imageView.context)
                            .load(item.mediaUrl)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .into(imageView)
                    }
                }
                "audio" -> {
                    audioLayout.visibility = View.VISIBLE
                    audioLayout.tag = item.mediaUrl
                    audioLayout.setOnClickListener {
                        val url = audioLayout.tag as? String ?: return@setOnClickListener
                        try {
                            val player = android.media.MediaPlayer()
                            player.setDataSource(url)
                            player.prepareAsync()
                            player.setOnPreparedListener { it.start() }
                            player.setOnCompletionListener { it.release() }
                        } catch (e: Exception) { /* ignora */ }
                    }
                }
                "location" -> {
                    locationLayout.visibility = View.VISIBLE
                    val locText = locationLayout.getChildAt(0) as? TextView
                    locText?.text = if (item.locationLat != null && item.locationLng != null)
                        "📍 ${"%.5f".format(item.locationLat)}, ${"%.5f".format(item.locationLng)}"
                    else "📍 Localização"
                }
                else -> {
                    textView.visibility = View.VISIBLE
                    // texto já descriptografado pelo ViewModel; só aplica highlight visual
                    textView.text = applyHighlight(item.text ?: "", textView)
                }
            }

            timeView.text       = item.timeFormatted
            timeView.visibility = View.VISIBLE

            if (item.showStatus) {
                statusView.visibility = View.VISIBLE
                statusView.text       = item.statusSymbol
            } else {
                statusView.visibility = View.GONE
            }
        }

        private fun applyHighlight(text: String, textView: TextView): SpannableString {
            val spannable = SpannableString(text)
            if (highlightKeyword.isNotBlank()) {
                val lower   = text.lowercase()
                val keyword = highlightKeyword.lowercase()
                var start   = lower.indexOf(keyword)
                val color   = ContextCompat.getColor(textView.context, R.color.highlight_yellow)
                while (start >= 0) {
                    val end = start + keyword.length
                    spannable.setSpan(BackgroundColorSpan(color), start, end, 0)
                    spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
                    start = lower.indexOf(keyword, end)
                }
            }
            return spannable
        }
    }
}
