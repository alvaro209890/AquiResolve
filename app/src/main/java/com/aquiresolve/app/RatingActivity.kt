package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.databinding.ActivityRatingBinding
import kotlinx.coroutines.launch

/**
 * RatingActivity - Tela de avaliação de prestadores
 * 
 * Funcionalidades:
 * - Avaliação por estrelas (1-5)
 * - Comentários e feedback
 * - Categorias de avaliação (qualidade, pontualidade, etc.)
 * - Envio da avaliação
 */
class RatingActivity : AppCompatActivity() {

    // ViewBinding para acesso aos elementos da interface
    private lateinit var binding: ActivityRatingBinding
    
    // Variáveis para controle de estado
    private var isLoading = false
    private var currentRating = 0
    private var orderId: String? = null
    private var providerName: String? = null
    
    // Avaliações por categoria
    private var qualityRating = 0
    private var punctualityRating = 0
    private var communicationRating = 0
    private var cleanlinessRating = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar ViewBinding
        binding = ActivityRatingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Obter dados da intent
        orderId = intent.getStringExtra("order_id")
        providerName = intent.getStringExtra("provider_name")
        
        // Configurar a interface
        setupUI()
        setupClickListeners()
    }

    /**
     * Configura os elementos da interface do usuário
     */
    private fun setupUI() {
        // Configurar a status bar
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        
        // Configurar dados do prestador
        providerName?.let { name ->
            binding.tvProviderName.text = name
        }
        
        // Configurar estrelas iniciais
        setupStarRatings()
    }

    /**
     * Configura os listeners de clique para todos os elementos interativos
     */
    private fun setupClickListeners() {
        // Botão voltar
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // Botão enviar avaliação
        binding.btnSubmitRating.setOnClickListener {
            submitRating()
        }
        
        // Botão pular (opcional)
        binding.btnSkip.setOnClickListener {
            skipRating()
        }
    }

    /**
     * Configura os listeners das estrelas
     */
    private fun setupStarRatings() {
        // Rating geral
        setupStarRating(
            binding.ratingBar,
            binding.tvRatingLabel
        ) { rating ->
            currentRating = rating
            updateOverallRating()
        }
        
        // Rating por qualidade
        setupStarRating(
            binding.ratingBarQuality,
            binding.tvQualityLabel
        ) { rating ->
            qualityRating = rating
        }
        
        // Rating por pontualidade
        setupStarRating(
            binding.ratingBarPunctuality,
            binding.tvPunctualityLabel
        ) { rating ->
            punctualityRating = rating
        }
        
        // Rating por comunicação
        setupStarRating(
            binding.ratingBarCommunication,
            binding.tvCommunicationLabel
        ) { rating ->
            communicationRating = rating
        }
        
        // Rating por limpeza
        setupStarRating(
            binding.ratingBarCleanliness,
            binding.tvCleanlinessLabel
        ) { rating ->
            cleanlinessRating = rating
        }
    }

    /**
     * Configura um rating bar com listener
     */
    private fun setupStarRating(
        ratingBar: android.widget.RatingBar,
        label: android.widget.TextView,
        onRatingChanged: (Int) -> Unit
    ) {
        ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
            if (fromUser) {
                onRatingChanged(rating.toInt())
            }
        }
    }

    /**
     * Atualiza o rating geral baseado nas categorias
     */
    private fun updateOverallRating() {
        val labels = listOf(
            "Péssimo",
            "Ruim", 
            "Regular",
            "Bom",
            "Excelente"
        )
        
        if (currentRating > 0 && currentRating <= labels.size) {
            binding.tvRatingLabel.text = labels[currentRating - 1]
        }
    }

    /**
     * Envia a avaliação
     */
    private fun submitRating() {
        if (isLoading) return
        
        // Validar se pelo menos o rating geral foi dado
        if (currentRating == 0) {
            showToast("Por favor, dê uma avaliação geral")
            return
        }
        
        // Obter comentário
        val comment = binding.etComment.text.toString().trim()
        
        // Simular envio
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
                    review = comment.ifEmpty { null },
                    detailedRatings = FirebaseOrderManager.DetailedRatings(
                        qualityRating = qualityRating.takeIf { it > 0 },
                        punctualityRating = punctualityRating.takeIf { it > 0 },
                        communicationRating = communicationRating.takeIf { it > 0 },
                        cleanlinessRating = cleanlinessRating.takeIf { it > 0 }
                    )
                )

                if (rateResult.isFailure) {
                    val errorMessage = rateResult.exceptionOrNull()?.message ?: "Não foi possível salvar sua avaliação"
                    showToast("❌ $errorMessage")
                    return@launch
                }

                showToast("✅ Avaliação enviada com sucesso!")
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

    /**
     * Pula a avaliação
     */
    private fun skipRating() {
        showToast("Avaliação pulada")
        setResult(RESULT_CANCELED)
        finish()
    }

    /**
     * Mostra uma mensagem toast
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
} 
