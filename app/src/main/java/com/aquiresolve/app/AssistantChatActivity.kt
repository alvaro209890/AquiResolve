package com.aquiresolve.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.databinding.ActivityAssistantChatBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class AssistantChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssistantChatBinding
    private val adapter = ChatAdapter(
        onNicheClick = { niche ->
            // Abre CreateOrderActivity com o nicho pré-selecionado
            val intent = Intent(this, CreateOrderActivity::class.java)
                .putExtra("service_category_name", niche)
            startActivity(intent)
            logEvent("ia_niche_click", Bundle().apply { putString("niche", niche) })
        }
    )
    private var isStreaming = false
    private var niches: List<String> = emptyList()
    private var voiceManager: VoiceInputManager? = null

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoice()
            else toast("Permita o microfone para falar com o Hello.")
        }

    companion object {
        const val EXTRA_PREFILL = "prefill_description"

        private val SUGGESTIONS = listOf(
            "\uD83E\uDEA0 Estou com um vazamento",
            "\uD83D\uDD0C Problema elétrico em casa",
            "\uD83E\uDDF9 Preciso de uma faxina",
            "❄️ Ar condicionado não funciona",
            "\uD83D\uDD27 Montagem de móveis",
            "\uD83D\uDDA5️ Formatação de computador"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!FirebaseConfig.isInitialized()) {
            FirebaseConfig.initialize(this)
        }
        binding = ActivityAssistantChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)
        logEvent("ia_chat_open", null)

        setupRecyclerView()
        setupSuggestions()
        setupVoice()
        setupListeners()

        lifecycleScope.launch {
            try { CatalogRepository.load() } catch (_: Exception) {}
            niches = CatalogRepository.cachedNicheNames()
        }

        val prefill = intent.getStringExtra(EXTRA_PREFILL)?.trim().orEmpty()
        if (prefill.isNotEmpty()) {
            binding.etInput.setText(prefill)
            binding.etInput.setSelection(prefill.length)
            sendMessage()
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerChat.adapter = adapter
        binding.recyclerChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
    }

    private fun setupSuggestions() {
        for (text in SUGGESTIONS) {
            val chip = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                this.text = text
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                cornerRadius = resources.getDimensionPixelSize(R.dimen.chip_corner_radius)
                setPadding(32, 0, 32, 0)
                minHeight = resources.getDimensionPixelSize(R.dimen.chip_min_height)
                setOnClickListener {
                    binding.suggestionsScroll.visibility = View.GONE
                    binding.etInput.setText(text)
                    sendMessage()
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.chip_spacing)
            }
            binding.suggestionsContainer.addView(chip, params)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.btnMic.setOnClickListener { onMicToggle() }
    }

    // ---- Voz: tap para ligar/desligar -----------------------------------------------

    private fun setupVoice() {
        voiceManager = VoiceInputManager(
            context = this,
            onReadyForSpeech = {
                runOnUiThread { setVoiceActive(true) }
            },
            onPartial = { text ->
                runOnUiThread {
                    binding.etInput.setText(text)
                    binding.etInput.setSelection(text.length)
                }
            },
            onResult = { text ->
                runOnUiThread {
                    binding.etInput.setText(text)
                    binding.etInput.setSelection(text.length)
                    setVoiceActive(false)
                    logEvent("ia_voz_reconhecida", Bundle().apply { putInt("len", text.length) })
                    sendMessage()
                }
            },
            onError = { msg ->
                runOnUiThread {
                    setVoiceActive(false)
                    if (msg.isNotBlank()) toast(msg)
                }
            },
            onEnd = {
                runOnUiThread { setVoiceActive(false) }
            }
        )

        if (voiceManager?.isAvailable() != true) {
            binding.btnMic.visibility = View.GONE
        }
    }

    private fun onMicToggle() {
        val vm = voiceManager ?: return
        if (vm.isListening) {
            vm.stop()
            return
        }
        if (isStreaming) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startVoice()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoice() {
        hideKeyboard()
        binding.etInput.setText("")
        logEvent("ia_voz_iniciada", null)
        voiceManager?.start()
    }

    private fun setVoiceActive(active: Boolean) {
        binding.voiceActiveBar.visibility = if (active) View.VISIBLE else View.GONE
        binding.btnMic.setBackgroundResource(
            if (active) R.drawable.bg_mic_button_active else R.drawable.bg_mic_button
        )
        val tintColor = if (active) R.color.white else R.color.gray_500
        binding.btnMic.setColorFilter(ContextCompat.getColor(this, tintColor))
        if (!active) {
            binding.etInput.hint = "Descreva o que precisa..."
        } else {
            binding.etInput.hint = "Fale agora..."
        }
    }

    // ---- Envio de mensagem ----------------------------------------------------------

    private fun sendMessage() {
        if (isStreaming) return
        val text = binding.etInput.text.toString().trim()
        if (text.length < 2) return

        binding.etInput.text?.clear()
        binding.suggestionsScroll.visibility = View.GONE
        hideKeyboard()

        adapter.addMessage(ChatAdapter.ChatMessage(role = "user", content = text))
        scrollToBottom()

        showTyping(true)
        isStreaming = true
        binding.btnSend.isEnabled = false
        binding.btnMic.isEnabled = false

        val assistantMsg = ChatAdapter.ChatMessage(role = "assistant", content = "", isStreaming = true)
        adapter.addMessage(assistantMsg)

        val history = adapter.messages
            .filter { !it.isStreaming && it.content.isNotEmpty() }
            .map { AssistantChatClient.Message(role = it.role, content = it.content) }

        lifecycleScope.launch {
            if (niches.isEmpty()) {
                try { CatalogRepository.load() } catch (_: Exception) {}
                niches = CatalogRepository.cachedNicheNames()
            }

            AssistantChatClient.chat(
                history,
                niches,
                object : AssistantChatClient.StreamCallback {
                    override fun onToken(token: String) {
                        runOnUiThread {
                            assistantMsg.content += token
                            adapter.updateLastMessage(assistantMsg)
                            scrollToBottom()
                        }
                    }

                    override fun onDone(fullText: String, suggestedNiche: String?) {
                        runOnUiThread {
                            assistantMsg.content = fullText.ifEmpty {
                                "Não consegui processar agora. Tente de novo ou use a busca."
                            }
                            assistantMsg.isStreaming = false
                            assistantMsg.suggestedNiche = suggestedNiche
                            adapter.updateLastMessage(assistantMsg)
                            showTyping(false)
                            isStreaming = false
                            binding.btnSend.isEnabled = true
                            binding.btnMic.isEnabled = true
                            scrollToBottom()
                        }
                    }

                    override fun onError(message: String) {
                        runOnUiThread {
                            if (assistantMsg.content.isEmpty()) {
                                assistantMsg.content = message
                            }
                            assistantMsg.isStreaming = false
                            adapter.updateLastMessage(assistantMsg)
                            showTyping(false)
                            isStreaming = false
                            binding.btnSend.isEnabled = true
                            binding.btnMic.isEnabled = true
                        }
                    }
                }
            )
        }
    }

    private fun showTyping(show: Boolean) {
        binding.typingLayout.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun scrollToBottom() {
        val count = adapter.itemCount
        if (count > 0) binding.recyclerChat.scrollToPosition(count - 1)
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
        } catch (_: Exception) {}
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun logEvent(name: String, params: Bundle?) {
        try { FirebaseConfig.getAnalytics()?.logEvent(name, params) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        voiceManager?.destroy()
        voiceManager = null
        super.onDestroy()
    }
}

// --- ChatAdapter ---

class ChatAdapter(
    private val onNicheClick: (niche: String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    data class ChatMessage(
        val id: Long = System.nanoTime(),
        val role: String,
        var content: String,
        var isStreaming: Boolean = false,
        var suggestedNiche: String? = null
    )

    val messages = mutableListOf<ChatMessage>()

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_ASSISTANT = 2
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(msg: ChatMessage) {
        if (messages.isNotEmpty()) {
            messages[messages.size - 1] = msg
            notifyItemChanged(messages.size - 1)
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (messages[position].role == "user") TYPE_USER else TYPE_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_USER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message_assistant, parent, false)
            AssistantViewHolder(view, onNicheClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is UserViewHolder -> holder.bind(msg)
            is AssistantViewHolder -> holder.bind(msg)
        }
    }

    override fun getItemCount() = messages.size

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tvMessage)
        fun bind(msg: ChatMessage) { tv.text = msg.content }
    }

    class AssistantViewHolder(
        view: View,
        private val onNicheClick: (niche: String) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tvMessage)
        private val btnSolicitar: MaterialButton = view.findViewById(R.id.btnSolicitar)

        fun bind(msg: ChatMessage) {
            tv.text = if (msg.isStreaming && msg.content.isEmpty()) "..." else msg.content

            val niche = msg.suggestedNiche
            if (!niche.isNullOrBlank() && !msg.isStreaming) {
                btnSolicitar.visibility = View.VISIBLE
                btnSolicitar.text = "Solicitar $niche"
                btnSolicitar.setOnClickListener { onNicheClick(niche) }
            } else {
                btnSolicitar.visibility = View.GONE
                btnSolicitar.setOnClickListener(null)
            }
        }
    }
}
