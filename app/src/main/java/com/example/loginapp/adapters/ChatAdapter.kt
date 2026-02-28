package com.example.loginapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.loginapp.R
import com.example.loginapp.models.AttachmentType
import com.example.loginapp.models.ChatMessage
import com.example.loginapp.models.MessageType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Formata o horário da mensagem
 */
private fun formatTime(timestamp: Date): String {
    val dateFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
    return dateFormat.format(timestamp)
}

/**
 * Adapter para lista de mensagens do chat
 */
class ChatAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (messages[position].type) {
            MessageType.SENT -> VIEW_TYPE_SENT
            MessageType.RECEIVED -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    /**
     * ViewHolder para mensagens enviadas
     */
    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val ivAttachment: ImageView = itemView.findViewById(R.id.ivAttachment)

        fun bind(message: ChatMessage) {
            val hasImage = !message.attachmentUrl.isNullOrEmpty() && message.attachmentType == AttachmentType.IMAGE
            if (hasImage) {
                ivAttachment.visibility = View.VISIBLE
                tvMessage.visibility = View.GONE
                Glide.with(itemView.context)
                    .load(message.attachmentUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(ivAttachment)
            } else {
                ivAttachment.visibility = View.GONE
                tvMessage.visibility = View.VISIBLE
                tvMessage.text = message.message
            }
            tvTime.text = formatTime(message.timestamp)
            tvStatus.text = if (message.isRead) "✓✓" else "✓"
            tvStatus.setTextColor(
                itemView.context.getColor(
                    if (message.isRead) R.color.blue_500 else R.color.gray_500
                )
            )
        }
    }

    /**
     * ViewHolder para mensagens recebidas
     */
    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSenderName: TextView = itemView.findViewById(R.id.tvSenderName)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val ivAttachment: ImageView = itemView.findViewById(R.id.ivAttachment)

        fun bind(message: ChatMessage) {
            tvSenderName.text = message.senderName
            val hasImage = !message.attachmentUrl.isNullOrEmpty() && message.attachmentType == AttachmentType.IMAGE
            if (hasImage) {
                ivAttachment.visibility = View.VISIBLE
                tvMessage.visibility = View.GONE
                Glide.with(itemView.context)
                    .load(message.attachmentUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(ivAttachment)
            } else {
                ivAttachment.visibility = View.GONE
                tvMessage.visibility = View.VISIBLE
                tvMessage.text = message.message
            }
            tvTime.text = formatTime(message.timestamp)
        }
    }
} 