package com.aquiresolve.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.aquiresolve.app.adapters.OsPhotosAdapter
import com.aquiresolve.app.databinding.ActivityPhotoEvidenceBinding
import com.aquiresolve.app.utils.VerificationCodeDialog
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class PhotoEvidenceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoEvidenceBinding
    private lateinit var checklistManager: FirebaseChecklistManager
    private lateinit var imageManager: FirebaseImageManager

    private var orderId: String? = null
    private var isProviderView = false

    // Photos lists
    private val beforePhotos = mutableListOf<Uri>()
    private val duringPhotos = mutableListOf<Uri>()
    private val afterPhotos = mutableListOf<Uri>()

    private lateinit var beforeAdapter: OsPhotosAdapter
    private lateinit var duringAdapter: OsPhotosAdapter
    private lateinit var afterAdapter: OsPhotosAdapter

    private var currentCategory = "before"
    private var cameraImageUri: Uri? = null

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { addPhotoToCurrentCategory(it) }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            addPhotoToCurrentCategory(cameraImageUri!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoEvidenceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderId = intent.getStringExtra("order_id")
        isProviderView = intent.getBooleanExtra("is_provider_view", false)

        if (orderId == null) {
            Toast.makeText(this, "Pedido não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.secondary_color)
        checklistManager = FirebaseChecklistManager()
        imageManager = FirebaseImageManager()

        setupAdapters()
        setupClickListeners()
    }

    private fun setupAdapters() {
        beforeAdapter = OsPhotosAdapter(
            uris = beforePhotos,
            onRemove = { pos ->
                beforePhotos.removeAt(pos)
                beforeAdapter.updateUris(beforePhotos)
                updateCounts()
            },
            onAdd = { showImagePickerDialog("before") }
        )
        binding.rvPhotosBefore.layoutManager = GridLayoutManager(this, 3)
        binding.rvPhotosBefore.adapter = beforeAdapter

        duringAdapter = OsPhotosAdapter(
            uris = duringPhotos,
            onRemove = { pos ->
                duringPhotos.removeAt(pos)
                duringAdapter.updateUris(duringPhotos)
                updateCounts()
            },
            onAdd = { showImagePickerDialog("during") }
        )
        binding.rvPhotosDuring.layoutManager = GridLayoutManager(this, 3)
        binding.rvPhotosDuring.adapter = duringAdapter

        afterAdapter = OsPhotosAdapter(
            uris = afterPhotos,
            onRemove = { pos ->
                afterPhotos.removeAt(pos)
                afterAdapter.updateUris(afterPhotos)
                updateCounts()
            },
            onAdd = { showImagePickerDialog("after") }
        )
        binding.rvPhotosAfter.layoutManager = GridLayoutManager(this, 3)
        binding.rvPhotosAfter.adapter = afterAdapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.btnAddBefore.setOnClickListener { showImagePickerDialog("before") }
        binding.btnAddDuring.setOnClickListener { showImagePickerDialog("during") }
        binding.btnAddAfter.setOnClickListener { showImagePickerDialog("after") }

        binding.btnContinue.setOnClickListener {
            if (validatePhotos()) {
                uploadAndSavePhotos()
            }
        }
    }

    private fun showImagePickerDialog(category: String) {
        currentCategory = category
        val options = arrayOf("📷 Câmera", "🖼️ Galeria")
        AlertDialog.Builder(this)
            .setTitle("Adicionar Foto")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        val photoFile = createImageFile()
        cameraImageUri = photoFile?.let {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)
        }
        cameraLauncher.launch(cameraImageUri)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openCamera()
        } else {
            Toast.makeText(this, "Permissão de câmera necessária", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun createImageFile(): File? {
        return try {
            val dir = File(cacheDir, "photos")
            dir.mkdirs()
            File(dir, "OS_${System.currentTimeMillis()}.jpg")
        } catch (e: IOException) {
            null
        }
    }

    private fun addPhotoToCurrentCategory(uri: Uri) {
        val list = when (currentCategory) {
            "before" -> beforePhotos
            "during" -> duringPhotos
            "after" -> afterPhotos
            else -> return
        }
        if (list.size >= 3) {
            Toast.makeText(this, "Máximo de 3 fotos por categoria", Toast.LENGTH_SHORT).show()
            return
        }
        list.add(uri)
        when (currentCategory) {
            "before" -> beforeAdapter.updateUris(beforePhotos)
            "during" -> duringAdapter.updateUris(duringPhotos)
            "after" -> afterAdapter.updateUris(afterPhotos)
        }
        updateCounts()
    }

    private fun updateCounts() {
        binding.tvBeforeCount.text = "${beforePhotos.size}/3"
        binding.tvDuringCount.text = "${duringPhotos.size}/3"
        binding.tvAfterCount.text = "${afterPhotos.size}/3"
    }

    private fun validatePhotos(): Boolean {
        if (beforePhotos.isEmpty()) {
            Toast.makeText(this, "Adicione fotos do antes do serviço", Toast.LENGTH_LONG).show()
            return false
        }
        if (afterPhotos.isEmpty()) {
            Toast.makeText(this, "Adicione fotos do depois do serviço", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun uploadAndSavePhotos() {
        lifecycleScope.launch {
            try {
                binding.btnContinue.isEnabled = false
                binding.btnContinue.text = "Enviando fotos..."

                uploadCategory("before", beforePhotos)
                if (duringPhotos.isNotEmpty()) uploadCategory("during", duringPhotos)
                uploadCategory("after", afterPhotos)

                requestClientCodeAndComplete()
            } catch (e: Exception) {
                Toast.makeText(this@PhotoEvidenceActivity, "Erro ao salvar fotos: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnContinue.isEnabled = true
                binding.btnContinue.text = "Enviar fotos e finalizar com código"
            }
        }
    }

    private fun requestClientCodeAndComplete() {
        binding.btnContinue.isEnabled = true
        binding.btnContinue.text = "Finalizar com código"
        VerificationCodeDialog.showCodeInputDialog(
            context = this,
            onCodeEntered = { code ->
                lifecycleScope.launch {
                    binding.btnContinue.isEnabled = false
                    binding.btnContinue.text = "Finalizando..."
                    val result = FirebaseOrderManager().completeOrderWithVerification(orderId!!, code)
                    if (result.isSuccess) {
                        checklistManager.markCompletedByClientCode(orderId!!)
                        Toast.makeText(this@PhotoEvidenceActivity, "Serviço finalizado com sucesso!", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@PhotoEvidenceActivity, ProviderHomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(
                            this@PhotoEvidenceActivity,
                            result.exceptionOrNull()?.message ?: "Erro ao finalizar serviço",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.btnContinue.isEnabled = true
                        binding.btnContinue.text = "Finalizar com código"
                    }
                }
            },
            onCancel = {
                Toast.makeText(this, "Fotos salvas. Finalize com o código do cliente quando estiver pronto.", Toast.LENGTH_LONG).show()
            }
        )
    }

    private suspend fun uploadCategory(category: String, uris: List<Uri>) {
        if (uris.isEmpty()) return

        val urls = mutableListOf<String>()
        val timestamps = mutableListOf<Timestamp>()

        for (uri in uris) {
            val uploadData = FirebaseImageManager.ImageUploadData(
                uri = uri,
                fileName = "os_${category}_${System.currentTimeMillis()}.jpg",
                folder = FirebaseImageManager.FOLDER_CHECKLISTS,
                orderId = orderId
            )
            when (val result = imageManager.uploadImage(this, uploadData)) {
                is FirebaseImageManager.UploadResult.Success -> {
                    urls.add(result.downloadUrl)
                    timestamps.add(Timestamp.now())
                }
                is FirebaseImageManager.UploadResult.Error -> {
                    throw Exception(result.message)
                }
                is FirebaseImageManager.UploadResult.Progress -> Unit
            }
        }

        if (urls.isEmpty()) {
            throw Exception("Nenhuma foto enviada para a categoria $category")
        }

        val saveResult = checklistManager.savePhotos(orderId!!, category, urls, timestamps)
        if (saveResult.isFailure) {
            throw saveResult.exceptionOrNull() ?: Exception("Erro ao salvar fotos da categoria $category")
        }
    }
}
