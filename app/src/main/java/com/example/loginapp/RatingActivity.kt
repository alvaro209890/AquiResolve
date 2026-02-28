package com.example.loginapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.loginapp.databinding.ActivityRatingBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    private var providerId: String? = null
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
        providerId = intent.getStringExtra("provider_id")
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
                val orderManager = FirebaseOrderManager()

                // 1. Salvar avaliação no pedido
                val rateResult = orderManager.rateOrder(
                    orderId = orderId ?: "",
                    rating = currentRating,
                    review = comment.ifEmpty { null }
                )

                if (rateResult.isFailure) {
                    showToast("❌ Erro ao salvar avaliação")
                    return@launch
                }

                // 2. Atualizar nota média do prestador
                if (!providerId.isNullOrEmpty()) {
                    updateProviderAverageRating(providerId!!)
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
     * Calcula e atualiza a nota média do prestador no Firebase
     */
    private suspend fun updateProviderAverageRating(providerId: String) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            // Buscar todos os pedidos avaliados deste prestador
            val ratedOrders = db.collection("orders")
                .whereEqualTo("assignedProvider", providerId)
                .whereGreaterThan("rating", 0)
                .get()
                .await()

            if (ratedOrders.isEmpty) return

            var totalRating = 0.0
            var count = 0
            for (doc in ratedOrders.documents) {
                val r = doc.getLong("rating")?.toDouble() ?: continue
                totalRating += r
                count++
            }

            if (count == 0) return
            val average = totalRating / count

            // Salvar nota média e total de avaliações no perfil do prestador
            db.collection("providers").document(providerId)
                .update(
                    mapOf(
                        "rating" to average,
                        "totalRatings" to count,
                        "updatedAt" to java.util.Date()
                    )
                )
                .await()
        } catch (e: Exception) {
            android.util.Log.e("RatingActivity", "Erro ao atualizar média: ${e.message}")
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