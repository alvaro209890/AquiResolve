package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.databinding.ActivityHomeBinding
import com.aquiresolve.app.utils.NotificationBadgeHelper
import com.aquiresolve.app.utils.ServiceSearchHelper
import kotlinx.coroutines.launch

/**
 * HomeActivity - Tela principal do aplicativo após o login
 * 
 * Esta activity gerencia a interface principal com:
 * - Barra de pesquisa
 * - Categorias de serviços
 * - Navegação inferior
 * - Lista de serviços em destaque
 */
class HomeActivity : AppCompatActivity() {

    // ViewBinding para acesso aos elementos da interface
    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar ViewBinding
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsHelper.apply(this, binding.rootLayout, binding.bottomNavigation)
        
        // Configurar a interface
        setupUI()
        setupClickListeners()
        loadUserData()
        
        // Verificar se há mensagem de alternância de conta
        checkSwitchMessage()
        
        // Verificar se pode alternar para prestador
        checkProviderSwitchOption()
        
        // Iniciar badge de notificações
        NotificationBadgeHelper.startListening(
            bottomNav = binding.bottomNavigation,
            menuItemId = R.id.navigation_orders
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        NotificationBadgeHelper.stopListening()
    }

    /**
     * Configura os elementos da interface do usuário
     */
    private fun setupUI() {
        // Configurar a status bar para ser transparente
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    /**
     * Configura os listeners de clique para todos os elementos interativos
     */
    private fun setupClickListeners() {
        // Barra de pesquisa - agora com busca inteligente
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            val query = binding.etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
            true
        }
        
        // Botão de filtro
        binding.ivFilter.setOnClickListener {
            showToast("🔧 Funcionalidade de filtro em desenvolvimento")
        }
        
        // Categorias de serviços: ir para aba Serviços (único local para fazer pedido)
        binding.cardPlumber.setOnClickListener { navigateToServices() }
        binding.cardElectrician.setOnClickListener { navigateToServices() }
        binding.cardPainter.setOnClickListener { navigateToServices() }
        binding.cardCleaning.setOnClickListener { navigateToServices() }
        binding.cardGardening.setOnClickListener { navigateToServices() }
        
        binding.cardMore.setOnClickListener {
            showToast("📋 Mais categorias em breve")
        }
        
        // Navegação inferior
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    // Já estamos na home
                    true
                }
                R.id.navigation_services -> {
                    startActivity(Intent(this, ServicesActivity::class.java))
                    true
                }
                R.id.navigation_orders -> {
                    val intent = Intent(this, ClientOrdersActivity::class.java)
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
    }

    /**
     * Carrega os dados do usuário logado
     */
    private fun loadUserData() {
        val user = LocalAuthManager.user
        if (user != null) {
            binding.tvWelcome.text = "Olá, ${user.fullName}! Que tipo de serviço você precisa?"
        }
    }

    /**
     * Executa a pesquisa inteligente.
     * Sempre mostra resultados na ServicesActivity com preços.
     */
    private fun performSearch(query: String) {
        if (query.isBlank()) return
        
        // Sempre navegar para ServicesActivity com a busca
        // Lá ele exibirá a lista de serviços encontrados com preços
        val intent = Intent(this, ServicesActivity::class.java).apply {
            putExtra("search_query", query)
        }
        startActivity(intent)
    }

    /**
     * Mostra o diálogo de filtros
     */
    private fun showFilterDialog() {
        showToast("🔧 Filtros em desenvolvimento...")
        // TODO: Implementar filtros
    }

    /**
     * Navega para uma categoria específica
     */
    private fun navigateToServiceCategory(category: String) {
        showToast("🔧 Navegando para: $category")
        // TODO: Implementar navegação para categoria
    }

    /**
     * Mostra todas as categorias
     */
    private fun showAllCategories() {
        showToast("📋 Todas as categorias em desenvolvimento...")
        // TODO: Implementar tela de todas as categorias
    }

    /**
     * Mostra todos os serviços
     */
    private fun showAllServices() {
        showToast("🔧 Todos os serviços em desenvolvimento...")
        // TODO: Implementar tela de todos os serviços
    }

    /**
     * Mostra os pedidos
     */
    private fun showOrders() {
        showToast("📋 Pedidos em desenvolvimento...")
        // TODO: Implementar tela de pedidos
    }

    /**
     * Navega para o perfil
     */
    private fun navigateToProfile() {
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
    }

    /**
     * Navega para a aba Serviços (único local onde cliente pode fazer pedido)
     */
    private fun navigateToServices() {
        startActivity(Intent(this, ServicesActivity::class.java))
    }

    /**
     * Verifica se há mensagem de alternância de conta
     */
    private fun checkSwitchMessage() {
        val showSwitchMessage = intent.getBooleanExtra("show_switch_message", false)
        if (showSwitchMessage) {
            val switchMessage = intent.getStringExtra("switch_message") ?: "Conta alterada com sucesso!"
            showToast(switchMessage)
        }
    }
    
    /**
     * Verifica se pode alternar para prestador
     */
    private fun checkProviderSwitchOption() {
        val canSwitchToProvider = intent.getBooleanExtra("can_switch_to_provider", false)
        if (canSwitchToProvider) {
            // Verificar se o usuário tem dados de prestador
            val provider = LocalAuthManager.getCurrentProviderData()
            if (provider != null) {
                // Mostrar opção para voltar à conta de prestador
                showProviderSwitchOption()
            }
        }
    }
    
    /**
     * Mostra opção para voltar à conta de prestador
     */
    private fun showProviderSwitchOption() {
        // Verificar se já existe o botão
        val existingButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_switch_to_provider)
        if (existingButton != null) return
        
        // Criar botão para voltar à conta de prestador
        val switchButton = com.google.android.material.button.MaterialButton(this).apply {
            id = R.id.btn_switch_to_provider
            text = "Voltar para Conta Prestador"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.white))
            backgroundTintList = ContextCompat.getColorStateList(this@HomeActivity, R.color.secondary_color)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(32, 16, 32, 16)
            }
            setOnClickListener {
                switchToProviderAccount()
            }
        }
        
        // Adicionar o botão na interface (você pode ajustar onde colocar)
        // Por exemplo, no topo da tela ou em uma posição específica
        val parentLayout = binding.root as android.widget.LinearLayout
        parentLayout.addView(switchButton, 0) // Adicionar no topo
    }
    
    /**
     * Volta para a conta de prestador
     */
    private fun switchToProviderAccount() {
        // Verificar se há dados de prestador
        val provider = LocalAuthManager.getCurrentProviderData()
        if (provider != null) {
            // Navegar diretamente para o dashboard do prestador
            val intent = Intent(this, ProviderDashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("show_welcome_message", true)
                putExtra("welcome_message", "🎉 Bem-vindo de volta à sua conta de Prestador!")
                putExtra("default_tab", 1) // Ir para aba Perfil
            }
            startActivity(intent)
            finish()
        } else {
            showToast("❌ Dados de prestador não encontrados")
        }
    }

    /**
     * Exibe uma mensagem toast para o usuário
     * 
     * @param message Mensagem a ser exibida
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}
