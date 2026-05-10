package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aquiresolve.app.adapters.ServiceTypesAdapter
import com.aquiresolve.app.databinding.ActivityServicesBinding
import com.aquiresolve.app.models.ServicePricing
import com.aquiresolve.app.models.ServiceType
import com.aquiresolve.app.utils.ServiceSearchHelper
import kotlinx.coroutines.launch

/**
 * ServicesActivity - Tela de serviços simplificada
 * 
 * Interface moderna para seleção de nichos de serviços
 * Segue o padrão da tela inicial com cards de categorias
 */
class ServicesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServicesBinding
    private lateinit var serviceManager: FirebaseServiceManager
    
    // Estado
    private var searchQuery = ""
    private var isLoading = false
    
    // Adapter para resultados da busca com preços
    private var searchResultsAdapter: ServiceTypesAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityServicesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        serviceManager = FirebaseServiceManager()
        
        setupWindowInsets()
        setupUI()
        setupClickListeners()
        setupSearchListener()
        populateSampleDataIfNeeded()
        
        // Verificar se veio com busca pré-preenchida
        val searchQueryFromIntent = intent.getStringExtra("search_query")
        if (!searchQueryFromIntent.isNullOrEmpty()) {
            binding.etSearch.setText(searchQueryFromIntent)
            binding.etSearch.setSelection(searchQueryFromIntent.length)
        }
    }

    /**
     * Configura a interface
     */
    private fun setupUI() {
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background_color)
        
        // Configurar RecyclerView para resultados da busca
        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        
        hideEmptyState()
        hideLoadingState()
    }

    private fun setupWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.appBarLayout.updatePadding(top = systemBars.top)
            binding.contentScroll.updatePadding(bottom = dpToPx(32) + systemBars.bottom)

            windowInsets
        }
    }

    /**
     * Configura os listeners de clique
     */
    private fun setupClickListeners() {
        // Botão voltar
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // Cards de nichos de serviços (novas categorias)
        binding.cardLimpeza.setOnClickListener {
            navigateToServiceCategory("estofados", "Limpeza de estofados")
        }
        
        binding.cardManutencao.setOnClickListener {
            navigateToServiceCategory("encanador", "Encanador")
        }
        
        binding.cardEletrica.setOnClickListener {
            navigateToServiceCategory("eletrica", "Elétrica")
        }
        
        binding.cardEncanamento.setOnClickListener {
            navigateToServiceCategory("instalacao", "Instalação")
        }
        
        binding.cardPintura.setOnClickListener {
            navigateToServiceCategory("caixa_dagua", "Caixa d'água")
        }
        
        binding.cardJardinagem.setOnClickListener {
            navigateToServiceCategory("desentupimento_manual", "Desentupimento manual")
        }
        
        binding.cardMudancas.setOnClickListener {
            navigateToServiceCategory("desentupimento_maquinario_2m", "Desentupimento com maquinário até 2 m")
        }
        
        binding.cardTecnologia.setOnClickListener {
            navigateToServiceCategory("caca_vazamentos", "Caça-vazamentos")
        }
        
        // Novos cards adicionados ao layout
        binding.cardArCondicionado.setOnClickListener {
            navigateToServiceCategory("ar_condicionado", "Ar condicionado")
        }
        binding.cardEletrodomesticos.setOnClickListener {
            navigateToServiceCategory("eletrodomesticos", "Eletrodomésticos")
        }
        binding.cardChaveiroResidencial.setOnClickListener {
            navigateToServiceCategory("chaveiro_residencial", "Chaveiro residencial")
        }
        binding.cardServicosAutomotivos.setOnClickListener {
            navigateToServiceCategory("servicos_automotivos", "Serviços automotivos")
        }
        binding.cardMontagemMoveis.setOnClickListener {
            navigateToServiceCategory("montagem_moveis", "Montagem de móveis")
        }
        binding.cardFaxina.setOnClickListener {
            navigateToServiceCategory("faxina", "Faxina")
        }
        // Botão popular dados (apenas para desenvolvimento)
        binding.btnPopulateData.setOnClickListener {
            populateSampleData()
        }
    }

    /**
     * Configura o listener de busca
     */
    private fun setupSearchListener() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                filterServiceCards()
            }
        })
    }

    /**
     * Filtra os cards e mostra resultados da busca com preços
     */
    private fun filterServiceCards() {
        val cards = listOf(
            binding.cardLimpeza to "Limpeza de estofados",
            binding.cardManutencao to "Encanador",
            binding.cardEletrica to "Elétrica",
            binding.cardEncanamento to "Instalação",
            binding.cardPintura to "Caixa d'água",
            binding.cardJardinagem to "Desentupimento manual",
            binding.cardMudancas to "Desentupimento com maquinário até 2 m",
            binding.cardTecnologia to "Caça-vazamentos",
            binding.cardArCondicionado to "Ar condicionado",
            binding.cardEletrodomesticos to "Eletrodomésticos",
            binding.cardChaveiroResidencial to "Chaveiro residencial",
            binding.cardServicosAutomotivos to "Serviços automotivos",
            binding.cardMontagemMoveis to "Montagem de móveis",
            binding.cardFaxina to "Faxina"
        )
        
        if (searchQuery.isEmpty()) {
            // Sem busca: mostrar grid de cards, esconder resultados
            cards.forEach { (card, _) ->
                card.visibility = View.VISIBLE
            }
            binding.rvSearchResults.visibility = View.GONE
            binding.serviceGrid.visibility = View.VISIBLE
            hideEmptyState()
        } else {
            // Com busca: usar busca inteligente
            val searchResults = ServiceSearchHelper.search(searchQuery)
            
            if (searchResults.isNotEmpty()) {
                // Achou serviços! Mostrar lista com preços
                showSearchResultsWithPrices(searchResults)
                
                // Cards ficam visíveis só da categoria encontrada
                val matchedCategories = searchResults.map { it.category }.toSet()
                cards.forEach { (card, name) ->
                    card.visibility = if (name in matchedCategories) View.VISIBLE else View.GONE
                }
                binding.serviceGrid.visibility = View.VISIBLE
                hideEmptyState()
            } else {
                // Fallback: busca por categoria
                val matchedCategory = ServiceSearchHelper.searchCategory(searchQuery)
                if (matchedCategory != null) {
                    // Mostrar todos os serviços da categoria com preços
                    showCategoryServicesWithPrices(matchedCategory)
                    
                    cards.forEach { (card, name) ->
                        card.visibility = if (name == matchedCategory) View.VISIBLE else View.GONE
                    }
                    binding.serviceGrid.visibility = View.VISIBLE
                    hideEmptyState()
                } else {
                    // Nada encontrado
                    binding.rvSearchResults.visibility = View.GONE
                    cards.forEach { (card, _) -> card.visibility = View.GONE }
                    binding.serviceGrid.visibility = View.GONE
                    showEmptyState()
                }
            }
        }
    }
    
    /**
     * Mostra os resultados da busca em formato de lista com preços
     */
    private fun showSearchResultsWithPrices(results: List<ServiceSearchHelper.SearchResult>) {
        // Agrupar por categoria para evitar duplicatas
        val seen = mutableSetOf<String>()
        val serviceTypes = mutableListOf<ServiceType>()
        
        for (result in results) {
            val key = "${result.category}|${result.serviceType}"
            if (seen.add(key)) {
                val price = ServicePricing.getPrice(result.category, result.serviceType)
                serviceTypes.add(
                    ServiceType(
                        id = "search_${key.hashCode()}",
                        categoryId = result.category,
                        name = result.serviceType,
                        description = "Categoria: ${result.category}",
                        estimatedPrice = price ?: ServicePricing.getDefaultPrice(result.category),
                        isActive = true
                    )
                )
            }
        }
        
        setupResultsAdapter(serviceTypes)
        binding.rvSearchResults.visibility = View.VISIBLE
    }
    
    /**
     * Mostra todos os serviços de uma categoria com preços
     */
    private fun showCategoryServicesWithPrices(category: String) {
        val serviceNames = ServicePricing.getServicesForCategory(category)
        val serviceTypes = serviceNames.mapIndexed { index, name ->
            val price = ServicePricing.getPrice(category, name)
            ServiceType(
                id = "cat_${category.hashCode()}_$index",
                categoryId = category,
                name = name,
                description = "Categoria: $category",
                estimatedPrice = price ?: ServicePricing.getDefaultPrice(category),
                isActive = true
            )
        }
        
        setupResultsAdapter(serviceTypes)
        binding.rvSearchResults.visibility = View.VISIBLE
    }
    
    /**
     * Configura o adapter de resultados com preços
     */
    private fun setupResultsAdapter(serviceTypes: List<ServiceType>) {
        searchResultsAdapter = ServiceTypesAdapter(
            serviceTypes = serviceTypes,
            onServiceClick = { serviceType ->
                // Navegar para CreateOrderActivity com o serviço selecionado
                val intent = Intent(this, CreateOrderActivity::class.java).apply {
                    putExtra("service_category_name", serviceType.categoryId)
                    putExtra("search_query", serviceType.name)
                }
                startActivity(intent)
            },
            onFavoriteClick = { _, _ -> /* sem ação de favorito na busca */ },
            serviceManager = serviceManager
        )
        binding.rvSearchResults.adapter = searchResultsAdapter
    }

    /**
     * Navega para uma categoria específica de serviço
     */
    private fun navigateToServiceCategory(categoryId: String, categoryName: String) {
        android.util.Log.d("ServicesActivity", "🔄 Navegando para categoria: $categoryName ($categoryId)")
        
        // Por enquanto, navegar diretamente para criação de pedido com a categoria pré-selecionada
        val intent = Intent(this, CreateOrderActivity::class.java).apply {
            putExtra("service_niche", categoryId)
            putExtra("service_category_name", categoryName)
        }
        startActivity(intent)
        
        showToast("📋 Categoria selecionada: $categoryName")
    }

    /**
     * Popula dados de exemplo se necessário
     */
    private fun populateSampleDataIfNeeded() {
        // Por enquanto, não precisamos popular dados automaticamente
        // Os cards já estão funcionando com navegação direta
        android.util.Log.d("ServicesActivity", "✅ Tela de serviços carregada com sucesso")
    }

    /**
     * Popula dados de exemplo (apenas para desenvolvimento)
     */
    private fun populateSampleData() {
        lifecycleScope.launch {
            try {
                setLoadingState(true)
                showToast("🔄 Populando dados de exemplo...")
                
                serviceManager.populateSampleData()
                
                setLoadingState(false)
                showSuccessMessage("✅ Dados de exemplo criados com sucesso!")
                
            } catch (e: Exception) {
                setLoadingState(false)
                showErrorMessage("Erro ao popular dados: ${e.message}")
            }
        }
    }

    /**
     * Controla o estado de carregamento
     */
    private fun setLoadingState(loading: Boolean) {
        isLoading = loading
        if (loading) {
            showLoadingState()
        } else {
            hideLoadingState()
        }
    }

    /**
     * Mostra estado de carregamento
     */
    private fun showLoadingState() {
        binding.loadingState.visibility = View.VISIBLE
    }

    /**
     * Esconde estado de carregamento
     */
    private fun hideLoadingState() {
        binding.loadingState.visibility = View.GONE
    }

    /**
     * Mostra estado vazio
     */
    private fun showEmptyState() {
        binding.emptyState.visibility = View.VISIBLE
    }

    /**
     * Esconde estado vazio
     */
    private fun hideEmptyState() {
        binding.emptyState.visibility = View.GONE
    }

    /**
     * Exibe uma mensagem de sucesso
     */
    private fun showSuccessMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Exibe uma mensagem de erro
     */
    private fun showErrorMessage(message: String) {
        Toast.makeText(this, "❌ $message", Toast.LENGTH_LONG).show()
    }

    /**
     * Exibe uma mensagem toast
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
