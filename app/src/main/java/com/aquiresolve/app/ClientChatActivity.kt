package com.aquiresolve.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aquiresolve.app.adapters.ChatAdapter
import com.aquiresolve.app.databinding.ActivityClientChatBinding
import com.aquiresolve.app.models.ChatMessage
import com.aquiresolve.app.models.MessageType
import com.aquiresolve.app.models.AttachmentType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * ClientChatActivity - Tela de chat específica para CLIENTES
 * 
 * Interface diferenciada para clientes:
 * - Design mais elegante e focado no cliente
 * - Botões de ação específicos para clientes
 * - Informações do prestador em destaque
 */
class ClientChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClientChatBinding
    
    // Dados do chat
    private var orderId: String? = null
    private var providerId: String? = null
    private var providerName: String? = null
    private var providerPhoto: String? = null
    private var orderTitle: String? = null
    
    // Lista de mensagens
    private var messages = mutableListOf<ChatMessage>()
    private var isProviderOnline = false
    private var isTyping = false
    
    // Adapter
    private lateinit var chatAdapter: ChatAdapter
    private val chatManager = FirebaseChatManager()
    private val db by lazy { com.google.firebase.firestore.FirebaseFirestore.getInstance() }
    
    // Constantes para anexos
    companion object {
        private const val REQUEST_CAMERA = 1001
        private const val REQUEST_GALLERY = 1002
        private const val REQUEST_CAMERA_PERMISSION = 1003
    }
    
    // Launcher para seleção/upload de imagem no chat
    private val chatImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUrl = result.data?.getStringExtra(ImagePickerActivity.EXTRA_IMAGE_URL)
            if (!imageUrl.isNullOrEmpty()) {
                sendChatImageMessage(imageUrl)
            } else {
                showErrorMessage("Erro ao obter a imagem enviada")
            }
        }
    }
    private val chatDocumentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadAndSendDocument(uri)
        }
    }
    private val auth by lazy { com.google.firebase.auth.FirebaseAuth.getInstance() }
    private var cameraPhotoUri: android.net.Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityClientChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Obter dados da intent
        orderId = intent.getStringExtra("order_id")
        providerId = intent.getStringExtra("provider_id")
        providerName = intent.getStringExtra("provider_name")
        providerPhoto = intent.getStringExtra("provider_photo")
        orderTitle = intent.getStringExtra("order_title")
        
        if (orderId == null || providerId == null) {
            showErrorMessage("Dados do chat não encontrados")
            finish()
            return
        }
        
        // Verificar acesso ao chat (5 minutos após aceitação)
        checkChatAccess()
        
        setupUI()
        setupClickListeners()
        setupRecyclerView()
        setupMessageInput()
        loadChatData()

        // Presença: observar prestador e atualizar minha presença
        providerId?.let { observeUserPresence(it) }
    }

    /**
     * Configura a interface específica para clientes
     */
    private fun setupUI() {
        // Status bar personalizada para clientes
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)
        
        // Configurar informações do prestador
        binding.tvProviderName.text = providerName ?: "Prestador"
        binding.tvOrderTitle.text = orderTitle ?: "Serviço"
        
        // Status online/offline
        updateProviderStatus(false)
        
        // Carregar foto do prestador
        binding.ivProviderPhoto.visibility = View.VISIBLE
        // Remover tint para que a foto carregue com cores corretas (evita aparência cinza)
        binding.ivProviderPhoto.imageTintList = null
        if (!providerPhoto.isNullOrEmpty()) {
            com.bumptech.glide.Glide.with(this)
                .load(providerPhoto)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(binding.ivProviderPhoto)
        } else {
            binding.ivProviderPhoto.setImageResource(R.drawable.ic_person)
        }
        
        // Mostrar informações específicas do cliente
        binding.tvClientInfo.text = "Você está conversando com o prestador"
    }

    /**
     * Configura os listeners específicos para clientes
     */
    private fun setupClickListeners() {
        // Botão voltar
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // Botão de informações do prestador
        binding.btnProviderInfo.setOnClickListener {
            showProviderInfoDialog()
        }
        
    }

    /**
     * Configura o RecyclerView para as mensagens
     */
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages)
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(this@ClientChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    /**
     * Configura o input de mensagem
     */
    private fun setupMessageInput() {
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnSend.isEnabled = !s.isNullOrBlank()
            }
        })
        
        binding.btnSend.setOnClickListener {
            sendMessage()
        }
        
        binding.btnAttach.setOnClickListener {
            showAttachmentOptions()
        }
    }

    /**
     * Carrega os dados do chat
     */
    /**
     * Verifica se o chat pode ser acessado
     */
    private fun checkChatAccess() {
        orderId?.let { oId ->
            lifecycleScope.launch {
                try {
                    val orderDoc = db.collection("orders").document(oId).get().await()
                    val order = orderDoc.toObject(com.aquiresolve.app.models.OrderData::class.java)
                        ?.copy(id = orderDoc.id)
                    
                    if (order != null) {
                        val (canAccess, message) = com.aquiresolve.app.utils.ChatAccessHelper.canAccessChat(order)
                        if (!canAccess) {
                            AlertDialog.Builder(this@ClientChatActivity)
                                .setTitle("Chat Indisponível")
                                .setMessage(message ?: "O chat ainda não está disponível.")
                                .setPositiveButton("OK") { _, _ ->
                                    finish()
                                }
                                .setCancelable(false)
                                .show()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ClientChatActivity", "Erro ao verificar acesso ao chat: ${e.message}")
                }
            }
        }
    }
    
    private fun loadChatData() {
        loadMessagesFromFirestore()
    }


    /**
     * Envia uma mensagem
     */
    private fun sendMessage() {
        val messageText = binding.etMessage.text.toString().trim()
        if (messageText.isEmpty()) return

        val oId = orderId
        if (oId.isNullOrBlank()) {
            showErrorMessage("Pedido inválido para envio de mensagem")
            return
        }
        val currentUserId = getCurrentUserId()
        if (currentUserId.isNullOrBlank()) {
            showErrorMessage("Faça login novamente para enviar mensagens")
            return
        }
        val currentUserName = getCurrentUserName()
        
        // Limpar input imediatamente; UI será atualizada via listener
        binding.etMessage.text?.clear()

        lifecycleScope.launch {
            val result = chatManager.sendMessage(
                FirebaseChatManager.ChatMessage(
                    orderId = oId,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    senderType = "client",
                    message = messageText
                )
            )
            if (result.isFailure) {
                showErrorMessage("Falha ao enviar mensagem: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Atualiza o status do prestador
     */
    private fun updateProviderStatus(online: Boolean) {
        isProviderOnline = online
        binding.tvProviderStatus.text = if (online) "🟢 Online" else "🔴 Offline"
        binding.tvProviderStatus.setTextColor(
            ContextCompat.getColor(this, if (online) R.color.success_color else R.color.error_color)
        )
    }

    override fun onResume() {
        super.onResume()
        setupPresence()
    }

    override fun onPause() {
        super.onPause()
        setOffline()
    }

    private val rtdb by lazy { com.google.firebase.database.FirebaseDatabase.getInstance() }
    private var presenceListener: com.google.firebase.database.ValueEventListener? = null
    private var connectedListener: com.google.firebase.database.ValueEventListener? = null

    private fun setupPresence() {
        val current = auth.currentUser ?: return
        val myRef = rtdb.getReference("presence").child(current.uid)

        // Detectar conexão e configurar onDisconnect
        val connectedRef = rtdb.getReference(".info/connected")
        connectedListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    val data = mapOf<String, Any>(
                        "online" to true,
                        "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP
                    )
                    myRef.setValue(data)
                    myRef.onDisconnect().setValue(mapOf<String, Any>(
                        "online" to false,
                        "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP
                    ))
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        connectedRef.addValueEventListener(connectedListener ?: return)
    }

    private fun setOffline() {
        val current = auth.currentUser ?: return
        val myRef = rtdb.getReference("presence").child(current.uid)
        myRef.setValue(mapOf<String, Any>(
            "online" to false,
            "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP
        ))
    }

    private fun observeUserPresence(userId: String) {
        val userRef = rtdb.getReference("presence").child(userId)
        presenceListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                val lastSeenTs = snapshot.child("lastSeen").getValue(Long::class.java)
                runOnUiThread {
                    isProviderOnline = online
                    if (online) {
                        binding.tvProviderStatus.text = "Online"
                        binding.tvProviderStatus.setTextColor(ContextCompat.getColor(this@ClientChatActivity, R.color.success_color))
                    } else {
                        val text = if (lastSeenTs != null) {
                            "Visto às ${formatBrt(java.util.Date(lastSeenTs))}"
                        } else "Offline"
                        binding.tvProviderStatus.text = text
                        binding.tvProviderStatus.setTextColor(ContextCompat.getColor(this@ClientChatActivity, R.color.gray_500))
                    }
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        userRef.addValueEventListener(presenceListener ?: return)
    }

    private fun formatBrt(date: java.util.Date): String {
        val fmt = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR"))
        fmt.timeZone = java.util.TimeZone.getTimeZone("America/Sao_Paulo")
        return fmt.format(date)
    }

    override fun onDestroy() {
        super.onDestroy()
        presenceListener?.let { listener ->
            providerId?.let { rtdb.getReference("presence").child(it).removeEventListener(listener) }
        }
        connectedListener?.let { listener ->
            rtdb.getReference(".info/connected").removeEventListener(listener)
        }
    }

    /**
     * Mostra diálogo de informações do prestador
     */
    private fun showProviderInfoDialog() {
        val statusText = if (isProviderOnline) "Online" else "Offline"
        AlertDialog.Builder(this)
            .setTitle("Informações do Prestador")
            .setMessage("""
                Nome: ${providerName ?: "Não informado"}
                Status: $statusText
                Serviço em andamento: ${orderTitle ?: "Não informado"}
                Pedido: ${orderId ?: "Não informado"}
            """.trimIndent())
            .setPositiveButton("OK", null)
            .setNegativeButton("Fechar", null)
            .show()
    }


    /**
     * Mostra opções de anexo
     */
    private fun showAttachmentOptions() {
        val options = arrayOf("📷 Tirar Foto", "🖼️ Galeria", "Arquivo")

        AlertDialog.Builder(this)
            .setTitle("Anexar Arquivo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhotoForChat()
                    1 -> openChatImagePicker()
                    2 -> openDocumentPicker()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun takePhotoForChat() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            return
        }
        try {
            val photoFile = java.io.File(cacheDir, "chat_camera_${System.currentTimeMillis()}.jpg")
            cameraPhotoUri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", photoFile)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            startActivityForResult(intent, REQUEST_CAMERA)
        } catch (e: Exception) {
            showErrorMessage("Erro ao abrir câmera: ${e.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAMERA && resultCode == RESULT_OK) {
            val uri = cameraPhotoUri ?: return
            cameraPhotoUri = null
            uploadAndSendImage(uri)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION &&
            grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            takePhotoForChat()
        }
    }

    private fun uploadAndSendImage(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val currentUserId = getCurrentUserId()
                if (currentUserId.isNullOrBlank()) {
                    showErrorMessage("Faça login novamente para enviar imagens")
                    return@launch
                }

                val imageManager = FirebaseImageManager()
                val uploadData = FirebaseImageManager.ImageUploadData(
                    uri = uri,
                    fileName = "chat_${System.currentTimeMillis()}.jpg",
                    folder = FirebaseImageManager.FOLDER_CHAT_IMAGES,
                    userId = currentUserId
                )
                val result = imageManager.uploadImage(this@ClientChatActivity, uploadData)
                when (result) {
                    is FirebaseImageManager.UploadResult.Success -> {
                        sendChatImageMessage(result.downloadUrl)
                    }
                    is FirebaseImageManager.UploadResult.Error -> {
                        showErrorMessage("Erro ao enviar imagem: ${result.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                showErrorMessage("Erro ao enviar imagem: ${e.message}")
            }
        }
    }

    private fun openDocumentPicker() {
        chatDocumentPickerLauncher.launch("*/*")
    }

    private fun uploadAndSendDocument(uri: Uri) {
        val oId = orderId
        if (oId.isNullOrBlank()) {
            showErrorMessage("Pedido inválido para envio de arquivo")
            return
        }

        lifecycleScope.launch {
            try {
                val currentUserId = getCurrentUserId()
                if (currentUserId.isNullOrBlank()) {
                    showErrorMessage("Faça login novamente para enviar arquivos")
                    return@launch
                }

                val storageManager = FirebaseStorageManager()
                val result = storageManager.uploadDocument(
                    this@ClientChatActivity,
                    uri,
                    "chats/$oId/files"
                )
                result.fold(
                    onSuccess = { url ->
                        sendChatDocumentMessage(
                            documentUrl = url,
                            fileName = queryDisplayName(uri) ?: "arquivo",
                            fileSize = queryFileSize(uri),
                            fileType = contentResolver.getType(uri)
                        )
                    },
                    onFailure = { error ->
                        showErrorMessage("Erro ao enviar arquivo: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                showErrorMessage("Erro ao enviar arquivo: ${e.message}")
            }
        }
    }

    /**
     * Rola para o final da lista
     */
    private fun scrollToBottom() {
        binding.recyclerViewMessages.post {
            val lastIndex = (messages.size - 1).coerceAtLeast(0)
            if (messages.isNotEmpty()) {
                binding.recyclerViewMessages.smoothScrollToPosition(lastIndex)
            }
        }
    }

    /**
     * Mostra mensagem de erro
     */
    private fun showErrorMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun getCurrentUserId(): String? = FirebaseAuth.getInstance().currentUser?.uid
    private fun getCurrentUserName(): String {
        val user = FirebaseAuth.getInstance().currentUser
        return user?.displayName
            ?: user?.email?.substringBefore("@")
            ?: "Usuário"
    }

    private fun loadMessagesFromFirestore() {
        val oId = orderId ?: return
        lifecycleScope.launch {
            chatManager.getMessagesFlow(oId, "client").collectLatest { remoteMessages ->
                val currentUserId = getCurrentUserId()
                if (currentUserId.isNullOrBlank()) {
                    showErrorMessage("Sessão expirada. Entre novamente para acessar o chat.")
                    finish()
                    return@collectLatest
                }
                val hasUnreadFromOther = remoteMessages.any { !it.isRead && it.senderId != currentUserId }
                messages.clear()
                messages.addAll(remoteMessages.map { rm ->
                    ChatMessage(
                        id = rm.id,
                        orderId = rm.orderId,
                        senderId = rm.senderId,
                        senderName = rm.senderName,
                        message = rm.message,
                        timestamp = rm.timestamp.toDate(),
                        type = if (rm.senderId == currentUserId) MessageType.SENT else MessageType.RECEIVED,
                        isRead = rm.isRead,
                        attachmentUrl = rm.imageUrl ?: rm.documentUrl,
                        attachmentType = when {
                            !rm.imageUrl.isNullOrEmpty() -> AttachmentType.IMAGE
                            !rm.documentUrl.isNullOrEmpty() -> AttachmentType.DOCUMENT
                            else -> null
                        }
                    )
                })
                chatAdapter.notifyDataSetChanged()
                scrollToBottom()
                // Marcar como lido apenas se há mensagens não lidas do outro usuário
                if (hasUnreadFromOther) {
                    chatManager.markMessagesAsRead(oId, currentUserId)
                }
            }
        }
    }
    private fun openChatImagePicker() {
        val currentUserId = getCurrentUserId()
        if (currentUserId.isNullOrBlank()) {
            showErrorMessage("Faça login novamente para enviar imagens")
            return
        }
        val intent = ImagePickerActivity.createIntent(
            context = this,
            folder = FirebaseImageManager.FOLDER_CHAT_IMAGES,
            userId = currentUserId,
            orderId = orderId,
            maxImages = 1
        )
        chatImagePickerLauncher.launch(intent)
    }

    private fun sendChatImageMessage(imageUrl: String) {
        val oId = orderId
        if (oId.isNullOrBlank()) {
            showErrorMessage("Pedido inválido para envio de imagem")
            return
        }
        val currentUserId = getCurrentUserId()
        if (currentUserId.isNullOrBlank()) {
            showErrorMessage("Faça login novamente para enviar imagens")
            return
        }
        val currentUserName = getCurrentUserName()

        val message = ChatMessage(
            id = "img_${System.currentTimeMillis()}",
            orderId = oId,
            senderId = currentUserId,
            senderName = currentUserName,
            message = "📷 Imagem enviada",
            timestamp = Date(),
            type = MessageType.SENT,
            isRead = false,
            attachmentUrl = imageUrl,
            attachmentType = AttachmentType.IMAGE
        )

        lifecycleScope.launch {
            val result = chatManager.sendMessage(
                FirebaseChatManager.ChatMessage(
                    orderId = message.orderId,
                    senderId = message.senderId,
                    senderName = message.senderName,
                    senderType = "client",
                    message = message.message,
                    imageUrl = message.attachmentUrl,
                    messageType = "image"
                )
            )
            if (result.isFailure) {
                showErrorMessage("Erro ao enviar imagem: ${result.exceptionOrNull()?.message}")
            }
            // Não adicionar localmente - o listener do Firestore já atualiza a lista automaticamente
        }
    }

    private fun sendChatDocumentMessage(
        documentUrl: String,
        fileName: String,
        fileSize: Long?,
        fileType: String?
    ) {
        val oId = orderId
        if (oId.isNullOrBlank()) {
            showErrorMessage("Pedido inválido para envio de arquivo")
            return
        }
        val currentUserId = getCurrentUserId()
        if (currentUserId.isNullOrBlank()) {
            showErrorMessage("Faça login novamente para enviar arquivos")
            return
        }

        lifecycleScope.launch {
            val result = chatManager.sendMessage(
                FirebaseChatManager.ChatMessage(
                    orderId = oId,
                    senderId = currentUserId,
                    senderName = getCurrentUserName(),
                    senderType = "client",
                    message = "Arquivo enviado: $fileName",
                    documentUrl = documentUrl,
                    messageType = "file",
                    fileName = fileName,
                    fileSize = fileSize,
                    fileType = fileType
                )
            )
            if (result.isFailure) {
                showErrorMessage("Erro ao enviar arquivo: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
    }

    private fun queryFileSize(uri: Uri): Long? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0) cursor.getLong(index) else null
            }
    }
}
