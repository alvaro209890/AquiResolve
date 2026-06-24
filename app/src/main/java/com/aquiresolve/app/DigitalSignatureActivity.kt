package com.aquiresolve.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.core.net.toUri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.databinding.ActivityDigitalSignatureBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

class DigitalSignatureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDigitalSignatureBinding
    private lateinit var checklistManager: FirebaseChecklistManager
    private lateinit var imageManager: FirebaseImageManager

    private var orderId: String? = null
    private var isProviderView = false
    private var orderProtocol: String? = null
    private var orderClientName: String? = null
    private var providerName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDigitalSignatureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderId = intent.getStringExtra("order_id")
        isProviderView = intent.getBooleanExtra("is_provider_view", false)
        orderProtocol = intent.getStringExtra("order_protocol")
        orderClientName = intent.getStringExtra("client_name")

        if (orderId == null) {
            Toast.makeText(this, "Pedido não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.secondary_color)
        checklistManager = FirebaseChecklistManager()
        imageManager = FirebaseImageManager()

        loadProviderData()
        setupClickListeners()
    }

    private fun loadProviderData() {
        lifecycleScope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser ?: return@launch
                val providerDoc = FirebaseFirestore.getInstance()
                    .collection("providers").document(user.uid).get().await()
                if (providerDoc.exists()) {
                    val name = providerDoc.getString("fullName") ?: user.displayName ?: ""
                    binding.etProviderName.setText(name)
                    providerName = name
                } else {
                    binding.etProviderName.setText(user.displayName ?: "")
                    providerName = user.displayName ?: ""
                }

                if (!orderClientName.isNullOrEmpty()) {
                    binding.etClientName.setText(orderClientName)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.btnClearProvider.setOnClickListener {
            binding.signaturePadProvider.clear()
        }

        binding.btnClearClient.setOnClickListener {
            binding.signaturePadClient.clear()
        }

        binding.btnFinish.setOnClickListener {
            if (validateSignatures()) {
                finishService()
            }
        }
    }

    private fun validateSignatures(): Boolean {
        val providerName = binding.etProviderName.text?.toString()?.trim()
        val clientName = binding.etClientName.text?.toString()?.trim()
        val clientDocument = binding.etClientDocument.text?.toString()?.trim()

        if (providerName.isNullOrEmpty()) {
            binding.tilProviderName.error = "Nome obrigatório"
            return false
        }
        binding.tilProviderName.error = null

        if (clientName.isNullOrEmpty()) {
            binding.tilClientName.error = "Nome obrigatório"
            return false
        }
        binding.tilClientName.error = null

        if (clientDocument.isNullOrEmpty()) {
            binding.tilClientDocument.error = "Documento obrigatório"
            return false
        }
        binding.tilClientDocument.error = null

        if (binding.signaturePadProvider.isEmpty()) {
            Toast.makeText(this, "Prestador deve assinar", Toast.LENGTH_LONG).show()
            return false
        }

        if (binding.signaturePadClient.isEmpty()) {
            Toast.makeText(this, "Cliente deve assinar", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    private fun finishService() {
        lifecycleScope.launch {
            try {
                binding.btnFinish.isEnabled = false
                binding.btnFinish.text = "Salvando..."

                val providerName = binding.etProviderName.text.toString().trim()
                val clientName = binding.etClientName.text.toString().trim()
                val clientDocument = binding.etClientDocument.text.toString().trim()

                // Save provider signature
                val providerBitmap = binding.signaturePadProvider.getSignatureBitmap()
                val providerUrl = if (providerBitmap != null) {
                    saveAndUploadSignature(providerBitmap, "provider")
                } else null
                if (providerUrl == null) {
                    throw Exception("Falha ao enviar assinatura do prestador")
                }

                // Save client signature
                val clientBitmap = binding.signaturePadClient.getSignatureBitmap()
                val clientUrl = if (clientBitmap != null) {
                    saveAndUploadSignature(clientBitmap, "client")
                } else null
                if (clientUrl == null) {
                    throw Exception("Falha ao enviar assinatura do cliente")
                }

                // Save to Firebase
                val providerSignatureResult = checklistManager.saveProviderSignature(orderId!!, providerUrl, providerName)
                if (providerSignatureResult.isFailure) {
                    throw providerSignatureResult.exceptionOrNull()
                        ?: Exception("Falha ao salvar assinatura do prestador")
                }

                val clientSignatureResult = checklistManager.saveClientSignature(orderId!!, clientUrl, clientName, clientDocument)
                if (clientSignatureResult.isFailure) {
                    throw clientSignatureResult.exceptionOrNull()
                        ?: Exception("Falha ao salvar assinatura do cliente")
                }

                // A OS apenas DOCUMENTA o serviço (checklist + fotos + assinaturas).
                // O encerramento do pedido é feito pelo prestador na tela de detalhes,
                // informando o código de verificação do cliente ("Finalizar com código").
                showSuccessAndFinish()
            } catch (e: Exception) {
                Toast.makeText(this@DigitalSignatureActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnFinish.isEnabled = true
                binding.btnFinish.text = "Finalizar e Concluir Serviço"
            }
        }
    }

    private suspend fun saveAndUploadSignature(bitmap: Bitmap, type: String): String? {
        return try {
            val dir = File(cacheDir, "signatures")
            dir.mkdirs()
            val file = File(dir, "signature_${type}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uploadData = FirebaseImageManager.ImageUploadData(
                uri = file.toUri(),
                fileName = file.name,
                folder = FirebaseImageManager.FOLDER_ORDER_IMAGES,
                orderId = orderId
            )
            val result = imageManager.uploadImage(this, uploadData)
            if (result is FirebaseImageManager.UploadResult.Success) {
                result.downloadUrl
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun showSuccessAndFinish() {
        AlertDialog.Builder(this)
            .setTitle("✅ OS Registrada")
            .setMessage("Checklist, fotos e assinaturas salvos com sucesso!\n\nPara CONCLUIR o pedido, toque em \"Finalizar com código\" e informe o código de verificação do cliente.")
            .setPositiveButton("OK") { _, _ ->
                val intent = Intent(this@DigitalSignatureActivity, ProviderHomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }
}
