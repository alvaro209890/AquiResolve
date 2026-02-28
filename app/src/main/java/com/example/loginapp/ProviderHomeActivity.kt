package com.example.loginapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.loginapp.databinding.ActivityProviderHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ProviderHomeActivity - Tela principal para prestadores
 * 
 * Interface específica para prestadores com:
 * - Dashboard de pedidos
 * - Estatísticas de trabalho
 * - Pedidos disponíveis
 * - Histórico de serviços
 * - Configurações de disponibilidade
 */
class ProviderHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProviderHomeBinding
    private lateinit var authManager: FirebaseAuthManager
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Garantir Firebase inicializado
        if (!FirebaseConfig.isInitialized()) {
            FirebaseConfig.initialize(this)
        }
        
        binding = ActivityProviderHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        authManager = FirebaseAuthManager(this)
        
        setupUI()
        setupClickListeners()
        loadProviderData()
    }
    
    override fun onResume() {
        super.onResume()
        ProviderNewOrderAlertManager.refreshMonitoring()
        // Recarregar dados quando voltar para esta tela (ex: após finalizar um pedido)
        // Adicionar um pequeno delay para garantir que o Firestore tenha atualizado os dados
        lifecycleScope.launch {
            delay(500) // Aguardar 500ms para garantir que o Firestore propagou a atualização
            loadProviderStats()
        }
    }

    /**
     * Configura a interface específica para prestadores
     */
    private fun setupUI() {
        // Status bar personalizada para prestadores
        window.statusBarColor = ContextCompat.getColor(this, R.color.secondary_color)
        
        // Configurar título específico para prestadores
        binding.tvWelcome.text = "Bem-vindo de volta!"
        binding.tvDashboardTitle.text = "Dashboard"
        binding.tvAvailableOrders.text = "Pedidos Disponíveis"
    }

    /**
     * Configura os listeners específicos para prestadores
     */
    private fun setupClickListeners() {
        // Barra de pesquisa de pedidos
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }
        
        // Botão de filtro
        binding.ivFilter.setOnClickListener {
            showFilterDialog()
        }
        
        // Botão de disponibilidade
        binding.btnAvailability.setOnClickListener {
            toggleAvailability()
        }
        
        // Botão de ver todos os pedidos
        binding.btnViewAllOrders.setOnClickListener {
            val intent = Intent(this, ProviderOrdersActivity::class.java)
            startActivity(intent)
        }
        
        // Navegação inferior específica para prestadores
        binding.bottomNavigation.menu.clear()
        binding.bottomNavigation.inflateMenu(R.menu.bottom_nav_menu_provider)
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    // Já estamos na home
                    true
                }
                R.id.navigation_orders -> {
                    // Ir para lista de pedidos do prestador
                    val intent = Intent(this, ProviderOrdersActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.navigation_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        
        // Botão de notificações
        binding.btnNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationSettingsActivity::class.java))
        }
        
        // Botão de configurações
        binding.btnSettings.setOnClickListener {
            showSettings()
        }
    }

    /**
     * Carrega os dados do prestador
     */
    private fun loadProviderData() {
        val user = authManager.getLocalUserData()
        if (user != null) {
            val firstName = user.fullName.ifEmpty { user.username }.trim().split(" ").firstOrNull() ?: "Prestador"
            binding.tvWelcome.text = "Bem-vindo de volta, $firstName!"
        }
        
        // Carregar estatísticas do prestador
        loadProviderStats()

        // Carregar pedidos disponíveis
        loadAvailableOrders()
    }

    /**
     * Carrega as estatísticas do prestador
     */
    private fun loadProviderStats() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            binding.tvCompletedServices.text = "0"
            binding.tvActiveOrders.text = "0"
            binding.tvEarnings.text = "R$ 0,00"
            return
        }
        
        lifecycleScope.launch {
            try {
                // Carregar dados do prestador do Firestore
                val providerDoc = db.collection("providers")
                    .document(currentUser.uid)
                    .get()
                    .await()
                
                if (providerDoc.exists()) {
                    val completedJobs = (providerDoc.getLong("completedJobs") ?: 0L).toInt()
                    val totalEarnings = providerDoc.getDouble("totalEarnings") ?: 0.0
                    
                    // Contar pedidos ativos (assigned ou in_progress)
                    val activeOrdersSnap = db.collection("orders")
                        .whereEqualTo("assignedProvider", currentUser.uid)
                        .whereIn("status", listOf("assigned", "in_progress"))
                        .get()
                        .await()
                    
                    val activeOrders = activeOrdersSnap.size()
                    
                    // Carregar nota média
                    val rating = providerDoc.getDouble("rating") ?: 0.0
                    val totalRatings = (providerDoc.getLong("totalRatings") ?: 0L).toInt()

                    // Atualizar interface
                    binding.tvCompletedServices.text = completedJobs.toString()
                    binding.tvActiveOrders.text = activeOrders.toString()
                    binding.tvEarnings.text = formatCurrency(totalEarnings)
                    binding.tvProviderRating.text = if (rating > 0) String.format("%.1f", rating) else "—"
                    binding.tvProviderRatingCount.text = if (totalRatings > 0) "Nota média ($totalRatings avaliações)" else "Sem avaliações ainda"
                    
                    android.util.Log.d("ProviderHome", "📊 Estatísticas carregadas - Completados: $completedJobs, Ativos: $activeOrders, Lucro: R$ $totalEarnings")
                } else {
                    // Prestador não encontrado, usar valores padrão
                    binding.tvCompletedServices.text = "0"
                    binding.tvActiveOrders.text = "0"
                    binding.tvEarnings.text = "R$ 0,00"
                }
            } catch (e: Exception) {
                android.util.Log.e("ProviderHome", "❌ Erro ao carregar estatísticas: ${e.message}")
                binding.tvCompletedServices.text = "0"
                binding.tvActiveOrders.text = "0"
                binding.tvEarnings.text = "R$ 0,00"
            }
        }
    }
    
    /**
     * Formata valor monetário
     */
    private fun formatCurrency(value: Double): String {
        return "R$ ${String.format("%.2f", value).replace(".", ",")}"
    }

    /**
     * Carrega os pedidos disponíveis
     */
    private fun loadAvailableOrders() {
        // TODO: Implementar carregamento de pedidos do Firestore
        // Por enquanto, mostra mensagem de exemplo
        binding.tvNoAvailableOrders.text = "Nenhum pedido disponível no momento."
    }

    /**
     * Executa a pesquisa de pedidos
     */
    private fun performSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isNotEmpty()) {
            showToast("🔍 Pesquisando por: $query")
            // TODO: Implementar pesquisa real
        }
    }

    /**
     * Mostra o diálogo de filtros
     */
    private fun showFilterDialog() {
        showToast("🔧 Filtros em desenvolvimento...")
        // TODO: Implementar filtros específicos para prestadores
    }

    /**
     * Alterna a disponibilidade do prestador
     */
    private fun toggleAvailability() {
        // TODO: Implementar toggle de disponibilidade
        val isAvailable = binding.btnAvailability.text.toString().contains("Disponível")
        if (isAvailable) {
            binding.btnAvailability.text = "🔴 Indisponível"
            binding.btnAvailability.setBackgroundColor(ContextCompat.getColor(this, R.color.error_color))
            showToast("Você está agora indisponível")
        } else {
            binding.btnAvailability.text = "🟢 Disponível"
            binding.btnAvailability.setBackgroundColor(ContextCompat.getColor(this, R.color.success_color))
            showToast("Você está agora disponível")
        }
    }

    /**
     * Mostra configurações
     */
    private fun showSettings() {
        showToast("⚙️ Configurações em desenvolvimento...")
        // TODO: Implementar tela de configurações
    }

    /**
     * Exibe uma mensagem toast
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpar recursos se necessário
    }
}
