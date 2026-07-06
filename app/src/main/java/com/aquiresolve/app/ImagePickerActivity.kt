package com.aquiresolve.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.databinding.ActivityImagePickerBinding
import com.aquiresolve.app.utils.ImagePermissionHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * ImagePickerActivity - Tela para seleção de imagens
 * 
 * Funcionalidades:
 * - Seleção de imagem da galeria
 * - Captura de foto com câmera
 * - Processamento e compressão
 * - Upload para Firebase Storage
 * - Preview da imagem selecionada
 */
class ImagePickerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityImagePickerBinding
    private lateinit var permissionManager: com.aquiresolve.app.utils.ActivityPermissionManager
    private lateinit var firebaseImageManager: FirebaseImageManager
    
    // URIs e arquivos temporários
    private var selectedImageUri: Uri? = null
    private var cameraImageUri: Uri? = null
    private var tempCameraFile: File? = null
    
    // Configurações
    private var maxImages: Int = 1
    private var folder: String = FirebaseImageManager.FOLDER_ORDER_IMAGES
    private var userId: String? = null
    private var orderId: String? = null
    
    // Launchers para resultados
    // Android Photo Picker — não exige permissão de mídia (política do Google Play)
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { handleImageSelected(it) }
    }
    
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            handleImageSelected(cameraImageUri!!)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityImagePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicializar componentes
        permissionManager = com.aquiresolve.app.utils.ActivityPermissionManager(this)
        firebaseImageManager = FirebaseImageManager()
        
        // Obter parâmetros da intent
        getIntentParameters()
        
        // Configurar UI
        setupUI()
        setupClickListeners()
    }
    
    /**
     * Obtém parâmetros da intent
     */
    private fun getIntentParameters() {
        maxImages = intent.getIntExtra(EXTRA_MAX_IMAGES, 1)
        folder = intent.getStringExtra(EXTRA_FOLDER) ?: FirebaseImageManager.FOLDER_ORDER_IMAGES
        userId = intent.getStringExtra(EXTRA_USER_ID)
        orderId = intent.getStringExtra(EXTRA_ORDER_ID)
    }
    
    /**
     * Configura a interface
     */
    private fun setupUI() {
        // Configurar título baseado no contexto
        val title = when (folder) {
            FirebaseImageManager.FOLDER_PROFILE_IMAGES -> "Foto do Perfil"
            FirebaseImageManager.FOLDER_ORDER_IMAGES -> "Fotos do Pedido"
            FirebaseImageManager.FOLDER_SERVICE_IMAGES -> "Fotos do Serviço"
            else -> "Selecionar Imagem"
        }
        
        binding.tvTitle.text = title
        
        // Mostrar/ocultar preview
        binding.layoutPreview.visibility = View.GONE
    }
    
    /**
     * Configura os listeners de clique
     */
    private fun setupClickListeners() {
        // Botão galeria
        binding.btnGallery.setOnClickListener {
            checkPermissionsAndOpenGallery()
        }
        
        // Botão câmera
        binding.btnCamera.setOnClickListener {
            checkPermissionsAndOpenCamera()
        }
        
        // Botão remover imagem
        binding.btnRemoveImage.setOnClickListener {
            removeSelectedImage()
        }
        
        // Botão confirmar
        binding.btnConfirm.setOnClickListener {
            confirmImageSelection()
        }
        
        // Botão cancelar
        binding.btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
    
    /**
     * Abre a galeria (Photo Picker) — sem pedir permissão de mídia.
     */
    private fun checkPermissionsAndOpenGallery() {
        openGallery()
    }

    /**
     * Verifica permissão de câmera e abre a câmera
     */
    private fun checkPermissionsAndOpenCamera() {
        permissionManager.checkAndRequestCameraPermission(
            onGranted = { openCamera() },
            onDenied = { showPermissionDeniedDialog() }
        )
    }

    /**
     * Abre galeria de imagens (Android Photo Picker)
     */
    private fun openGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    
    /**
     * Abre câmera para captura
     */
    private fun openCamera() {
        try {
            // Criar arquivo temporário para a foto
            tempCameraFile = createTempImageFile()
            cameraImageUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                tempCameraFile!!
            )
            
            cameraLauncher.launch(cameraImageUri)
        } catch (e: IOException) {
            Toast.makeText(this, "Erro ao abrir câmera", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Cria arquivo temporário para foto da câmera
     */
    private fun createTempImageFile(): File {
        val imageFileName = "TEMP_${System.currentTimeMillis()}"
        val storageDir = getExternalFilesDir("Pictures")
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }
    
    /**
     * Processa imagem selecionada
     */
    private fun handleImageSelected(uri: Uri) {
        selectedImageUri = uri
        
        // Mostrar preview
        binding.layoutPreview.visibility = View.VISIBLE
        binding.ivPreview.setImageURI(uri)
        
        // Habilitar botão confirmar
        binding.btnConfirm.isEnabled = true
        
        // Mostrar informações da imagem
        showImageInfo(uri)
    }
    
    /**
     * Mostra informações da imagem selecionada
     */
    private fun showImageInfo(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Processar imagem para obter metadados
                val result = ImageManager.processImage(this@ImagePickerActivity, uri)
                when (result) {
                    is ImageManager.ProcessResult.Success -> {
                        val processedImage = result.processedImage
                        binding.tvImageInfo.text = buildString {
                            append("Arquivo: ${processedImage.fileName}\n")
                            append("Tamanho: ${formatFileSize(processedImage.originalSize)}\n")
                            append("Dimensões: ${processedImage.width}x${processedImage.height}")
                            if (processedImage.isCompressed) {
                                append("\nComprimido: ${formatFileSize(processedImage.compressedSize ?: 0)}")
                            }
                        }
                    }
                    is ImageManager.ProcessResult.Error -> {
                        binding.tvImageInfo.text = "Erro ao processar imagem"
                    }
                }
            } catch (e: Exception) {
                binding.tvImageInfo.text = "Erro ao obter informações da imagem"
            }
        }
    }
    
    /**
     * Remove imagem selecionada
     */
    private fun removeSelectedImage() {
        selectedImageUri = null
        binding.layoutPreview.visibility = View.GONE
        binding.btnConfirm.isEnabled = false
        
        // Limpar arquivo temporário da câmera
        tempCameraFile?.delete()
        tempCameraFile = null
        cameraImageUri = null
    }
    
    /**
     * Confirma seleção da imagem
     */
    private fun confirmImageSelection() {
        val uri = selectedImageUri ?: return
        
        // Mostrar loading
        binding.progressBar.visibility = View.VISIBLE
        binding.btnConfirm.isEnabled = false
        
        lifecycleScope.launch {
            try {
                // Processar imagem
                val processResult = ImageManager.processImage(this@ImagePickerActivity, uri)
                when (processResult) {
                    is ImageManager.ProcessResult.Success -> {
                        // Fazer upload para Firebase Storage
                        val uploadData = FirebaseImageManager.ImageUploadData(
                            uri = processResult.processedImage.compressedUri ?: uri,
                            fileName = processResult.processedImage.fileName,
                            folder = folder,
                            userId = userId,
                            orderId = orderId,
                            metadata = mapOf(
                                "originalSize" to processResult.processedImage.originalSize.toString(),
                                "compressedSize" to (processResult.processedImage.compressedSize?.toString() ?: "0"),
                                "dimensions" to "${processResult.processedImage.width}x${processResult.processedImage.height}",
                                "mimeType" to processResult.processedImage.mimeType
                            )
                        )
                        
                        val uploadResult = firebaseImageManager.uploadImage(
                            this@ImagePickerActivity,
                            uploadData
                        ) { progress ->
                            runOnUiThread {
                                binding.progressBar.progress = progress
                            }
                        }
                        
                        when (uploadResult) {
                            is FirebaseImageManager.UploadResult.Success -> {
                                // Ocultar loading antes de finalizar
                                binding.progressBar.visibility = View.GONE
                                binding.btnConfirm.isEnabled = true
                                
                                // Retornar resultado
                                val resultIntent = Intent().apply {
                                    putExtra(EXTRA_IMAGE_URL, uploadResult.downloadUrl)
                                    putExtra(EXTRA_FILE_NAME, uploadResult.fileName)
                                    putExtra(EXTRA_IMAGE_URI, uri.toString())
                                }
                                setResult(Activity.RESULT_OK, resultIntent)
                                finish()
                            }
                            is FirebaseImageManager.UploadResult.Error -> {
                                binding.progressBar.visibility = View.GONE
                                binding.btnConfirm.isEnabled = true
                                showErrorDialog("Erro no upload: ${uploadResult.message}")
                            }
                            is FirebaseImageManager.UploadResult.Progress -> {
                                // Progress já é tratado no callback
                            }
                        }
                    }
                    is ImageManager.ProcessResult.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnConfirm.isEnabled = true
                        showErrorDialog("Erro ao processar imagem: ${processResult.message}")
                    }
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnConfirm.isEnabled = true
                showErrorDialog("Erro inesperado: ${e.message}")
            }
        }
    }
    
    /**
     * Mostra diálogo de erro
     */
    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Erro")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Mostra diálogo de permissão negada
     */
    private fun showPermissionDeniedDialog() {
        ImagePermissionHelper.showSettingsDialog(
            this,
            onPositiveClick = { ImagePermissionHelper.openAppSettings(this) }
        )
    }
    
    /**
     * Formata tamanho do arquivo
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Limpar arquivo temporário
        tempCameraFile?.delete()
    }
    
    companion object {
        // Constantes para Intent
        const val EXTRA_MAX_IMAGES = "max_images"
        const val EXTRA_FOLDER = "folder"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_ORDER_ID = "order_id"
        
        // Constantes para resultado
        const val EXTRA_IMAGE_URL = "image_url"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_IMAGE_URI = "image_uri"
        
        /**
         * Cria intent para ImagePickerActivity
         */
        fun createIntent(
            context: Activity,
            folder: String = FirebaseImageManager.FOLDER_ORDER_IMAGES,
            userId: String? = null,
            orderId: String? = null,
            maxImages: Int = 1
        ): Intent {
            return Intent(context, ImagePickerActivity::class.java).apply {
                putExtra(EXTRA_FOLDER, folder)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_MAX_IMAGES, maxImages)
            }
        }
    }
}
