package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aquiresolve.app.adapters.ProviderOrdersAdapter
import com.aquiresolve.app.databinding.FragmentProviderOrdersBinding
import com.aquiresolve.app.models.OrderData
import com.aquiresolve.app.utils.NewOrderSoundHelper
import com.aquiresolve.app.utils.ServiceNicheCatalog
import com.aquiresolve.app.utils.TowingDispatch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ProviderOrdersFragment - Fragment para exibir pedidos do prestador
 */
class ProviderOrdersFragment : Fragment() {

    private var _binding: FragmentProviderOrdersBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var ordersAdapter: ProviderOrdersAdapter
    private val ordersList = mutableListOf<OrderData>()
    private val allOrdersList = mutableListOf<OrderData>() // Lista completa para filtros
    
    // Firebase
    private val firestore = FirebaseFirestore.getInstance()
    private var ordersListener: com.google.firebase.firestore.ListenerRegistration? = null
    
    // Estado atual da tab
    private var currentTab = 0 // 0: Disponíveis, 1: Aceitos, 2: Concluídos, 3: Histórico
    private val knownAvailableOrderIds = mutableSetOf<String>()
    private var providerServices = emptyList<String>()
    private var providerServicesNormalized = emptySet<String>()

    // Localização do prestador (para o dispatch por raio do guincho) e ticker que
    // reavalia o raio expansivo ao longo do tempo enquanto a tela está visível.
    private var providerLocation: GeoPoint? = null
    private val dispatchHandler = Handler(Looper.getMainLooper())
    private val dispatchTicker = object : Runnable {
        override fun run() {
            if (currentTab == 0) applyTabFilter(notify = false)
            dispatchHandler.postDelayed(this, DISPATCH_REFRESH_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProviderOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        setupTabs()
        loadOrders()
        // setupRealtimeListener() será chamado dentro de loadOrders() apenas se prestador estiver aprovado
    }

    override fun onResume() {
        super.onResume()
        ProviderNewOrderAlertManager.refreshMonitoring()
        // Reavalia o raio do guincho periodicamente (expansão a cada 4 min).
        dispatchHandler.removeCallbacks(dispatchTicker)
        dispatchHandler.postDelayed(dispatchTicker, DISPATCH_REFRESH_MS)
    }

    override fun onPause() {
        super.onPause()
        dispatchHandler.removeCallbacks(dispatchTicker)
    }

    /**
     * Configura o RecyclerView para exibir os pedidos
     */
    private fun setupRecyclerView() {
        ordersAdapter = ProviderOrdersAdapter(
            orders = ordersList,
            onOrderClick = { order ->
                showOrderDetails(order)
            },
            onRejectOrder = { order ->
                rejectOrder(order)
            }
        )
        
        binding.recyclerViewOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ordersAdapter
        }
    }

    /**
     * Configura as tabs de filtro
     */
    private fun setupTabs() {
        binding.tabLayoutOrders.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.let {
                    currentTab = it.position
                    filterOrdersByTab()
                }
            }
            
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }
    
