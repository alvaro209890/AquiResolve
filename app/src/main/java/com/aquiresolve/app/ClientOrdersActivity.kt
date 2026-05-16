package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aquiresolve.app.adapters.DetailedOrdersAdapter
import com.aquiresolve.app.adapters.OrdersViewPagerAdapter
import com.aquiresolve.app.databinding.ActivityClientOrdersBinding
import com.aquiresolve.app.models.OrderData
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * ClientOrdersActivity - Tela de acompanhamento de pedidos do cliente
 * 
 * Funcionalidades:
 * - Lista todos os pedidos do cliente organizados em 4 abas
 * - Estatísticas de pedidos
 * - Ações específicas por status
 */
class ClientOrdersActivity : AppCompatActivity() {

    companion object {
        private const val RATING_REQUEST_CODE = 1001
    }

    // ViewBinding para acesso aos elementos da interface
    private lateinit var binding: ActivityClientOrdersBinding
    
    // Variáveis para controle de estado
    private var isLoading = false
    private var allOrders = listOf<OrderData>()
    
    // Firebase
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var ordersListener: ListenerRegistration? = null
    private lateinit var orderManager: FirebaseOrderManager
    
    // ViewPager e Adapter
    private lateinit var viewPagerAdapter: OrdersViewPagerAdapter
    private val fragments = mutableListOf<OrdersTabFragment>()
    private lateinit var ordersViewModel: OrdersViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar ViewBinding
        binding = ActivityClientOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicializar managers
        orderManager = FirebaseOrderManager()
        ordersViewModel = androidx.lifecycle.ViewModelProvider(this)[OrdersViewModel::class.java]
        
        // Configurar a interface
        setupUI()
        setupClickListeners()
        setupViewPager()
        
        // Verificar se deve abrir em uma aba específica
        val openTab = intent.getStringExtra("open_tab")
        if (openTab == "distributing") {
            // Abrir na aba "Em Distribuição" (índice 1)
            binding.viewPager.post {
                binding.viewPager.setCurrentItem(1, false)
            }
        }
        
        // Carregar dados
        loadOrders()
        
