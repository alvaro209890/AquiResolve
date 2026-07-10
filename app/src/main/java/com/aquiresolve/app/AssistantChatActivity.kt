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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.databinding.ActivityAssistantChatBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AssistantChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssistantChatBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val adapter = ChatAdapter(
        onNicheClick = { niche ->
            val intent = Intent(this, CreateOrderActivity::class.java)
                .putExtra("service_category_name", niche)
            startActivity(intent)
            logEvent("ia_niche_click", Bundle().apply { putString("niche", niche) })
        }
    )
    private var isStreaming = false
    private var niches: List<String> = emptyList()
    private var voiceManager: VoiceInputManager? = null
    private var chatId: String? = null
    private var chatTitle: String = ""

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoice()
            else toast("Permita o microfone para falar com o Helô.")
        }

    // ---- Análise de imagem (Helô vê a foto e diz o serviço) ----
    private var cameraImageUri: android.net.Uri? = null

    // Android Photo Picker — não exige permissão de mídia (política do Google Play)
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) processSelectedImage(uri)
        }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) cameraImageUri?.let { processSelectedImage(it) }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
            else toast("Permita a câmera para enviar uma foto.")
        }

    companion object {
        const val EXTRA_PREFILL = "prefill_description"
        const val EXTRA_CHAT_ID = "chat_id"

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
        if (!FirebaseConfig.isInitialized()) FirebaseConfig.initialize(this)
        binding = ActivityAssistantChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)

        // Chat novo sempre (a menos que venha de histórico)
        chatId = intent.getStringExtra(EXTRA_CHAT_ID)
        if (chatId != null) {
            logEvent("ia_chat_open_existing", null)
            loadChatMessages()
        } else {
            chatId = db.collection("ai_chats").document().id
            logEvent("ia_chat_open", null)
        }

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

        // Se veio do botão flutuante de microfone, inicia voz automaticamente
        if (intent.getBooleanExtra("start_with_voice", false)) {
            binding.root.postDelayed({ startVoiceIfPermitted() }, 400)
        }
    }

    private fun startVoiceIfPermitted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startVoice()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun loadChatMessages() {
        val cid = chatId ?: return
        db.collection("ai_chats").document(cid)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val role = doc.getString("role") ?: continue
                    val content = doc.getString("content") ?: continue
                    val niche = doc.getString("niche")
                    adapter.addMessage(ChatAdapter.ChatMessage(
                        role = role,
                        content = content,
                        suggestedNiche = niche
                    ))
                }
                scrollToBottom()
            }
    }

    private fun createChatIfNeeded(title: String) {
        val uid = auth.currentUser?.uid ?: return
        val data = hashMapOf<String, Any>(
            "userId" to uid,
            "title" to title,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastMessage" to title
        )
        db.collection("ai_chats").document(chatId!!)
            .set(data, SetOptions.merge())
    }

    private fun saveMessage(role: String, content: String, niche: String?) {
        val cid = chatId ?: return
        val uid = auth.currentUser?.uid ?: return
        val msg = hashMapOf<String, Any>(
            "role" to role,
            "content" to content,
            "timestamp" to FieldValue.serverTimestamp()
        )
        if (niche != null) msg["niche"] = niche

        db.collection("ai_chats").document(cid)
            .collection("messages").add(msg)

        val updates = hashMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastMessage" to content.take(120)
        )
        if (chatTitle.isEmpty() && role == "user") {
            chatTitle = content.take(50)
            updates["title"] = chatTitle
        }
        if (niche != null) updates["niche"] = niche

        db.collection("ai_chats").document(cid)
            .set(updates, SetOptions.merge())

        // Garante que o doc raiz tenha userId (cria se não existir)
        db.collection("ai_chats").document(cid).get().addOnSuccessListener { doc ->
            if (!doc.exists() || doc.getString("userId") == null) {
                db.collection("ai_chats").document(cid).set(
                    mapOf("userId" to uid, "createdAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge()
                )
            }
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
        binding.btnAttachImage.setOnClickListener { showImageSourceChooser() }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, AssistantChatListActivity::class.java))
        }
    }

    // ---- Imagem: escolha da fonte, captura e análise ----------------

    private fun showImageSourceChooser() {
        if (isStreaming) return
        val options = arrayOf("📷 Tirar foto", "🖼️ Escolher da galeria")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enviar foto para a Helô analisar")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startCameraWithPermission()
                    1 -> pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            }
            .show()
    }

    private fun startCameraWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            val file = java.io.File(cacheDir, "helo_capture_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            cameraImageUri = uri
            takePhotoLauncher.launch(uri)
        } catch (e: Exception) {
            toast("Não foi possível abrir a câmera.")
        }
    }

    /** Decodifica a imagem da Uri, reduz (<=1024px), comprime JPEG e envia para a Helô. */
    private fun processSelectedImage(uri: android.net.Uri) {
        if (isStreaming) return
        lifecycleScope.launch {
            val base64 = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    encodeDownscaledJpeg(uri)
                }
            } catch (e: Exception) {
                null
            }
            if (base64.isNullOrBlank()) {
                toast("Não consegui ler essa imagem. Tente outra.")
                return@launch
            }
            analyzeImage(base64)
        }
    }

    private fun encodeDownscaledJpeg(uri: android.net.Uri): String {
        contentResolver.openInputStream(uri).use { input ->
            val original = android.graphics.BitmapFactory.decodeStream(input)
                ?: throw IllegalStateException("decode falhou")
            val maxSide = 1024
            val w = original.width
            val h = original.height
            val scale = if (w >= h) maxSide.toFloat() / w else maxSide.toFloat() / h
            val bmp = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    original, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true
                )
            } else original
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
            return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        }
    }

    private fun analyzeImage(imageBase64: String) {
        // Bolha do usuário indicando a foto enviada
        saveMessage("user", "📷 Foto enviada para análise", null)
        adapter.addMessage(ChatAdapter.ChatMessage(role = "user", content = "📷 Foto enviada para análise"))
        binding.suggestionsScroll.visibility = View.GONE
        scrollToBottom()

        showTyping(true)
        isStreaming = true
        binding.btnSend.isEnabled = false
        binding.btnMic.isEnabled = false
        binding.btnAttachImage.isEnabled = false
        logEvent("ia_imagem_enviada", null)

        val assistantMsg = ChatAdapter.ChatMessage(role = "assistant", content = "", isStreaming = true)
        adapter.addMessage(assistantMsg)
        scrollToBottom()

        lifecycleScope.launch {
            if (niches.isEmpty()) {
                try { CatalogRepository.load() } catch (_: Exception) {}
                niches = CatalogRepository.cachedNicheNames()
            }
            AssistantVisionClient.analyze(
                imageBase64 = imageBase64,
                text = null,
                niches = niches,
                callback = object : AssistantVisionClient.Callback {
                    override fun onResult(result: AssistantVisionClient.Result) {
                        runOnUiThread {
                            assistantMsg.content = result.text
                            assistantMsg.isStreaming = false
                            assistantMsg.suggestedNiche = result.niche
                            adapter.updateLastMessage(assistantMsg)
                            saveMessage("assistant", result.text, result.niche)
                            if (result.niche != null) {
                                logEvent("ia_imagem_niche", Bundle().apply { putString("niche", result.niche) })
                            }
                            finishImageAnalysis()
                        }
                    }
                    override fun onError(message: String) {
                        runOnUiThread {
                            assistantMsg.content = message
                            assistantMsg.isStreaming = false
                            adapter.updateLastMessage(assistantMsg)
                            saveMessage("assistant", message, null)
                            finishImageAnalysis()
                        }
                    }
                }
            )
        }
    }

    private fun finishImageAnalysis() {
        showTyping(false)
        isStreaming = false
        binding.btnSend.isEnabled = true
        binding.btnMic.isEnabled = true
        binding.btnAttachImage.isEnabled = true
        scrollToBottom()
    }

    // ---- Voz ----------------------------------------------------

    private fun setupVoice() {
        voiceManager = VoiceInputManager(
            context = this,
            onReadyForSpeech = { runOnUiThread { setVoiceActive(true) } },
            onPartial = { text -> runOnUiThread {
                binding.etInput.setText(text)
                binding.etInput.setSelection(text.length)
            }},
            onResult = { text -> runOnUiThread {
                binding.etInput.setText(text)
                binding.etInput.setSelection(text.length)
                setVoiceActive(false)
                logEvent("ia_voz_reconhecida", Bundle().apply { putInt("len", text.length) })
                sendMessage()
            }},
            onError = { msg -> runOnUiThread {
                setVoiceActive(false)
                if (msg.isNotBlank()) toast(msg)
            }},
            onEnd = { runOnUiThread { setVoiceActive(false) } }
        )
        if (voiceManager?.isAvailable() != true) binding.btnMic.visibility = View.GONE
    }

    private fun onMicToggle() {
        val vm = voiceManager ?: return
        if (vm.isListening) { vm.stop(); return }
        if (isStreaming) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            startVoice()
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoice() {
        hideKeyboard()
        binding.etInput.setText("")
        logEvent("ia_voz_iniciada", null)
        voiceManager?.start()
    }

    private fun setVoiceActive(active: Boolean) {
        binding.voiceActiveBar.visibility = if (active) View.VISIBLE else View.GONE
        binding.btnMic.setBackgroundResource(if (active) R.drawable.bg_mic_button_active else R.drawable.bg_mic_button)
        val tintColor = if (active) R.color.white else R.color.gray_500
        binding.btnMic.setColorFilter(ContextCompat.getColor(this, tintColor))
        binding.etInput.hint = if (active) "Fale agora..." else "Descreva o que precisa..."
    }

    // ---- Envio de mensagem ---------------------------------------

    private fun sendMessage() {
        if (isStreaming) return
        val text = binding.etInput.text.toString().trim()
        if (text.length < 2) return

        binding.etInput.text?.clear()
        binding.suggestionsScroll.visibility = View.GONE
        hideKeyboard()

        // Salva mensagem do usuário no Firestore
        saveMessage("user", text, null)
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

            AssistantChatClient.chat(history, niches,
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
                            val finalText = fullText.ifEmpty { "Não consegui processar agora. Tente de novo ou use a busca." }
                            assistantMsg.content = finalText
                            assistantMsg.isStreaming = false
                            assistantMsg.suggestedNiche = suggestedNiche
                            adapter.updateLastMessage(assistantMsg)
                            // Salva resposta da IA no Firestore
                            saveMessage("assistant", finalText, suggestedNiche)
                            showTyping(false)
                            isStreaming = false
                            binding.btnSend.isEnabled = true
                            binding.btnMic.isEnabled = true
                            scrollToBottom()
                        }
                    }
                    override fun onError(message: String) {
                        runOnUiThread {
                            if (assistantMsg.content.isEmpty()) assistantMsg.content = message
                            assistantMsg.isStreaming = false
                            adapter.updateLastMessage(assistantMsg)
                            saveMessage("assistant", message, null)
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
