package com.aquiresolve.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.databinding.ActivityRefundRequestBinding
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Tela onde o cliente solicita o reembolso de um pedido pago: descreve o motivo e
 * anexa fotos. Ao enviar, sobe as fotos para o Storage e grava a solicitação no pedido
 * (refundStatus = 'requested'), que o admin aprova ou recusa no painel.
 */
class RefundRequestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRefundRequestBinding
    private val imageManager = FirebaseImageManager()
    private val orderManager = FirebaseOrderManager()

    private var orderId: String? = null
    private val selectedPhotos = mutableListOf<Uri>()
    private var isLoading = false

    companion object {
        private const val MAX_PHOTOS = 5
    }

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        for (uri in uris) {
            if (selectedPhotos.size >= MAX_PHOTOS) {
                showToast("Máximo de $MAX_PHOTOS fotos")
                break
            }
            if (uri !in selectedPhotos) selectedPhotos.add(uri)
        }
        renderPhotos()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRefundRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderId = intent.getStringExtra("order_id")
        if (orderId.isNullOrBlank()) {
            showToast("Pedido inválido")
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnAddPhoto.setOnClickListener {
            if (selectedPhotos.size >= MAX_PHOTOS) {
                showToast("Máximo de $MAX_PHOTOS fotos")
            } else {
                photoPicker.launch("image/*")
            }
        }
        binding.btnSubmitRefund.setOnClickListener { submit() }
    }

    private fun renderPhotos() {
        binding.llPhotos.removeAllViews()
        val size = (96 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()
        selectedPhotos.forEach { uri ->
            val frame = FrameLayout(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = margin
                }
            }
            val img = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(size, size)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            Glide.with(this).load(uri).centerCrop().into(img)
            val remove = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (28 * resources.displayMetrics.density).toInt(),
                    (28 * resources.displayMetrics.density).toInt(),
                    android.view.Gravity.END or android.view.Gravity.TOP
                )
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundResource(R.drawable.circle_background_secondary)
                setPadding(4, 4, 4, 4)
                setOnClickListener {
                    selectedPhotos.remove(uri)
                    renderPhotos()
                }
            }
            frame.addView(img)
            frame.addView(remove)
            binding.llPhotos.addView(frame)
        }
    }

    private fun submit() {
        if (isLoading) return
        val reason = binding.etRefundReason.text?.toString()?.trim().orEmpty()
        if (reason.length < 10) {
            binding.etRefundReason.error = "Descreva o motivo (mín. 10 caracteres)"
            return
        }
        val id = orderId ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            showToast("Sessão expirada. Faça login novamente.")
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                // 1) Sobe as fotos (se houver) para order_images/{orderId}/...
                val photoUrls = mutableListOf<String>()
                selectedPhotos.forEachIndexed { index, uri ->
                    val uploadData = FirebaseImageManager.ImageUploadData(
                        uri = uri,
                        fileName = "refund_${id}_${System.currentTimeMillis()}_$index.jpg",
                        folder = FirebaseImageManager.FOLDER_ORDER_IMAGES,
                        userId = uid,
                        orderId = id
                    )
                    when (val result = imageManager.uploadImage(this@RefundRequestActivity, uploadData)) {
                        is FirebaseImageManager.UploadResult.Success -> photoUrls.add(result.downloadUrl)
                        is FirebaseImageManager.UploadResult.Error -> {
                            showToast("❌ Falha ao enviar foto: ${result.message}")
                            setLoading(false)
                            return@launch
                        }
                        else -> {}
                    }
                }

                // 2) Grava a solicitação no pedido
                val res = orderManager.requestRefund(id, reason, photoUrls)
                if (res.isSuccess) {
                    showToast("✅ Solicitação de reembolso enviada! A equipe vai analisar.")
                    setResult(RESULT_OK)
                    finish()
                } else {
                    showToast("❌ ${res.exceptionOrNull()?.message ?: "Não foi possível enviar a solicitação"}")
                    setLoading(false)
                }
            } catch (e: Exception) {
                showToast("❌ Erro: ${e.message}")
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSubmitRefund.isEnabled = !loading
        binding.btnAddPhoto.isEnabled = !loading
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
