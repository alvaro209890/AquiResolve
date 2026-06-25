package com.aquiresolve.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
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

/**
 * Chat multi-turno do Hello AquiResolve (v2 do plano 06).
 *
 * Streaming SSE token-por-token via [AssistantChatClient] + chips de sugestão
 * na abertura. Substitui o fluxo single-turn antigo de [AssistantActivity].
 *
 * Features:
 * - Conversa completa com histórico (a IA lembra do contexto)
 * - Texto aparece token por token (streaming via Groq)
 * - Chips clicáveis com problemas comuns (vazamento, elétrica, faxina...)
 * - UI de chat bubbles (usuário à direita, assistente à esquerda)
 * - Fallback amigável em caso de erro (nunca bloqueia a contratação)
 */
class AssistantChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssistantChatBinding
    private val adapter = ChatAdapter()
    private var isStreaming = false
    private var niches: List<String> = emptyList()
    private var voiceManager: VoiceInputManager? = null
    private var pendingVoiceStart = false

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingVoiceStart) {
                startVoiceInput()
            } else if (!granted) {
                toast("Permita o microfone para falar com o Hello.")
            }
            pendingVoiceStart = false
        }

    companion object {
        /** Extra opcional: pré-preenche a descrição (ex.: vindo da busca sem resultado). */
        const val EXTRA_PREFILL = "prefill_description"

        private val SUGGESTIONS = listOf(
            "🪠 Estou com um vazamento",
            "🔌 Problema elétrico em casa",
            "🧹 Preciso de uma faxina",
            "❄️ Ar condicionado não funciona",
            "🔧 Montagem de móveis",
            "🖥️ Formatação de computador"
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

        // Carrega catálogo de nichos (enviado no prompt para a IA classificar)
        lifecycleScope.launch {
            try {
                CatalogRepository.load()
            } catch (_: Exception) {
            }
            niches = CatalogRepository.cachedNicheNames()
        }

        // Pré-preenche descrição se veio da busca sem resultado
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
        val container = binding.suggestionsContainer
        for (text in SUGGESTIONS) {
            val chip = MaterialButton(this).apply {
                this.text = text
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setBackgroundColor(ContextCompat.getColor(context, R.color.surface_color))
                strokeWidth = 1
                strokeColor = ContextCompat.getColorStateList(context, R.color.gray_300)
                cornerRadius = resources.getDimensionPixelSize(R.dimen.chip_corner_radius)
                isClickable = true
                isFocusable = true
                setPadding(24, 8, 24, 8)
                setOnClickListener {
                    binding.etInput.setText(text)
                    binding.etInput.setSelection(text.length)
                    sendMessage()
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.chip_spacing)
            }
            container.addView(chip, params)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.fabVoice.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isStreaming) requestVoiceInput()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    voiceManager?.stop()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    voiceManager?.cancel()
                    setVoiceListening(false)
                    true
                }
                else -> true
            }
        }
    }

    private fun sendMessage() {
        if (isStreaming) return
        val text = binding.etInput.text.toString().trim()
        if (text.length < 2) return

        binding.etInput.text?.clear()
        hideKeyboard()

        // Adiciona mensagem do usuário
        adapter.addMessage(ChatAdapter.ChatMessage(role = "user", content = text))

        // Mostra indicador de digitando
        showTyping(true)
        isStreaming = true
        binding.btnSend.isEnabled = false

        // Cria placeholder da resposta do assistente que será atualizado via streaming
        val assistantMsg = ChatAdapter.ChatMessage(
            role = "assistant",
            content = "",
            isStreaming = true
        )
        adapter.addMessage(assistantMsg)

        // Constrói histórico (mensagens não-streaming com conteúdo)
        val history = adapter.messages
            .filter { !it.isStreaming && it.content.isNotEmpty() }
            .map { AssistantChatClient.Message(role = it.role, content = it.content) }

        lifecycleScope.launch {
            // Garante que os nichos estão carregados
            if (niches.isEmpty()) {
                try {
                    CatalogRepository.load()
                } catch (_: Exception) {
                }
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
                        }
                    }

                    override fun onDone(fullText: String) {
                        runOnUiThread {
                            assistantMsg.content = fullText.ifEmpty { "Não consegui processar sua pergunta agora. Tente de novo ou use a busca." }
                            assistantMsg.isStreaming = false
                            adapter.updateLastMessage(assistantMsg)
                            showTyping(false)
                            isStreaming = false
                            binding.btnSend.isEnabled = true
                        }
                    }

                    override fun onError(message: String) {
                        runOnUiThread {
                            if (assistantMsg.content.isEmpty()) {
                                assistantMsg.content = message
                                assistantMsg.isStreaming = false
                                adapter.updateLastMessage(assistantMsg)
                            }
                            showTyping(false)
                            isStreaming = false
                            binding.btnSend.isEnabled = true
                        }
                    }
                }
            )
        }
    }

    private fun showTyping(show: Boolean) {
        binding.tvTypingIndicator.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvTypingIndicator.text = if (show) "Digitando..." else ""
    }

    private fun setupVoice() {
        voiceManager = VoiceInputManager(
            context = this,
            onReadyForSpeech = { setVoiceListening(true) },
            onPartial = { text -> fillInputWithVoiceText(text) },
            onResult = { text -> fillInputWithVoiceText(text) },
            onError = { message ->
                if (message.isNotBlank()) toast(message)
            },
            onEnd = { setVoiceListening(false) }
        )

        if (voiceManager?.isAvailable() != true) {
            binding.fabVoice.visibility = View.GONE
            binding.tvVoiceStatus.visibility = View.GONE
        }
    }

    private fun requestVoiceInput() {
        val vm = voiceManager ?: return
        if (!vm.isAvailable()) {
            toast("Reconhecimento de voz indisponível neste aparelho.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceInput()
        } else {
            pendingVoiceStart = true
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceInput() {
        hideKeyboard()
        binding.etInput.hint = "Fale agora..."
        voiceManager?.start()
    }

    private fun fillInputWithVoiceText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        binding.etInput.setText(clean)
        binding.etInput.setSelection(clean.length)
    }

    private fun setVoiceListening(listening: Boolean) {
        binding.tvVoiceStatus.visibility = if (listening) View.VISIBLE else View.GONE
        binding.fabVoice.alpha = if (listening) 0.72f else 1f
        binding.etInput.hint = if (listening) "Fale agora..." else "Digite sua mensagem..."
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
        } catch (_: Exception) {
        }
    }

    private fun logEvent(name: String, params: Bundle?) {
        try {
            FirebaseConfig.getAnalytics()?.logEvent(name, params)
        } catch (_: Exception) {
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        voiceManager?.destroy()
        voiceManager = null
        super.onDestroy()
    }
}

// --- ChatAdapter ---

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    data class ChatMessage(
        val id: Long = System.nanoTime(),
        val role: String,        // "user" | "assistant"
        var content: String,
        var isStreaming: Boolean = false
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
            AssistantViewHolder(view)
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
        fun bind(msg: ChatMessage) {
            tv.text = msg.content
        }
    }

    class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tvMessage)
        fun bind(msg: ChatMessage) {
            tv.text = msg.content
        }
    }
}
