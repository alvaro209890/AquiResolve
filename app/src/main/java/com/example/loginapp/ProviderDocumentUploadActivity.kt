package com.example.loginapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.loginapp.databinding.ActivityProviderDocumentUploadBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.*

/**
 * ProviderDocumentUploadActivity - Tela para upload de documentos de prestadores
 */
class ProviderDocumentUploadActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProviderDocumentUploadBinding
    private val auth = FirebaseAuth.getInstance()
    private val verificationManager = ProviderVerificationManager()
    
    // Armazena seleção de arquivos por tipo
    private val selectedDocs = mutableMapOf<ProviderVerificationManager.DocumentType, Uri>()
    private var currentDocType: ProviderVerificationManager.DocumentType? = null
    
    // Constantes para câmera e galeria
    companion object {
        private const val REQUEST_CAMERA = 1001
        private const val REQUEST_GALLERY = 1002
        private const val REQUEST_CAMERA_PERMISSION = 1003
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityProviderDocumentUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupClickListeners()
    }
    
    /**
     * Configura a interface
     */
    private fun setupUI() {
        // Configurar título
        binding.tvTitle.text = "Documentos para Verificação"
        binding.tvSubtitle.text = "Envie os documentos obrigatórios para ativar sua conta de prestador"
        
        // Configurar lista de documentos
        setupDocumentList()
    }
    
    /**
     * Configura a lista de documentos obrigatórios
     */
    private fun setupDocumentList() {
        binding.tvDocumentList.text = buildString {
            appendLine("📋 Documentos Obrigatórios:")
            appendLine()
            appendLine("1. RG ou CNH (Frente) - ❌ Pendente")
            appendLine("2. RG ou CNH (Verso) - ❌ Pendente")
            appendLine("3. Comprovante de Residência - ❌ Pendente")
            appendLine("4. Selfie para Verificação - ❌ Pendente")
        }
    }
    
    /**
     * Configura os listeners
     */
    private fun setupClickListeners() {
        // Botão voltar
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // Botão enviar documentos
        binding.btnSubmitDocuments.setOnClickListener {
            submitDocuments()
        }
        
        // Botões de upload para cada documento
        binding.btnUploadRgFrente.setOnClickListener {
            showDocumentPicker(ProviderVerificationManager.DocumentType.RG_FRONT)
        }
        
        binding.btnUploadRgVerso.setOnClickListener {
            showDocumentPicker(ProviderVerificationManager.DocumentType.RG_BACK)
        }
        
        binding.btnUploadComprovante.setOnClickListener {
            showDocumentPicker(ProviderVerificationManager.DocumentType.PROOF_OF_ADDRESS)
        }
        
        binding.btnUploadSelfie.setOnClickListener {
            showDocumentPicker(ProviderVerificationManager.DocumentType.SELFIE)
        }
    }
    
    /**
     * Mostra seletor de documentos
     */
    private fun showDocumentPicker(documentType: ProviderVerificationManager.DocumentType) {
        currentDocType = documentType
        val options = arrayOf("📷 Tirar Foto", "🖼️ Galeria")
        
        AlertDialog.Builder(this)
            .setTitle("Enviar ${documentType.displayName}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> selectFromGallery()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    /**
     * Tira foto do documento
     */
    private fun takePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            return
        }
        
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_CAMERA)
        } else {
            showErrorMessage("Câmera não disponível")
        }
    }
    
    /**
     * Seleciona da galeria
     */
    private fun selectFromGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(Intent.createChooser(intent, "Selecionar documento"), REQUEST_GALLERY)
    }
    
    /**
     * Processa resultado da câmera/galeria
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CAMERA -> {
                    val imageUri = data?.data
                    if (imageUri != null) {
                        registerSelectedDoc(imageUri)
                        showSuccessMessage("Foto capturada com sucesso!")
                    }
                }
                REQUEST_GALLERY -> {
                    val imageUri = data?.data
                    if (imageUri != null) {
                        registerSelectedDoc(imageUri)
                        showSuccessMessage("Imagem selecionada com sucesso!")
                    }
                }
            }
        }
    }
    
    /**
     * Processa permissões
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showSuccessMessage("Permissão da câmera concedida")
            } else {
                showErrorMessage("Permissão da câmera negada")
            }
        }
    }
    
    /**
     * Envia documentos para aprovação
     */
    private fun submitDocuments() {
        lifecycleScope.launch {
            try {
                setLoadingState(true)
                
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    showErrorMessage("Usuário não autenticado")
                    return@launch
                }
                // Regras mínimas: selfie obrigatória + RG (frente/verso) ou CNH (frente/verso)
                val hasSelfie = selectedDocs.containsKey(ProviderVerificationManager.DocumentType.SELFIE)
                val hasRg = selectedDocs.containsKey(ProviderVerificationManager.DocumentType.RG_FRONT) &&
                        selectedDocs.containsKey(ProviderVerificationManager.DocumentType.RG_BACK)
                val hasCnh = selectedDocs.containsKey(ProviderVerificationManager.DocumentType.CNH_FRONT) &&
                        selectedDocs.containsKey(ProviderVerificationManager.DocumentType.CNH_BACK)
                
                if (!hasSelfie) {
                    showErrorMessage("Envie a selfie obrigatória.")
                    setLoadingState(false)
                    return@launch
                }
                if (!hasRg && !hasCnh) {
                    showErrorMessage("Envie RG (frente/verso) ou CNH (frente/verso).")
                    setLoadingState(false)
                    return@launch
                }

                // Upload de todos os selecionados
                selectedDocs.forEach { (type, uri) ->
                    val fileName = getFileName(uri) ?: "${type.name.lowercase()}_${System.currentTimeMillis()}.jpg"
                    val fileSize = getFileSize(uri)
                    val result = verificationManager.uploadDocument(
                        context = this@ProviderDocumentUploadActivity,
                        providerId = currentUser.uid,
                        documentType = type,
                        fileName = fileName,
                        fileSize = fileSize,
                        fileUri = uri
                    )
                    if (result !is ProviderVerificationManager.VerificationResult.Success) {
                        throw IllegalStateException("Falha ao enviar ${type.displayName}")
                    }
                }

                // Submeter para análise
                val submitResult = verificationManager.submitForReview(currentUser.uid)
                if (submitResult is ProviderVerificationManager.VerificationResult.Success) {
                    showSuccessMessage("✅ Documentos enviados para aprovação!")
                    showSuccessMessage("⏳ Aguarde a análise da equipe administrativa")
                    val intent = Intent(this@ProviderDocumentUploadActivity, ProviderHomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                } else if (submitResult is ProviderVerificationManager.VerificationResult.Error) {
                    showErrorMessage(submitResult.message)
                }
                
            } catch (e: Exception) {
                showErrorMessage("Erro ao enviar documentos: ${e.message}")
            } finally {
                setLoadingState(false)
            }
        }
    }
    
    /**
     * Controla estado de loading
     */
    private fun setLoadingState(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSubmitDocuments.isEnabled = !loading
    }
    
    /**
     * Mostra mensagem de sucesso
     */
    private fun showSuccessMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Mostra mensagem de erro
     */
    private fun showErrorMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun registerSelectedDoc(uri: Uri) {
        val type = currentDocType ?: return
        selectedDocs[type] = uri
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    if (nameIndex >= 0) it.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}