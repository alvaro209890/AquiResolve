package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.databinding.ActivityRatingBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/**
 * RatingActivity - Tela de avaliação de prestadores (v2)
 *
 * Melhorias:
 * - Emoji reactions conforme a nota
 * - Tags rápidas (ChipGroup): "Pontual", "Preço justo", "Profissional", etc.
 * - Info do serviço realizado
 * - Contador de caracteres no comentário
 * - Feedback visual ao enviar
 */
class RatingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRatingBinding

    private var isLoading = false
    private var currentRating = 0
    private var orderId: String? = null
    private var providerName: String? = null
    private var serviceType: String? = null
    private var serviceName: String? = null

    private var qualityRating = 0
    private var punctualityRating = 0
    private var communicationRating = 0
    private var cleanlinessRating = 0

    private val selectedTags = mutableSetOf<String>()

    companion object {
        private const val MAX_COMMENT_LENGTH = 500

        private val TAG_OPTIONS = listOf(
            "Pontual", "Preço justo", "Profissional", "Serviço rápido",
            "Bem avaliado", "Atencioso", "Organizado", "Recomendo"
        )

        private val EMOJI_BY_RATING = mapOf(
            0 to "⭐",
            1 to "😞",
            2 to "😕",
            3 to "😐",
            4 to "😊",
            5 to "😍"
        )

        private val LABEL_BY_RATING = mapOf(
            0 to "Toque nas estrelas para avaliar",
            1 to "Precisa melhorar muito",
            2 to "Deixou a desejar",
            3 to "Foi razoável",
            4 to "Muito bom!",
            5 to "Excelente! Adorei!"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRatingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderId = intent.getStringExtra("order_id")
        providerName = intent.getStringExtra("provider_name")
        serviceType = intent.getStringExtra("service_type")
        serviceName = intent.getStringExtra("service_name")

        setupUI()
        setupClickListeners()
        setupTagChips()
        setupCommentCounter()
    }

    private fun setupUI() {
        // Barra de status: cor sólida do tema (Android <15) / faixa do EdgeToEdgeInsets (15+).
        // O hack fullscreen antigo deixava o conteúdo sob a barra de status sem compensação.

        // Nome do prestador
        providerName?.let { name ->
            binding.tvProviderName.text = name
        }

        // Info do serviço
        val serviceInfo = buildString {
            if (!serviceName.isNullOrBlank()) append(serviceName)
            if (!serviceType.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append(serviceType)
            }
        }
        binding.tvServiceInfo.text = serviceInfo.ifEmpty { "Serviço realizado" }

        setupStarRatings()
        updateEmojiAndLabel(0)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSubmitRating.setOnClickListener { submitRating() }
        binding.btnSkip.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun setupTagChips() {
        val chipGroup = binding.chipGroupTags
        chipGroup.removeAllViews()

        for (tag in TAG_OPTIONS) {
            val chip = Chip(this).apply {
                text = tag
                isCheckable = true
                isCheckedIconVisible = true
                chipBackgroundColor = ContextCompat.getColorStateList(
                    this@RatingActivity,
                    android.R.color.transparent
                )?.let { null } // usamos o estilo padrão
                setChipBackgroundColorResource(R.color.orange_50)
                setTextColor(ContextCompat.getColor(this@RatingActivity, R.color.text_primary))
                setOnCheckedChangeListener { button, isChecked ->
                    if (isChecked) selectedTags.add(tag) else selectedTags.remove(tag)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupCommentCounter() {
        binding.tvCharCount.text = "0/$MAX_COMMENT_LENGTH"
        binding.etComment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val length = s?.length ?: 0
                binding.tvCharCount.text = "$length/$MAX_COMMENT_LENGTH"
                if (length > MAX_COMMENT_LENGTH) {
                    binding.tvCharCount.setTextColor(ContextCompat.getColor(this@RatingActivity, R.color.error_color))
                } else {
                    binding.tvCharCount.setTextColor(ContextCompat.getColor(this@RatingActivity, R.color.gray_400))
                }
            }
        })
    }

    private fun setupStarRatings() {
        // Rating geral
        setupStarRating(binding.ratingBar) { rating ->
            currentRating = rating
            updateEmojiAndLabel(rating)
            // Mostrar/esconder detalhadas
            binding.cardDetailedRatings.visibility = if (rating > 0) View.VISIBLE else View.GONE
        }

        setupStarRating(binding.ratingBarQuality) { qualityRating = it }
        setupStarRating(binding.ratingBarPunctuality) { punctualityRating = it }
        setupStarRating(binding.ratingBarCommunication) { communicationRating = it }
        setupStarRating(binding.ratingBarCleanliness) { cleanlinessRating = it }
    }

    private fun setupStarRating(
        ratingBar: android.widget.RatingBar,
        onRatingChanged: (Int) -> Unit
    ) {
        ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
            if (fromUser) {
                onRatingChanged(rating.toInt())
            }
        }
    }

    private fun updateEmojiAndLabel(rating: Int) {
        binding.tvEmojiFeedback.text = EMOJI_BY_RATING[rating] ?: "⭐"
        binding.tvRatingLabel.text = LABEL_BY_RATING[rating] ?: ""
    }

    private fun submitRating() {
        if (isLoading) return

        if (currentRating == 0) {
            showToast("Por favor, dê uma avaliação geral ⭐")
            return
        }

        val comment = binding.etComment.text.toString().trim()
        if (comment.length > MAX_COMMENT_LENGTH) {
            showToast("O comentário não pode exceder $MAX_COMMENT_LENGTH caracteres")
            return
        }

        // As tags são persistidas em um campo estruturado (ratingTags), não no texto.
        val reviewComment = comment.takeIf { it.isNotBlank() }
        val tagsToSubmit = selectedTags.toList()

        isLoading = true
        binding.btnSubmitRating.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val selectedOrderId = orderId
                if (selectedOrderId.isNullOrBlank()) {
                    showToast("❌ ID do pedido não encontrado")
                    return@launch
                }

                val orderManager = FirebaseOrderManager()

                val rateResult = orderManager.submitOrderRating(
                    orderId = selectedOrderId,
                    rating = currentRating,
                    review = reviewComment,
                    detailedRatings = FirebaseOrderManager.DetailedRatings(
                        qualityRating = qualityRating.takeIf { it > 0 },
                        punctualityRating = punctualityRating.takeIf { it > 0 },
                        communicationRating = communicationRating.takeIf { it > 0 },
                        cleanlinessRating = cleanlinessRating.takeIf { it > 0 }
                    ),
                    tags = tagsToSubmit
                )

                if (rateResult.isFailure) {
                    val errorMessage = rateResult.exceptionOrNull()?.message
                        ?: "Não foi possível salvar sua avaliação"
                    showToast("❌ $errorMessage")
                    return@launch
                }

                showToast("✅ Avaliação enviada! Obrigado pelo feedback.")
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                showToast("❌ Erro ao enviar avaliação: ${e.message}")
            } finally {
                isLoading = false
                binding.btnSubmitRating.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