        // Configurar listener em tempo real
        setupRealtimeListener()
    }

    /**
     * Configura os elementos da interface do usuário
     */
    private fun setupUI() {
        // Configurar a status bar
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)
    }

    /**
     * Configura os listeners de clique para todos os elementos interativos
     */
    private fun setupClickListeners() {
        // Botão voltar
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // Botão filtro
        binding.btnFilter.setOnClickListener {
            showFilterDialog()
        }
        
        // FAB removido: pedidos só podem ser feitos pela aba Serviços
        binding.fabNewOrder.visibility = View.GONE
    }

    /**
     * Configura o ViewPager2 e TabLayout
     */
    private fun setupViewPager() {
        android.util.Log.d("ClientOrders", "🔧 Configurando ViewPager2 e TabLayout")
        
        // Configurar ViewPager2
        viewPagerAdapter = OrdersViewPagerAdapter(this, isProviderContext = false)
        binding.viewPager.adapter = viewPagerAdapter
        
        android.util.Log.d("ClientOrders", "📱 ViewPager2 configurado com ${viewPagerAdapter.itemCount} itens")
        
        // Configurar TabLayout
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabName = when (position) {
                0 -> "Em Andamento"
                1 -> "Em Distribuição"
                2 -> "Concluídos"
                3 -> "Cancelados"
                else -> ""
            }
            tab.text = tabName
            android.util.Log.d("ClientOrders", "🏷️ Tab $position configurado: $tabName")
        }.attach()
        
        android.util.Log.d("ClientOrders", "✅ TabLayout configurado")
        
        // Obter referências dos fragments após a configuração
        binding.viewPager.post {
            android.util.Log.d("ClientOrders", "🔍 Buscando fragments criados...")
            for (i in 0 until viewPagerAdapter.itemCount) {
                val fragment = supportFragmentManager.findFragmentByTag("f$i") as? OrdersTabFragment
                fragment?.let { 
                    android.util.Log.d("ClientOrders", "📱 Fragment $i encontrado: ${it.tabType}")
                    if (!fragments.contains(it)) {
                        fragments.add(it)
                        android.util.Log.d("ClientOrders", "➕ Fragment ${it.tabType} adicionado à lista")
                    }
                } ?: android.util.Log.w("ClientOrders", "⚠️ Fragment $i não encontrado")
            }
            android.util.Log.d("ClientOrders", "📊 Total de fragments na lista: ${fragments.size}")
        }
    }

    /**
     * Carrega os pedidos do cliente
     */
    private fun loadOrders() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showErrorMessage("Usuário não autenticado")
            return
        }
        
        android.util.Log.d("ClientOrders", "🔄 Carregando pedidos para usuário: ${currentUser.uid}")
        setLoadingState(true)
        
        lifecycleScope.launch {
            try {
                // Buscar pedidos do Firebase (SEM orderBy para evitar índice composto)
                val snapshot = db.collection("orders")
                    .whereEqualTo("clientId", currentUser.uid)
                    .get()
                    .await()
                
                android.util.Log.d("ClientOrders", "📋 Documentos encontrados: ${snapshot.documents.size}")
                
                allOrders = snapshot.documents.mapNotNull { doc ->
                    try {
                        val order = doc.toObject(OrderData::class.java)?.copy(id = doc.id)
                        android.util.Log.d("ClientOrders", "📄 Pedido carregado: ${order?.id} - Status: ${order?.status}")
                        order
                    } catch (e: Exception) {
                        android.util.Log.e("ClientOrders", "❌ Erro ao carregar pedido ${doc.id}: ${e.message}")
                        null
                    }
                }
                
                // Ordenar manualmente por createdAt (DESCENDING)
                allOrders = allOrders.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                
                android.util.Log.d("ClientOrders", "✅ Total de pedidos carregados: ${allOrders.size}")
                android.util.Log.d("ClientOrders", "📊 Status dos pedidos: ${allOrders.map { it.status }}")
                
                updateStatistics()
                // Publicar pedidos no ViewModel para que as abas atualizem sozinhas
                ordersViewModel.setOrders(allOrders)
                updateAllFragments()
                setLoadingState(false)
                
                // Mostrar mensagem de sucesso se houver pedidos
                if (allOrders.isNotEmpty()) {
                    showSuccessMessage("📋 ${allOrders.size} pedido(s) carregado(s)")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("ClientOrders", "❌ Erro ao carregar pedidos: ${e.message}")
                android.util.Log.e("ClientOrders", "Stack trace: ${e.stackTraceToString()}")
                setLoadingState(false)
                showErrorMessage("Erro ao carregar pedidos: ${e.message}")
            }
        }
    }

    /**
     * Atualiza as estatísticas
     */
    private fun updateStatistics() {
        val activeOrders = allOrders.count { 
            it.status == OrderData.STATUS_IN_PROGRESS || 
            it.status == OrderData.STATUS_ASSIGNED ||
            it.status == OrderData.STATUS_DISTRIBUTING ||
            it.status == OrderData.STATUS_AWAITING_PAYMENT ||
            it.status == OrderData.STATUS_PENDING
        }
        
        val completedOrders = allOrders.count { 
            it.status == OrderData.STATUS_COMPLETED 
        }
        
        val totalOrders = allOrders.size
        
        binding.tvActiveOrders.text = activeOrders.toString()
        binding.tvCompletedOrders.text = completedOrders.toString()
        binding.tvTotalOrders.text = totalOrders.toString()
    }

    /**
     * Atualiza todos os fragments com os novos dados
     */
    private fun updateAllFragments() {
        android.util.Log.d("ClientOrders", "🔄 Atualizando ${fragments.size} fragments")
        android.util.Log.d("ClientOrders", "📊 Total de pedidos para distribuir: ${allOrders.size}")
        android.util.Log.d("ClientOrders", "📋 Status dos pedidos: ${allOrders.map { it.status }}")
        
        fragments.forEachIndexed { index, fragment ->
            android.util.Log.d("ClientOrders", "📱 Atualizando fragment $index: ${fragment.tabType}")
            fragment.updateOrders(allOrders)
        }
        
        android.util.Log.d("ClientOrders", "✅ Todos os fragments atualizados")
    }

    /**
     * Configura listener em tempo real para atualizações
     */
    private fun setupRealtimeListener() {
        val currentUser = auth.currentUser ?: return
        
        ordersListener?.remove() // Remove listener anterior se existir
        ordersListener = db.collection("orders")
            .whereEqualTo("clientId", currentUser.uid)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ClientOrders", "❌ Erro no listener: ${error.message}")
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    lifecycleScope.launch {
                        allOrders = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toObject(OrderData::class.java)?.copy(id = doc.id)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        
                        updateStatistics()
                        ordersViewModel.setOrders(allOrders)
                        updateAllFragments()
                        
                        android.util.Log.d("ClientOrders", "🔄 Dados atualizados em tempo real: ${allOrders.size} pedidos")
                    }
                }
            }
    }

    /**
     * Define o estado de carregamento
     */
    private fun setLoadingState(loading: Boolean) {
        isLoading = loading
        fragments.forEach { fragment ->
            fragment.setLoading(loading)
        }
    }

    /**
     * Mostra diálogo de filtros (mantido para compatibilidade)
     */
    private fun showFilterDialog() {
        Toast.makeText(this, "Filtros disponíveis nas abas acima", Toast.LENGTH_SHORT).show()
    }

    /**
     * Mostra mensagem de erro
     */
    private fun showErrorMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Mostra mensagem de sucesso
     */
    private fun showSuccessMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == RATING_REQUEST_CODE && resultCode == RESULT_OK) {
            // Recarregar pedidos após avaliação
            loadOrders()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ordersListener?.remove()
        ordersListener = null
    }
}
