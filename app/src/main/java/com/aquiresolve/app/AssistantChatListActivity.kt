package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.databinding.ActivityAssistantChatListBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AssistantChatListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssistantChatListBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val adapter = ChatHistoryAdapter { chat -> openChat(chat.id) }

    data class ChatPreview(
        val id: String,
        val title: String,
        val lastMessage: String,
        val updatedAt: Date?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!FirebaseConfig.isInitialized()) FirebaseConfig.initialize(this)
        binding = ActivityAssistantChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)

        binding.recyclerChats.layoutManager = LinearLayoutManager(this)
        binding.recyclerChats.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }
        binding.btnNewChat.setOnClickListener { openNewChat() }

        loadChats()
    }

    private fun loadChats() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("ai_chats")
            .whereEqualTo("userId", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val chats = snapshot.documents.map { doc ->
                    ChatPreview(
                        id = doc.id,
                        title = doc.getString("title") ?: "Chat sem título",
                        lastMessage = doc.getString("lastMessage") ?: "",
                        updatedAt = doc.getDate("updatedAt")
                    )
                }
                adapter.submitList(chats)
                binding.tvEmpty.visibility = if (chats.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun openChat(chatId: String) {
        startActivity(Intent(this, AssistantChatActivity::class.java)
            .putExtra("chat_id", chatId))
    }

    private fun openNewChat() {
        startActivity(Intent(this, AssistantChatActivity::class.java))
    }
}

class ChatHistoryAdapter(
    private val onClick: (AssistantChatListActivity.ChatPreview) -> Unit
) : RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder>() {

    private var chats = listOf<AssistantChatListActivity.ChatPreview>()

    fun submitList(list: List<AssistantChatListActivity.ChatPreview>) {
        chats = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount() = chats.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvChatTitle)
        private val tvLastMsg: TextView = view.findViewById(R.id.tvLastMessage)
        private val tvDate: TextView = view.findViewById(R.id.tvDate)

        fun bind(chat: AssistantChatListActivity.ChatPreview) {
            tvTitle.text = chat.title
            tvLastMsg.text = chat.lastMessage.ifEmpty { "Nova conversa" }
            tvDate.text = formatDate(chat.updatedAt)
            itemView.setOnClickListener { onClick(chat) }
        }

        private fun formatDate(date: Date?): String {
            if (date == null) return ""
            val now = System.currentTimeMillis()
            val diff = now - date.time
            return when {
                diff < 60_000 -> "Agora"
                diff < 3_600_000 -> "${diff / 60_000}min"
                diff < 86_400_000 -> "${diff / 3_600_000}h"
                diff < 172_800_000 -> "Ontem"
                else -> SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(date)
            }
        }
    }
}
