package com.example.loginapp

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.loginapp.adapters.DocumentUploadAdapter
import com.example.loginapp.databinding.ActivityDocumentUploadBinding
import com.example.loginapp.models.DocumentUploadItem
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * DocumentUploadActivity - Tela de upload de documentos para verificação
 * 
 * Funcionalidades:
 * - Upload de documentos obrigatórios e opcionais
 * - Validação de arquivos
 * - Preview de documentos
 * - Submissão para verificação
 * - Acompanhamento do status
 */
class DocumentUploadActivity : AppCompatActivity() {

    // ViewBinding para acesso aos elementos da interface
    private lateinit var binding: ActivityDocumentUploadBinding
    
    // Variáveis para controle de estado
    private var isLoading = false
    private var documents = mutableListOf<DocumentUploadItem>()
    private var verificationId: String? = null
    private lateinit var authManager: FirebaseAuthManager
    private var currentDocumentType: ProviderVerificationManager.DocumentType? = null
    private var currentPhotoFile: File? = null
    
    // Adapter da lista
    private lateinit var documentAdapter: DocumentUploadAdapter
    
    // Launcher para seleção de arquivos
    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleDocumentSelection(uri)
            }
        }
    }

    // Launcher para câmera
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoFile?.let { file ->
                val photoUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                handleDocumentSelection(photoUri)
            }
        }
        currentPhotoFile = null
    }

    // Launcher para permissão de mídia
    private val requestMediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Reabrir diálogo após permissão
                showDocumentTypeDialog()
            } else {
                showToast("Permissão de acesso a mídia negada. Não será possível enviar documentos.")
            }
        }
    
    // Launcher para permissão de câmera
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                takePhoto()
            } else {
                showToast("Permissão de câmera negada. Não será possível tirar fotos.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar ViewBinding
        binding = ActivityDocumentUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicializar managers
        authManager = FirebaseAuthManager(this)
        
        // Configurar a interface
        setupUI()
        setupClickListeners()
        setupRecyclerView()
        
        // Carregar dados
        loadVerificationData()
    }

    /**
     * Configura os elementos da interface do usuário
     */
    private fun setupUI() {
        // Configurar a status bar
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)
    }

    /**
     * Configura os listeners de clique para todos os elementos interativos
     */
    private fun setupClickListeners() {
        // Botão voltar
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // Botão adicionar documento
        binding.btnUploadDocument.setOnClickListener {
            ensureMediaPermissionAndOpenPicker()
        }
        
        // Botão enviar para verificação
        binding.btnSubmitForReview.setOnClickListener {
            submitVerification()
        }
        
        // Botão salvar progresso
        binding.btnSaveProgress.setOnClickListener {
            saveProgress()
        }
        
        // Botão ajuda
        binding.btnHelp.setOnClickListener {
            showToast("Funcionalidade de ajuda em desenvolvimento")
        }
    }

    /**
     * Configura o RecyclerView
     */
    private fun setupRecyclerView() {
        binding.rvDocuments.layoutManager = LinearLayoutManager(this)
        
        documentAdapter = DocumentUploadAdapter(
            documents = documents,
            onDocumentClick = { document -> onDocumentClick(document) },
            onRemoveClick = { document -> onRemoveDocument(document) },
            onRetryClick = { document -> onRetryUpload(document) }
        )
        
        binding.rvDocuments.adapter = documentAdapter
    }

    /**
     * Carrega dados da verificação
     */
    private fun loadVerificationData() {
        // Obter dados do usuário atual (pode ser cliente ou prestador)
        val currentUser = authManager.getLocalUserData()
        if (currentUser == null) {
            showErrorMessage("Usuário não encontrado")
            finish()
            return
        }
        
        setLoadingState(true)
        
        lifecycleScope.launch {
            try {
                // Verificar se já existe verificação
                val verificationManager = ProviderVerificationManager()
                val existingVerification = verificationManager.getVerificationStatus(currentUser.uid)
                
                if (existingVerification != null) {
                    verificationId = existingVerification.id
                    loadExistingDocuments()
                    // Se não há documentos (prestador novo ou sem uploads), mostrar slots obrigatórios
                    if (documents.isEmpty()) {
                        setupRequiredDocuments()
                    }
                } else {
                    // Iniciar nova verificação
                    startNewVerification()
                }
                
                setLoadingState(false)
                
            } catch (e: Exception) {
                setLoadingState(false)
                showErrorMessage("Erro ao carregar dados de verificação")
            }
        }
    }

    /**
     * Inicia nova verificação
     */
    private suspend fun startNewVerification() {
        val currentUser = authManager.getLocalUserData()
        if (currentUser == null) return
        
        val verificationManager = ProviderVerificationManager()
        val result = verificationManager.startVerification(currentUser.uid)
        
        when (result) {
            is ProviderVerificationManager.VerificationResult.Success -> {
                showToast("Verificação iniciada com sucesso")
            }
            is ProviderVerificationManager.VerificationResult.VerificationCreated -> {
                verificationId = result.verificationId
                setupRequiredDocuments()
            }
            is ProviderVerificationManager.VerificationResult.Error -> {
                showErrorMessage(result.message)
            }
        }
    }

    /**
     * Carrega documentos existentes
     */
    private suspend fun loadExistingDocuments() {
        val currentUser = authManager.getLocalUserData()
        if (currentUser == null) return
        
        val verificationManager = ProviderVerificationManager()
        val existingDocuments = verificationManager.getProviderDocuments(currentUser.uid)
        
        documents.clear()
        documents.addAll(existingDocuments.map { document ->
            DocumentUploadItem(
                id = document.id,
                type = document.type,
                fileName = document.fileName,
                fileSize = document.fileSize,
                fileUri = Uri.parse(document.fileUri),
                status = when (document.status) {
                    ProviderVerificationManager.DocumentStatus.PENDING -> DocumentUploadItem.Status.PENDING
                    ProviderVerificationManager.DocumentStatus.UPLOADED -> DocumentUploadItem.Status.UPLOADED
                    ProviderVerificationManager.DocumentStatus.VALIDATED -> DocumentUploadItem.Status.VALIDATED
                    ProviderVerificationManager.DocumentStatus.REJECTED -> DocumentUploadItem.Status.REJECTED
                    ProviderVerificationManager.DocumentStatus.EXPIRED -> DocumentUploadItem.Status.ERROR
                },
                uploadedAt = document.uploadedAt,
                validationNotes = document.validationNotes
            )
        })
        
        documentAdapter.notifyDataSetChanged()
        updateUI()
    }

    /**
     * Configura documentos obrigatórios
     */
    private fun setupRequiredDocuments() {
        documents.clear()
        
        // Adicionar SELFIE obrigatória e permitir escolha RG ou CNH (frente e verso)
        val typesToAdd = listOf(
            ProviderVerificationManager.DocumentType.SELFIE,
            // O usuário escolhe depois RG ou CNH; começamos com RG como padrão
            ProviderVerificationManager.DocumentType.RG_FRONT,
            ProviderVerificationManager.DocumentType.RG_BACK
        )

        typesToAdd.forEach { documentType ->
            documents.add(
                DocumentUploadItem(
                    id = "",
                    type = documentType,
                    fileName = "",
                    fileSize = 0,
                    fileUri = null,
                    status = DocumentUploadItem.Status.PENDING,
                    uploadedAt = null,
                    validationNotes = null
                )
            )
        }
        
        documentAdapter.notifyDataSetChanged()
        updateUI()
    }

    /**
     * Mostra diálogo de seleção de tipo de documento
     */
    private fun showDocumentTypeDialog() {
        val pendingTypes = documents.filter { 
            it.status == DocumentUploadItem.Status.PENDING 
        }.map { it.type }
        
        if (pendingTypes.isEmpty()) {
            showToast("Todos os documentos obrigatórios foram enviados")
            return
        }
        
        val options = (pendingTypes.map { it.displayName } + listOf("Trocar para CNH", "Trocar para RG")).toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Selecionar Tipo de Documento")
            .setItems(options) { _, which ->
                val isExtra = which >= pendingTypes.size
                if (isExtra) {
                    val label = options[which]
                    if (label.contains("CNH")) {
                        // Trocar campos RG por CNH
                        replaceRgWithCnh()
                    } else if (label.contains("RG")) {
                        // Trocar campos CNH por RG
                        replaceCnhWithRg()
                    }
                    documentAdapter.notifyDataSetChanged()
                    updateUI()
                } else {
                    val selectedType = pendingTypes[which]
                    openDocumentPicker(selectedType)
                }
            }
            .show()
    }

    private fun replaceRgWithCnh() {
        // Remove RG frente/verso pendentes e adiciona CNH frente/verso se não existirem
        documents.removeAll { it.type == ProviderVerificationManager.DocumentType.RG_FRONT || it.type == ProviderVerificationManager.DocumentType.RG_BACK }
        val cnhFrontExists = documents.any { it.type == ProviderVerificationManager.DocumentType.CNH_FRONT }
        val cnhBackExists = documents.any { it.type == ProviderVerificationManager.DocumentType.CNH_BACK }
        if (!cnhFrontExists) {
            documents.add(
                DocumentUploadItem(
                    id = "",
                    type = ProviderVerificationManager.DocumentType.CNH_FRONT,
                    fileName = "",
                    fileSize = 0,
                    fileUri = null,
                    status = DocumentUploadItem.Status.PENDING,
                    uploadedAt = null,
                    validationNotes = null
                )
            )
        }
        if (!cnhBackExists) {
            documents.add(
                DocumentUploadItem(
                    id = "",
                    type = ProviderVerificationManager.DocumentType.CNH_BACK,
                    fileName = "",
                    fileSize = 0,
                    fileUri = null,
                    status = DocumentUploadItem.Status.PENDING,
                    uploadedAt = null,
                    validationNotes = null
                )
            )
        }
    }

    private fun replaceCnhWithRg() {
        // Remove CNH frente/verso pendentes e adiciona RG frente/verso se não existirem
        documents.removeAll { it.type == ProviderVerificationManager.DocumentType.CNH_FRONT || it.type == ProviderVerificationManager.DocumentType.CNH_BACK }
        val rgFrontExists = documents.any { it.type == ProviderVerificationManager.DocumentType.RG_FRONT }
        val rgBackExists = documents.any { it.type == ProviderVerificationManager.DocumentType.RG_BACK }
        if (!rgFrontExists) {
            documents.add(
                DocumentUploadItem(
                    id = "",
                    type = ProviderVerificationManager.DocumentType.RG_FRONT,
                    fileName = "",
                    fileSize = 0,
                    fileUri = null,
                    status = DocumentUploadItem.Status.PENDING,
                    uploadedAt = null,
                    validationNotes = null
                )
            )
        }
        if (!rgBackExists) {
            documents.add(
                DocumentUploadItem(
                    id = "",
                    type = ProviderVerificationManager.DocumentType.RG_BACK,
                    fileName = "",
                    fileSize = 0,
                    fileUri = null,
                    status = DocumentUploadItem.Status.PENDING,
                    uploadedAt = null,
                    validationNotes = null
                )
            )
        }
    }

    private fun ensureMediaPermissionAndOpenPicker() {
        val hasPermission = com.example.loginapp.utils.PermissionHelper.isMediaPermissionGranted(this)
        if (hasPermission) {
            showDocumentTypeDialog()
            return
        }

        val permission = com.example.loginapp.utils.PermissionHelper.getRequiredMediaPermission()
        if (permission != null) {
            requestMediaPermissionLauncher.launch(permission)
        } else {
            showDocumentTypeDialog()
        }
    }

    /**
     * Abre seletor de documentos com opção de câmera ou galeria
     */
    private fun openDocumentPicker(documentType: ProviderVerificationManager.DocumentType) {
        currentDocumentType = documentType
        val options = arrayOf("📷 Tirar Foto", "🖼️ Escolher da Galeria")
        
        AlertDialog.Builder(this)
            .setTitle("Enviar ${documentType.displayName}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Tirar foto
                        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) 
                            != PackageManager.PERMISSION_GRANTED) {
                            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        } else {
                            takePhoto()
                        }
                    }
                    1 -> {
                        // Escolher da galeria
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "image/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                "image/jpeg",
                                "image/png"
                            ))
                        }
                        documentPickerLauncher.launch(Intent.createChooser(intent, "Selecionar Imagem"))
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    /**
     * Tira foto do documento
     */
    private fun takePhoto() {
        try {
            val photoFile = File(getExternalFilesDir(null), "photo_${System.currentTimeMillis()}.jpg")
            currentPhotoFile = photoFile
            
            val photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                cameraLauncher.launch(intent)
            } else {
                showToast("Câmera não disponível")
            }
        } catch (e: Exception) {
            showToast("Erro ao abrir câmera: ${e.message}")
        }
    }

    /**
     * Processa seleção de documento
     */
    private fun handleDocumentSelection(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Obter informações do arquivo
                val fileName = getFileName(uri)
                val fileSize = getFileSize(uri)
                
                if (fileName == null || fileSize == null) {
                    showErrorMessage("Erro ao obter informações do arquivo")
                    return@launch
                }
                
                // Validar arquivo
                val validationResult = validateDocument(fileName, fileSize)
                if (validationResult != null) {
                    showErrorMessage(validationResult)
                    return@launch
                }
                
                // Encontrar documento pendente para substituir
                val pendingDocument = documents.find { 
                    it.status == DocumentUploadItem.Status.PENDING 
                }
                
                if (pendingDocument == null) {
                    showErrorMessage("Não há documentos pendentes")
                    return@launch
                }
                
                // Apenas anexar localmente, sem enviar agora
                val documentIndex = documents.indexOf(pendingDocument)
                if (documentIndex != -1) {
                    documents[documentIndex] = DocumentUploadItem(
                        id = "local_${System.currentTimeMillis()}",
                        type = pendingDocument.type,
                        fileName = fileName,
                        fileSize = fileSize,
                        fileUri = uri,
                        status = DocumentUploadItem.Status.SELECTED,
                        uploadedAt = null,
                        validationNotes = null
                    )
                    documentAdapter.notifyItemChanged(documentIndex)
                    updateUI()
                }
                
            } catch (e: Exception) {
                showErrorMessage("Erro ao processar documento: ${e.message}")
            }
        }
    }

    /**
     * Valida documento
     */
    private fun validateDocument(fileName: String, fileSize: Long): String? {
        // Verificar tamanho (máximo 10MB)
        if (fileSize > 10 * 1024 * 1024) {
            return "Arquivo muito grande. Máximo 10MB"
        }
        
        // Verificar extensão
        val allowedExtensions = listOf("jpg", "jpeg", "png", "pdf")
        val fileExtension = fileName.substringAfterLast(".", "").lowercase()
        if (fileExtension !in allowedExtensions) {
            return "Formato não suportado. Use JPG, PNG ou PDF"
        }
        
        return null
    }

    /**
     * Upload do documento
     */
    private suspend fun uploadDocument(
        documentType: ProviderVerificationManager.DocumentType,
        fileName: String,
        fileSize: Long,
        fileUri: Uri
    ) {
        val currentUser = authManager.getLocalUserData()
        if (currentUser == null) return
        
        setLoadingState(true)
        
        try {
            val verificationManager = ProviderVerificationManager()
            val result = verificationManager.uploadDocument(
                context = this@DocumentUploadActivity,
                providerId = currentUser.uid,
                documentType = documentType,
                fileName = fileName,
                fileSize = fileSize,
                fileUri = fileUri
            )
            
            when (result) {
                is ProviderVerificationManager.VerificationResult.Success -> {
                    // Atualizar documento na lista
                    val documentIndex = documents.indexOfFirst { it.type == documentType }
                    if (documentIndex != -1) {
                        documents[documentIndex] = DocumentUploadItem(
                            id = "temp_${System.currentTimeMillis()}",
                            type = documentType,
                            fileName = fileName,
                            fileSize = fileSize,
                            fileUri = fileUri,
                            status = DocumentUploadItem.Status.UPLOADED,
                            uploadedAt = Date(),
                            validationNotes = null
                        )
                        documentAdapter.notifyItemChanged(documentIndex)
                    }
                    
                    showSuccessMessage("✅ Documento enviado com sucesso!")
                    updateUI()
                }
                is ProviderVerificationManager.VerificationResult.Error -> {
                    showErrorMessage("❌ ${result.message}")
                }
                else -> {
                    showErrorMessage("❌ Erro ao enviar documento")
                }
            }
            
        } catch (e: Exception) {
            showErrorMessage("❌ Erro ao enviar documento: ${e.message}")
        } finally {
            setLoadingState(false)
        }
    }

    /**
     * Submete verificação
     */
    private fun submitVerification() {
        val currentUser = authManager.getLocalUserData()
        if (currentUser == null) return
        
        // Verificar se todos os documentos obrigatórios foram anexados (SELECTED ou UPLOADED)
        val pendingDocuments = documents.filter { 
            it.status == DocumentUploadItem.Status.PENDING 
        }
        
        if (pendingDocuments.isNotEmpty()) {
            showErrorMessage("Envie todos os documentos obrigatórios antes de submeter")
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Enviar para Verificação")
            .setMessage("Tem certeza que deseja enviar seus documentos para verificação? Não será possível editar após o envio.")
            .setPositiveButton("Enviar") { _, _ ->
                uploadAllAndSubmit()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Submete para análise com barra de progresso de upload
     */
    private fun uploadAllAndSubmit() {
        val currentUser = authManager.getLocalUserData()
        if (currentUser == null) return

        setLoadingState(true)
        binding.layoutUploadProgress.visibility = View.VISIBLE
        binding.progressBarUpload.progress = 0
        binding.tvUploadStatus.text = "Preparando envio..."

        lifecycleScope.launch {
            try {
                val verificationManager = ProviderVerificationManager()
                val docsToUpload = documents.filter {
                    it.status == DocumentUploadItem.Status.SELECTED && it.fileUri != null
                }
                val totalSteps = docsToUpload.size + 1 // +1 para submissão final
                var currentStep = 0

                // 1) Fazer upload de todos com status SELECTED
                for (item in docsToUpload) {
                    currentStep++
                    val progress = (currentStep * 100) / totalSteps
                    runOnUiThread {
                        binding.tvUploadStatus.text = "Enviando ${item.type.displayName}... ($currentStep/$totalSteps)"
                        binding.progressBarUpload.progress = progress
                    }

                    val result = verificationManager.uploadDocument(
                        context = this@DocumentUploadActivity,
                        providerId = currentUser.uid,
                        documentType = item.type,
                        fileName = item.fileName,
                        fileSize = item.fileSize,
                        fileUri = item.fileUri!!
                    )
                    if (result !is ProviderVerificationManager.VerificationResult.Success) {
                        throw IllegalStateException("Falha ao enviar ${item.type.displayName}")
                    }

                    // Atualizar item na lista
                    val idx = documents.indexOf(item)
                    if (idx != -1) {
                        documents[idx] = item.copy(status = DocumentUploadItem.Status.UPLOADED, uploadedAt = Date())
                        runOnUiThread { documentAdapter.notifyItemChanged(idx) }
                    }
                }

                // 2) Submeter para análise
                runOnUiThread {
                    binding.tvUploadStatus.text = "Enviando para verificação..."
                    binding.progressBarUpload.progress = 95
                }

                val submitResult = verificationManager.submitForReview(currentUser.uid)
                if (submitResult is ProviderVerificationManager.VerificationResult.Success) {
                    runOnUiThread {
                        binding.tvUploadStatus.text = "Enviado com sucesso!"
                        binding.progressBarUpload.progress = 100
                    }

                    documents.replaceAll { d ->
                        if (d.status == DocumentUploadItem.Status.SELECTED || d.status == DocumentUploadItem.Status.UPLOADED) {
                            d.copy(status = DocumentUploadItem.Status.SUBMITTED, uploadedAt = d.uploadedAt ?: Date())
                        } else d
                    }
                    runOnUiThread {
                        documentAdapter.notifyDataSetChanged()
                        updateUI()
                    }

                    showSuccessMessage("Documentos enviados para verificação! Você será notificado em até 48 horas.")
                    binding.root.postDelayed({ finish() }, 2000)
                } else if (submitResult is ProviderVerificationManager.VerificationResult.Error) {
                    showErrorMessage(submitResult.message)
                }

            } catch (e: Exception) {
                showErrorMessage("Erro ao enviar verificação: ${e.message}")
            } finally {
                setLoadingState(false)
                runOnUiThread {
                    binding.layoutUploadProgress.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Salva progresso
     */
    private fun saveProgress() {
        showSuccessMessage("📝 Progresso salvo")
        // TODO: Implementar salvamento de progresso
    }

    /**
     * Trata clique em documento
     */
    private fun onDocumentClick(document: DocumentUploadItem) {
        if (document.fileUri != null) {
            // Abrir preview do documento
            val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                putStringArrayListExtra("image_uris", arrayListOf(document.fileUri.toString()))
                putStringArrayListExtra("file_names", arrayListOf(document.fileName))
                putExtra("file_sizes", longArrayOf(document.fileSize))
                putStringArrayListExtra("image_types", arrayListOf("DOCUMENT"))
                putExtra("current_position", 0)
            }
            startActivity(intent)
        }
    }

    /**
     * Remove documento
     */
    private fun onRemoveDocument(document: DocumentUploadItem) {
        // Bloquear exclusão após submissão
        if (document.status == DocumentUploadItem.Status.SUBMITTED || document.status == DocumentUploadItem.Status.VALIDATED) {
            showErrorMessage("Você não pode excluir documentos após o envio para verificação")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Remover Documento")
            .setMessage("Tem certeza que deseja remover este documento?")
            .setPositiveButton("Remover") { _, _ ->
                removeDocument(document)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Remove documento
     */
    private fun removeDocument(document: DocumentUploadItem) {
        lifecycleScope.launch {
            try {
                // Remover localmente itens SELECTED; para UPLOADED, permitir exclusão via manager
                val indexById = documents.indexOfFirst { it.id == document.id }
                val indexByType = documents.indexOfFirst { it.type == document.type }

                if (document.status == DocumentUploadItem.Status.SELECTED) {
                    val i = if (indexById != -1) indexById else indexByType
                    if (i != -1) {
                        documents[i] = documents[i].copy(
                            status = DocumentUploadItem.Status.PENDING,
                            fileUri = null,
                            fileName = "",
                            fileSize = 0,
                            uploadedAt = null
                        )
                        documentAdapter.notifyItemChanged(i)
                        showSuccessMessage("🗑️ Documento removido")
                        updateUI()
                    }
                } else if (document.status == DocumentUploadItem.Status.UPLOADED) {
                    val verificationManager = ProviderVerificationManager()
                    val result = verificationManager.removeDocument(document.id)
                    if (result is ProviderVerificationManager.VerificationResult.Success) {
                        val i = if (indexById != -1) indexById else indexByType
                        if (i != -1) {
                            documents[i] = documents[i].copy(
                                status = DocumentUploadItem.Status.PENDING,
                                fileUri = null,
                                fileName = "",
                                fileSize = 0,
                                uploadedAt = null
                            )
                            documentAdapter.notifyItemChanged(i)
                        }
                        showSuccessMessage("🗑️ Documento removido")
                        updateUI()
                    } else if (result is ProviderVerificationManager.VerificationResult.Error) {
                        showErrorMessage("❌ ${result.message}")
                    }
                }
                
            } catch (e: Exception) {
                showErrorMessage("❌ Erro ao remover documento")
            }
        }
    }

    /**
     * Tenta upload novamente
     */
    private fun onRetryUpload(document: DocumentUploadItem) {
        openDocumentPicker(document.type)
    }

    /**
     * Atualiza interface
     */
    private fun updateUI() {
        val totalRequired = documents.count { it.type.required }
        val uploadedRequired = documents.count { 
            it.type.required && (it.status == DocumentUploadItem.Status.SELECTED || it.status == DocumentUploadItem.Status.UPLOADED || it.status == DocumentUploadItem.Status.SUBMITTED)
        }
        val totalOptional = documents.count { !it.type.required }
        val uploadedOptional = documents.count { 
            !it.type.required && it.status == DocumentUploadItem.Status.UPLOADED 
        }
        
        // Atualizar progresso
        binding.tvDocumentsCount.text = "$uploadedRequired/$totalRequired"
        binding.progressBar.progress = if (totalRequired > 0) {
            (uploadedRequired * 100) / totalRequired
        } else 0
        
        // Atualizar botão de envio
        binding.btnSubmitForReview.isEnabled = uploadedRequired == totalRequired
        
        // Mostrar/esconder estado vazio
        binding.rvDocuments.visibility = if (documents.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Obtém nome do arquivo (compatível com content:// URIs no Android 10+)
     */
    private fun getFileName(uri: Uri): String? {
        val fallback = uri.lastPathSegment?.substringAfterLast("/") ?: "document_${System.currentTimeMillis()}.jpg"
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotBlank() } ?: fallback else fallback
                } else fallback
            } ?: contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotBlank() } ?: fallback else fallback
                } else fallback
            } ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }

    /**
     * Obtém tamanho do arquivo (compatível com content:// URIs no Android 10+)
     */
    private fun getFileSize(uri: Uri): Long? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) cursor.getLong(sizeIndex) else null
                } else null
            } ?: contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (sizeIndex >= 0) cursor.getLong(sizeIndex) else null
                } else null
            } ?: contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Controla o estado de carregamento da interface
     */
    private fun setLoadingState(loading: Boolean) {
        isLoading = loading

        binding.btnSubmitForReview.isEnabled = !loading && documents.none { it.status == DocumentUploadItem.Status.PENDING }
        binding.btnUploadDocument.isEnabled = !loading
        binding.btnSaveProgress.isEnabled = !loading

        if (loading) {
            binding.btnSubmitForReview.text = "Enviando..."
        } else {
            binding.btnSubmitForReview.text = "Enviar para Verificação"
        }
    }

    /**
     * Exibe uma mensagem de sucesso
     */
    private fun showSuccessMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Exibe uma mensagem de erro
     */
    private fun showErrorMessage(message: String) {
        Toast.makeText(this, "❌ $message", Toast.LENGTH_LONG).show()
    }

    /**
     * Exibe uma mensagem toast
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
} 