    /**
     * Filtra pedidos baseado na tab selecionada
     */
    private fun filterOrdersByTab() {
        // VERIFICAR STATUS DE VERIFICAÇÃO ANTES DE FILTRAR
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            lifecycleScope.launch {
                val verificationManager = ProviderVerificationManager()
                val verificationStatus = verificationManager.getVerificationStatus(currentUser.uid)
                
                // BLOQUEAR FILTRO SE NÃO ESTIVER APROVADO
                if (verificationStatus == null || verificationStatus.status != ProviderVerificationManager.VerificationStatus.APPROVED) {
                    ordersList.clear()
                    ordersAdapter.notifyDataSetChanged()
                    updateEmptyState()
                    return@launch
                }
                
                // Se aprovado, aplicar filtro normalmente
                applyTabFilter()
            }
        } else {
            ordersList.clear()
            ordersAdapter.notifyDataSetChanged()
            updateEmptyState()
        }
    }
    
    /**
     * Aplica o filtro da tab atual (chamado apenas se prestador estiver aprovado)
     */
    private fun applyTabFilter(notify: Boolean = true) {
        ordersList.clear()

        when (currentTab) {
            0 -> { // Disponíveis
                // Guincho só aparece para guincheiros dentro do raio de dispatch atual
                // (10 km + 5 km a cada 4 min). Demais serviços não são afetados.
                ordersList.addAll(allOrdersList.filter {
                    (it.status == "pending" || it.status == "available" || it.status == "distributing") &&
                        TowingDispatch.canOfferToProvider(it, providerLocation)
                })
            }
            1 -> { // Aceitos
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                ordersList.addAll(allOrdersList.filter {
                    (it.status == "assigned" || it.status == "in_progress") && it.assignedProvider == currentUserId
                })
            }
            2 -> { // Concluídos
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                ordersList.addAll(allOrdersList.filter { it.status == "completed" && it.assignedProvider == currentUserId })
            }
            3 -> { // Histórico (todos)
                ordersList.addAll(allOrdersList)
            }
        }
        
        ordersAdapter.notifyDataSetChanged()
        updateEmptyState()
        updateStatistics()

        if (notify) {
            val tabNames = listOf("Disponíveis", "Aceitos", "Concluídos", "Histórico")
            showToast("📋 Mostrando: ${tabNames[currentTab]} (${ordersList.size} pedidos)")
        }
    }

    /**
     * Configura os listeners de clique
     */
    private fun setupClickListeners() {
        binding.btnRefresh.setOnClickListener {
            loadOrders()
        }
        
        binding.btnFilters.setOnClickListener {
            showFiltersDialog()
        }
        
        binding.btnSort.setOnClickListener {
            showSortDialog()
        }
        
        binding.btnMap.setOnClickListener {
            showOrdersMap()
        }
        
        binding.btnSearch.setOnClickListener {
            performSearch()
        }
        
        // Buscar ao pressionar Enter
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }
    }
    
    /**
     * Realiza a busca nos pedidos
     */
    private fun performSearch() {
        val searchQuery = binding.etSearch.text.toString().trim()
        if (searchQuery.isEmpty()) {
            loadOrders() // Carregar todos se busca vazia
            return
        }
        
        // VERIFICAR STATUS DE VERIFICAÇÃO ANTES DE BUSCAR
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            showToast("❌ Usuário não autenticado")
            return
        }
        
        lifecycleScope.launch {
            try {
                // Verificar status de verificação
                val verificationManager = ProviderVerificationManager()
                val verificationStatus = verificationManager.getVerificationStatus(currentUser.uid)
                
                // BLOQUEAR BUSCA SE NÃO ESTIVER APROVADO
                if (verificationStatus == null || verificationStatus.status != ProviderVerificationManager.VerificationStatus.APPROVED) {
                    showToast("⏳ Você precisa estar verificado para buscar pedidos")
                    return@launch
                }
                
                setLoadingState(true)
                
                // Buscar pedidos que correspondem aos serviços do prestador
                val query = firestore.collection("orders")
                    .whereIn("status", listOf("pending", "available", "distributing", "assigned", "in_progress", "completed"))
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(100)
                
                val snapshot = query.get().await()
                ordersList.clear()
                
                for (document in snapshot.documents) {
                    try {
                        val order = document.toObject(OrderData::class.java)?.copy(id = document.id)
                        if (order != null) {
                            val hasMatchingService = shouldIncludeOrderForProvider(order, currentUser.uid)

                            if (hasMatchingService) {
                                // Filtrar por texto de busca
                                val matchesSearch = order.description.contains(searchQuery, ignoreCase = true) ||
                                                  order.clientName.contains(searchQuery, ignoreCase = true) ||
                                                  order.address.contains(searchQuery, ignoreCase = true) ||
                                                  order.serviceType.contains(searchQuery, ignoreCase = true)
                                
                                if (matchesSearch) {
                                    ordersList.add(order)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ProviderOrders", "Erro ao processar pedido: ${e.message}")
                    }
                }
                
                ordersAdapter.notifyDataSetChanged()
                updateEmptyState()
                updateStatistics()
                
                if (ordersList.isEmpty()) {
                    showToast("🔍 Nenhum pedido encontrado para: $searchQuery")
                } else {
                    showToast("🔍 ${ordersList.size} pedido(s) encontrado(s)")
                }
                
            } catch (e: Exception) {
                showToast("❌ Erro ao buscar pedidos: ${e.message}")
                android.util.Log.e("ProviderOrders", "Erro ao buscar pedidos", e)
            } finally {
                setLoadingState(false)
            }
        }
    }

    /**
     * Carrega os pedidos disponíveis para o prestador
     */
    private fun loadOrders() {
        setLoadingState(true)
        
        lifecycleScope.launch {
            try {
                // Verificar se o usuário é prestador
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    showToast("❌ Usuário não autenticado")
                    return@launch
                }
                
                // Garantir Firebase inicializado
                if (!FirebaseConfig.isInitialized()) {
                    FirebaseConfig.initialize(requireContext())
                }
                
                val authManager = FirebaseAuthManager(requireContext())
                val userData = authManager.getLocalUserData()
                if (userData?.userType != FirebaseAuthManager.USER_TYPE_PROVIDER) {
                    showToast("❌ Acesso restrito a prestadores")
                    return@launch
                }
                
                // Verificar status de verificação do prestador na coleção providers (verificationStatus)
                val verificationManager = ProviderVerificationManager()
                val verificationStatus = verificationManager.getVerificationStatus(currentUser.uid)
                
                android.util.Log.d("ProviderOrdersFragment", "🔍 Verificando status do prestador: ${currentUser.uid}")
                android.util.Log.d("ProviderOrdersFragment", "📊 Status encontrado: ${verificationStatus?.status}")
                
                // Verificar se está APROVADO conforme o Firestore (como no print)
                if (verificationStatus == null) {
                    android.util.Log.w("ProviderOrdersFragment", "❌ Nenhuma verificação encontrada para o prestador")
                    showDocumentsPendingMessage(ProviderVerificationManager.VerificationStatus.PENDING)
                    return@launch
                }
                
                when (verificationStatus.status) {
                    ProviderVerificationManager.VerificationStatus.APPROVED -> {
                        android.util.Log.d("ProviderOrdersFragment", "✅ Prestador APROVADO - Carregando pedidos")
                        // Prestador aprovado - carregar pedidos normalmente
                    }
                    ProviderVerificationManager.VerificationStatus.PENDING -> {
                        android.util.Log.w("ProviderOrdersFragment", "⏳ Documentos pendentes")
                        showDocumentsPendingMessage(ProviderVerificationManager.VerificationStatus.PENDING)
                        // Limpar lista e remover listener
                        allOrdersList.clear()
                        ordersList.clear()
                        knownAvailableOrderIds.clear()
                        ordersAdapter.notifyDataSetChanged()
                        ordersListener?.remove()
                        ordersListener = null
                        return@launch
                    }
                    ProviderVerificationManager.VerificationStatus.UNDER_REVIEW -> {
                        android.util.Log.w("ProviderOrdersFragment", "🔍 Verificação em análise")
                        showDocumentsPendingMessage(ProviderVerificationManager.VerificationStatus.UNDER_REVIEW)
                        // Limpar lista e remover listener
                        allOrdersList.clear()
                        ordersList.clear()
                        knownAvailableOrderIds.clear()
                        ordersAdapter.notifyDataSetChanged()
                        ordersListener?.remove()
                        ordersListener = null
                        return@launch
                    }
                    ProviderVerificationManager.VerificationStatus.REJECTED -> {
                        android.util.Log.w("ProviderOrdersFragment", "❌ Verificação rejeitada")
                        showDocumentsPendingMessage(ProviderVerificationManager.VerificationStatus.REJECTED)
                        // Limpar lista e remover listener
                        allOrdersList.clear()
                        ordersList.clear()
                        knownAvailableOrderIds.clear()
                        ordersAdapter.notifyDataSetChanged()
                        ordersListener?.remove()
                        ordersListener = null
                        return@launch
                    }
                    ProviderVerificationManager.VerificationStatus.EXPIRED -> {
                        android.util.Log.w("ProviderOrdersFragment", "⏰ Verificação expirada")
                        showDocumentsPendingMessage(ProviderVerificationManager.VerificationStatus.EXPIRED)
                        // Limpar lista e remover listener
                        allOrdersList.clear()
                        ordersList.clear()
                        knownAvailableOrderIds.clear()
                        ordersAdapter.notifyDataSetChanged()
                        ordersListener?.remove()
                        ordersListener = null
                        return@launch
                    }
                }
                
                providerServices = loadProviderServices(currentUser.uid)
                providerServicesNormalized = ServiceNicheCatalog.normalizeProviderServices(providerServices)
                providerLocation = loadProviderLocation(currentUser.uid)

                android.util.Log.d("ProviderOrders", "🔍 Carregando pedidos para prestador: ${currentUser.uid}")
                android.util.Log.d("ProviderOrders", "🧰 Nichos oferecidos: $providerServices")
                
                // Buscar pedidos com todos os status relevantes
                val query = firestore.collection("orders")
                    .whereIn("status", listOf("pending", "available", "distributing", "assigned", "in_progress", "completed"))
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(100)
                
                val snapshot = query.get().await()
                allOrdersList.clear()
                ordersList.clear()
                
                var totalOrders = 0
                var filteredOrders = 0
                
                for (document in snapshot.documents) {
                    try {
                        val order = document.toObject(OrderData::class.java)?.copy(id = document.id)
                        if (order != null) {
                            totalOrders++
                            
                            val hasMatchingService = shouldIncludeOrderForProvider(order, currentUser.uid)

                            if (hasMatchingService) {
                                allOrdersList.add(order)
                                filteredOrders++
                                android.util.Log.d("ProviderOrders", "✅ Pedido aceito: ${order.serviceType} - ${order.description}")
                            } else {
                                android.util.Log.d("ProviderOrders", "❌ Pedido rejeitado: ${order.serviceType} (não oferecido pelo prestador)")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ProviderOrders", "Erro ao processar pedido: ${e.message}")
                    }
                }
                
                android.util.Log.d("ProviderOrders", "📊 Total de pedidos: $totalOrders, Filtrados: $filteredOrders")

                // Baseline dos pedidos disponíveis para detectar somente novos IDs.
                knownAvailableOrderIds.clear()
                knownAvailableOrderIds.addAll(
                    allOrdersList
                        .filter { isAvailableStatus(it.status) }
                        .map { it.id }
                )
                
                // Aplicar filtro da tab atual (já verifica status dentro)
                applyTabFilter()
                
                // Configurar listener em tempo real APENAS se aprovado
                setupRealtimeListener()
                
            } catch (e: Exception) {
                showToast("❌ Erro ao carregar pedidos: ${e.message}")
                android.util.Log.e("ProviderOrders", "Erro ao carregar pedidos", e)
            } finally {
                setLoadingState(false)
            }
        }
    }

    /**
     * Mostra detalhes de um pedido
     */
    private fun showOrderDetails(order: OrderData) {
        val intent = Intent(requireContext(), OrderDetailsActivity::class.java)
        intent.putExtra("order_id", order.id)
        intent.putExtra("is_provider_view", true)
        startActivity(intent)
    }

    /**
     * Aceita um pedido
     */
    private fun acceptOrder(order: OrderData) {
        if (order.coordinates == null) {
            showToast("❌ Pedido sem localização no mapa. O cliente precisa corrigir o endereço.")
            return
        }

        // Guincho: só pode aceitar quem está dentro do raio de dispatch atual.
        if (TowingDispatch.isTowingOrder(order) && !TowingDispatch.canOfferToProvider(order, providerLocation)) {
            val raio = TowingDispatch.currentRadiusKm(order).toInt()
            showToast("📍 Este guincho ainda está sendo oferecido a guincheiros mais próximos (raio atual ${raio} km). Aguarde a ampliação.")
            return
        }

        lifecycleScope.launch {
            if (!FirebaseConfig.isInitialized()) {
                FirebaseConfig.initialize(requireContext())
            }

            val result = FirebaseOrderManager().acceptOrderAsProvider(order.id)
            if (result.isSuccess) {
                showToast("✅ Pedido aceito com sucesso!")
                if (!isAdded) return@launch

                ProviderLocationForegroundService.start(requireContext().applicationContext)

                val intent = Intent(requireContext(), OrderDetailsActivity::class.java).apply {
                    putExtra("order_id", order.id)
                    putExtra("is_provider_view", true)
                }
                startActivity(intent)
                loadOrders()
            } else {
                showToast("❌ Erro ao aceitar pedido: ${result.exceptionOrNull()?.message ?: "erro desconhecido"}")
            }
        }
    }

    /**
     * Recusa um pedido
     */
    private fun rejectOrder(order: OrderData) {
        lifecycleScope.launch {
            try {
                // Garantir Firebase inicializado
                if (!FirebaseConfig.isInitialized()) {
                    FirebaseConfig.initialize(requireContext())
                }
                
                val authManager = FirebaseAuthManager(requireContext())
                val user = authManager.getLocalUserData()
                if (user == null) {
                    showToast("❌ Usuário não encontrado")
                    return@launch
                }
                
                // Adicionar este prestador ao array rejectedBy do pedido.
                // Só 'rejectedBy' muda (arrayUnion do próprio uid) — não altera o status,
                // então a recusa apenas esconde o pedido DESTE prestador, sem cancelá-lo
                // para os demais. (Gravar campos extras quebraria validProviderRejectUpdate.)
                firestore.collection("orders")
                    .document(order.id)
                    .update(
                        "rejectedBy",
                        com.google.firebase.firestore.FieldValue.arrayUnion(user.uid)
                    )
                    .await()
                
                // Parar som contínuo se estiver tocando para este pedido
                NewOrderSoundHelper.stopSound(order.id)
                
                showToast("❌ Pedido recusado")
                loadOrders() // Recarregar lista
                
            } catch (e: Exception) {
                showToast("❌ Erro ao recusar pedido: ${e.message}")
                android.util.Log.e("ProviderOrders", "Erro ao recusar pedido", e)
            }
        }
    }

    /**
     * Mostra diálogo de filtros
     */
    private fun showFiltersDialog() {
        val filterOptions = arrayOf(
            "Todos os pedidos",
            "Apenas pendentes",
            "Apenas aceitos",
            "Apenas recusados",
            "Por serviço específico"
        )
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("🔧 Filtrar Pedidos")
        builder.setItems(filterOptions) { _, which ->
            when (which) {
                0 -> loadOrders() // Todos
                1 -> filterOrdersByStatus("pending")
                2 -> filterOrdersByStatus("accepted")
                3 -> filterOrdersByStatus("rejected")
                4 -> {
                    if (providerServices.isEmpty()) {
                        showToast("ℹ️ Você ainda não definiu nichos. Exibindo todos os pedidos.")
                    } else {
                        showServiceFilterDialog(providerServices)
                    }
                }
            }
        }
        builder.show()
    }
    
    /**
     * Filtra pedidos por status
     */
    private fun filterOrdersByStatus(status: String) {
        // VERIFICAR STATUS DE VERIFICAÇÃO ANTES DE FILTRAR
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            showToast("❌ Usuário não autenticado")
            return
        }
        
        lifecycleScope.launch {
            try {
                // Verificar status de verificação
                val verificationManager = ProviderVerificationManager()
                val verificationStatus = verificationManager.getVerificationStatus(currentUser.uid)
                
                // BLOQUEAR FILTRO SE NÃO ESTIVER APROVADO
                if (verificationStatus == null || verificationStatus.status != ProviderVerificationManager.VerificationStatus.APPROVED) {
                    showToast("⏳ Você precisa estar verificado para filtrar pedidos")
                    return@launch
                }
                
                val query = firestore.collection("orders")
                    .whereEqualTo("status", status)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                
                val snapshot = query.get().await()
                ordersList.clear()
                
                for (document in snapshot.documents) {
                    try {
                        val order = document.toObject(OrderData::class.java)?.copy(id = document.id)
                        if (order != null) {
                            val hasMatchingService = shouldIncludeOrderForProvider(order, currentUser.uid)
                            if (hasMatchingService) {
                                ordersList.add(order)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ProviderOrders", "Erro ao processar pedido: ${e.message}")
                    }
                }
                
                ordersAdapter.notifyDataSetChanged()
                updateEmptyState()
                updateStatistics()
                
            } catch (e: Exception) {
                showToast("❌ Erro ao filtrar pedidos: ${e.message}")
            }
        }
    }
    
    /**
     * Mostra diálogo para filtrar por serviço específico
     */
    private fun showServiceFilterDialog(services: List<String>) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("🔧 Filtrar por Serviço")
        builder.setItems(services.toTypedArray()) { _, which ->
            val selectedService = services[which]
            filterOrdersByService(selectedService)
        }
        builder.show()
    }
    
    /**
     * Filtra pedidos por serviço específico
     */
    private fun filterOrdersByService(serviceType: String) {
        val selectedServiceNormalized = ServiceNicheCatalog
            .normalizeProviderServices(listOf(serviceType))
            .firstOrNull()
            ?: return

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            ?: run {
                showToast("❌ Usuário não autenticado")
                return
            }

        lifecycleScope.launch {
            try {
                val query = firestore.collection("orders")
                    .whereIn("status", listOf("pending", "available", "distributing"))
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                
                val snapshot = query.get().await()
                ordersList.clear()
                
                for (document in snapshot.documents) {
                    try {
                        val order = document.toObject(OrderData::class.java)?.copy(id = document.id)
                        if (order != null) {
                            val matchesProvider = shouldIncludeOrderForProvider(order, currentUserId)
                            val matchesSelectedService = ServiceNicheCatalog.matchesProviderServices(
                                setOf(selectedServiceNormalized),
                                order
                            )
                            val withinDispatch = TowingDispatch.canOfferToProvider(order, providerLocation)
                            if (matchesProvider && matchesSelectedService && withinDispatch) {
                                ordersList.add(order)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ProviderOrders", "Erro ao processar pedido: ${e.message}")
                    }
                }
                
                ordersAdapter.notifyDataSetChanged()
                updateEmptyState()
                updateStatistics()
                
            } catch (e: Exception) {
                showToast("❌ Erro ao filtrar por serviço: ${e.message}")
            }
        }
    }
    
    /**
     * Mostra diálogo de ordenação
     */
    private fun showSortDialog() {
        val sortOptions = arrayOf(
            "Data (mais recentes primeiro)",
            "Data (mais antigos primeiro)",
            "Preço (maior primeiro)",
            "Preço (menor primeiro)",
            "Status (pendentes primeiro)",
            "Cliente (A-Z)",
            "Serviço (A-Z)"
        )
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("🔧 Ordenar Pedidos")
        builder.setItems(sortOptions) { _, which ->
            when (which) {
                0 -> sortOrdersByDate(true) // Mais recentes primeiro
                1 -> sortOrdersByDate(false) // Mais antigos primeiro
                2 -> sortOrdersByPrice(true) // Maior preço primeiro
                3 -> sortOrdersByPrice(false) // Menor preço primeiro
                4 -> sortOrdersByStatus() // Pendentes primeiro
                5 -> sortOrdersByClient() // Cliente A-Z
                6 -> sortOrdersByService() // Serviço A-Z
            }
        }
        builder.show()
    }
    
    /**
     * Ordena pedidos por data
     */
    private fun sortOrdersByDate(descending: Boolean) {
        ordersList.sortByDescending { it.createdAt.toDate() }
        if (!descending) {
            ordersList.reverse()
        }
        ordersAdapter.notifyDataSetChanged()
        showToast("📅 Pedidos ordenados por data")
    }
    
    /**
     * Ordena pedidos por preço
     */
    private fun sortOrdersByPrice(descending: Boolean) {
        ordersList.sortByDescending { it.estimatedPrice }
        if (!descending) {
            ordersList.reverse()
        }
        ordersAdapter.notifyDataSetChanged()
        showToast("💰 Pedidos ordenados por preço")
    }
    
    /**
     * Ordena pedidos por status
     */
    private fun sortOrdersByStatus() {
        val statusOrder = listOf("pending", "available", "distributing", "accepted", "rejected", "completed")
        ordersList.sortBy { statusOrder.indexOf(it.status) }
        ordersAdapter.notifyDataSetChanged()
        showToast("📊 Pedidos ordenados por status")
    }
    
    /**
     * Ordena pedidos por cliente
     */
    private fun sortOrdersByClient() {
        ordersList.sortBy { it.clientName }
        ordersAdapter.notifyDataSetChanged()
        showToast("👤 Pedidos ordenados por cliente")
    }
    
    /**
     * Ordena pedidos por serviço
     */
    private fun sortOrdersByService() {
        ordersList.sortBy { it.serviceType }
        ordersAdapter.notifyDataSetChanged()
        showToast("🔧 Pedidos ordenados por serviço")
    }
    
    /**
     * Mostra mapa com localização dos pedidos
     */
    private fun showOrdersMap() {
        if (ordersList.isEmpty()) {
            showToast("❌ Nenhum pedido para exibir no mapa")
            return
        }
        
        val ordersWithLocation = ordersList.filter { it.coordinates != null }
        if (ordersWithLocation.isEmpty()) {
            showToast("❌ Nenhum pedido possui localização para exibir no mapa")
            return
        }
        
        // Criar intent para abrir o mapa
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
        val uri = android.net.Uri.parse("geo:0,0?q=" + ordersWithLocation.first().coordinates?.latitude + "," + ordersWithLocation.first().coordinates?.longitude)
        intent.data = uri
        
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback: abrir no navegador
            val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            val webUri = android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=" + 
                ordersWithLocation.first().coordinates?.latitude + "," + 
                ordersWithLocation.first().coordinates?.longitude)
            webIntent.data = webUri
            startActivity(webIntent)
        }
        
        showToast("🗺️ Abrindo mapa com ${ordersWithLocation.size} pedido(s)")
    }

    /**
     * Atualiza o estado vazio da lista
     */
    private fun updateEmptyState() {
        if (ordersList.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.recyclerViewOrders.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.recyclerViewOrders.visibility = View.VISIBLE
        }
    }

    /**
     * Atualiza as estatísticas
     */
    private fun updateStatistics() {
        val total = ordersList.size
        val accepted = ordersList.count { it.status == "accepted" }
        val pending = ordersList.count { it.status == "pending" || it.status == "available" || it.status == "distributing" }
        val rejected = ordersList.count { it.status == "rejected" }
        val completed = ordersList.count { it.status == "completed" }
        
        // Calcular valor total dos pedidos aceitos
        val totalValue = ordersList
            .filter { it.status == "accepted" || it.status == "completed" }
            .sumOf { it.estimatedPrice }
        
        // Atualizar textos
        binding.tvTotalOrders.text = total.toString()
        binding.tvAcceptedOrders.text = accepted.toString()
        binding.tvPendingOrders.text = pending.toString()
        
        // Adicionar informações extras se disponíveis
        if (binding.root.findViewById<android.widget.TextView>(R.id.tvRejectedOrders) != null) {
            binding.root.findViewById<android.widget.TextView>(R.id.tvRejectedOrders).text = rejected.toString()
        }
        if (binding.root.findViewById<android.widget.TextView>(R.id.tvCompletedOrders) != null) {
            binding.root.findViewById<android.widget.TextView>(R.id.tvCompletedOrders).text = completed.toString()
        }
        if (binding.root.findViewById<android.widget.TextView>(R.id.tvTotalValue) != null) {
            binding.root.findViewById<android.widget.TextView>(R.id.tvTotalValue).text = "R$ ${String.format(java.util.Locale("pt", "BR"), "%.2f", totalValue)}"
        }
        
        android.util.Log.d("ProviderOrders", "📊 Estatísticas atualizadas - Total: $total, Aceitos: $accepted, Pendentes: $pending, Recusados: $rejected, Concluídos: $completed, Valor Total: R$ ${String.format("%.2f", totalValue)}")
    }

    /**
     * Controla o estado de carregamento da interface
     */
    private fun setLoadingState(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRefresh.isEnabled = !loading
        binding.btnFilters.isEnabled = !loading
    }

    /**
     * Exibe uma mensagem toast para o usuário
     */
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Configura listener em tempo real para novos pedidos
     */
    private fun setupRealtimeListener() {
        // VERIFICAR STATUS DE VERIFICAÇÃO ANTES DE CONFIGURAR LISTENER (dentro de coroutine)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            android.util.Log.w("ProviderOrders", "⚠️ Usuário não autenticado - não configurando listener")
            return
        }
        
        lifecycleScope.launch {
            val verificationManager = ProviderVerificationManager()
            val verificationStatus = verificationManager.getVerificationStatus(currentUser.uid)
            
            // SÓ CONFIGURAR LISTENER SE PRESTADOR ESTIVER APROVADO
            if (verificationStatus == null || verificationStatus.status != ProviderVerificationManager.VerificationStatus.APPROVED) {
                android.util.Log.w("ProviderOrders", "⚠️ Prestador não aprovado - não configurando listener de pedidos")
                // Remover listener anterior se existir
                ordersListener?.remove()
                ordersListener = null
                return@launch
            }
            
            // Remover listener anterior se existir
            ordersListener?.remove()
            
            android.util.Log.d("ProviderOrders", "✅ Prestador aprovado - configurando listener de pedidos")
            
            // Configurar novo listener
            ordersListener = firestore.collection("orders")
                .whereIn("status", listOf("pending", "available", "distributing", "assigned", "in_progress", "completed"))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ProviderOrders", "Erro no listener: ${error.message}")
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val newOrders = mutableListOf<OrderData>()
                    
                    for (document in snapshot.documents) {
                        try {
                            val order = document.toObject(OrderData::class.java)?.copy(id = document.id)
                            if (order != null) {
                                val hasMatchingService = shouldIncludeOrderForProvider(order, currentUser.uid)
                                if (hasMatchingService) {
                                    newOrders.add(order)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ProviderOrders", "Erro ao processar pedido: ${e.message}")
                        }
                    }
                    
                    val currentAvailableIds = newOrders
                        .filter { isAvailableStatus(it.status) }
                        .map { it.id }
                        .toSet()
                    val newOrderIds = currentAvailableIds - knownAvailableOrderIds
                    knownAvailableOrderIds.clear()
                    knownAvailableOrderIds.addAll(currentAvailableIds)
                    
                    // Atualizar lista completa
                    allOrdersList.clear()
                    allOrdersList.addAll(newOrders)
                    
                    // Aplicar filtro da tab atual (já verifica status dentro)
                    applyTabFilter()
                    
                    // Mostrar notificação de novos pedidos
                    if (newOrderIds.isNotEmpty()) {
                        showNewOrdersNotification(newOrderIds.size)
                    }
                }
            }
        }
    }
    
    /**
     * Mostra notificação de novos pedidos
     */
    private fun showNewOrdersNotification(count: Int) {
        val message = if (count == 1) {
            "🆕 Novo pedido disponível!"
        } else {
            "🆕 $count novos pedidos disponíveis!"
        }
        
        showToast(message)
        
        // Tocar som de alerta de novo pedido
        NewOrderSoundHelper.playNewOrderSound(requireContext())
        
        // Vibrar o dispositivo se possível
        try {
            val vibrator = requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        } catch (e: Exception) {
            android.util.Log.e("ProviderOrders", "Erro ao vibrar: ${e.message}")
        }
    }

    private suspend fun loadProviderServices(providerId: String): List<String> {
        return try {
            val providerDoc = firestore.collection("providers")
                .document(providerId)
                .get()
                .await()

            val rawServices = (providerDoc.get("services") as? List<*>)
                ?.mapNotNull { it as? String }
                ?: emptyList()

            ServiceNicheCatalog.canonicalizeProviderServices(rawServices)
        } catch (e: Exception) {
            android.util.Log.e("ProviderOrders", "Erro ao carregar serviços do prestador: ${e.message}")
            emptyList()
        }
    }

    /** Localização atual do prestador (mantida em `users/{uid}.coordinates` enquanto online). */
    private suspend fun loadProviderLocation(providerId: String): GeoPoint? {
        return try {
            firestore.collection("users").document(providerId).get().await()
                .getGeoPoint("coordinates")
        } catch (e: Exception) {
            android.util.Log.e("ProviderOrders", "Erro ao carregar localização do prestador: ${e.message}")
            null
        }
    }

    private fun shouldIncludeOrderForProvider(order: OrderData, providerId: String?): Boolean {
        val isAssignedToCurrentProvider = !providerId.isNullOrBlank() && order.assignedProvider == providerId
        if (isAssignedToCurrentProvider) {
            return true
        }

        // Excluir pedidos que este prestador já rejeitou
        if (!providerId.isNullOrBlank() && order.rejectedBy.contains(providerId)) {
            return false
        }

        return ServiceNicheCatalog.matchesProviderServices(providerServicesNormalized, order)
    }
    
    /**
     * Remove o listener em tempo real
     */
    private fun removeRealtimeListener() {
        ordersListener?.remove()
        ordersListener = null
        knownAvailableOrderIds.clear()
    }

    private fun isAvailableStatus(status: String): Boolean {
        return when (status.lowercase()) {
            "pending", "available", "distributing" -> true
            else -> false
        }
    }

    /**
     * Mostra mensagem quando documentos estão pendentes
     */
    private fun showDocumentsPendingMessage(status: ProviderVerificationManager.VerificationStatus?) {
        setLoadingState(false)
        
        val message = when (status) {
            ProviderVerificationManager.VerificationStatus.PENDING -> {
                "📋 Para visualizar pedidos disponíveis, você precisa enviar seus documentos para verificação.\n\n" +
                "📄 Documentos necessários:\n" +
                "• Foto do rosto (selfie)\n" +
                "• RG (frente e verso) OU CNH (frente e verso)\n\n" +
                "⏱️ Após o envio, aguarde a aprovação da administração."
            }
            ProviderVerificationManager.VerificationStatus.UNDER_REVIEW -> {
                "⏳ Seus documentos estão sendo analisados pela administração.\n\n" +
                "📧 Você será notificado sobre o resultado em até 48 horas.\n\n" +
                "✅ Após a aprovação, você poderá visualizar e aceitar pedidos."
            }
            ProviderVerificationManager.VerificationStatus.REJECTED -> {
                "❌ Seus documentos foram rejeitados.\n\n" +
                "📋 Verifique as observações e envie novos documentos.\n\n" +
                "🔄 Após a correção, aguarde nova análise."
            }
            ProviderVerificationManager.VerificationStatus.EXPIRED -> {
                "⏰ Sua verificação expirou.\n\n" +
                "📄 É necessário enviar novos documentos para verificação.\n\n" +
                "🔄 Após o envio, aguarde a aprovação."
            }
            else -> {
                "📋 Para visualizar pedidos disponíveis, você precisa enviar seus documentos para verificação.\n\n" +
                "📄 Documentos necessários:\n" +
                "• Foto do rosto (selfie)\n" +
                "• RG (frente e verso) OU CNH (frente e verso)\n\n" +
                "⏱️ Após o envio, aguarde a aprovação da administração."
            }
        }
        
        // Mostrar mensagem no layout
        binding.apply {
            layoutDocumentsPending.visibility = View.VISIBLE
            tvEmptyMessage.text = message
            recyclerViewOrders.visibility = View.GONE
            tabLayoutOrders.visibility = View.GONE
            
            // Botão para ir para upload de documentos
            btnUploadDocuments.visibility = View.VISIBLE
            btnUploadDocuments.setOnClickListener {
                val intent = Intent(requireContext(), DocumentUploadActivity::class.java)
                startActivity(intent)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dispatchHandler.removeCallbacks(dispatchTicker)
        removeRealtimeListener()
        _binding = null
    }

    companion object {
        // Cadência de reavaliação do raio do guincho (degraus de 4 min).
        private const val DISPATCH_REFRESH_MS = 60_000L
    }
}
