package com.example.loginapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.loginapp.adapters.OrdersViewPagerAdapter
import com.example.loginapp.utils.NewOrderSoundHelper
import com.example.loginapp.utils.ServiceNicheCatalog
import com.example.loginapp.databinding.ActivityProviderOrdersBinding
import com.example.loginapp.models.OrderData
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * ProviderOrdersActivity - Tela de pedidos disponíveis para prestadores
 * 
 * Funcionalidades:
 * - Lista pedidos disponíveis para o prestador organizados em 4 abas
 * - Estatísticas de pedidos
 * - Ações de aceitar/enviar cotação
 */
class ProviderOrdersActivity : AppCompatActivity() {

    // ViewBinding para acesso aos elementos da interface
    private lateinit var binding: ActivityProviderOrdersBinding
    
    // Variáveis para controle de estado
    private var isLoading = false
    private var allOrders = listOf<OrderData>()
    
    // Firebase
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var authManager: FirebaseAuthManager
    
    // ViewPager e Adapter
    private lateinit var viewPagerAdapter: OrdersViewPagerAdapter
    private val fragments = mutableListOf<OrdersTabFragment>()
    private val ordersViewModel: OrdersViewModel by viewModels()
    
    // Listeners em tempo real
    private var assignedOrdersListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var availableOrdersListener: com.google.firebase.firestore.ListenerRegistration? = null
    
    // IDs de pedidos disponíveis já conhecidos (detecção robusta de novos pedidos)
    private val knownAvailableOrderIds = mutableSetOf<String>()
    private var providerServices = emptyList<String>()
    private var providerServicesNormalized = emptySet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Garantir Firebase inicializado
        if (!FirebaseConfig.isInitialized()) {
            FirebaseConfig.initialize(this)
        }
        
        // Inicializar ViewBinding
        binding = ActivityProviderOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicializar managers
        authManager = FirebaseAuthManager(this)
        
        // Configurar a interface
        setupUI()
        setupClickListeners()
        setupViewPager()
        
        // Carregar dados (setupRealtimeListener será chamado dentro de loadOrders apenas se aprovado)
        loadOrders()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Remover listeners ao destruir a activity
        removeRealtimeListeners()
    }

    override fun onResume() {
        super.onResume()
        ProviderNewOrderAlertManager.refreshMonitoring()
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
        
        // Botão refresh
        binding.btnRefresh.setOnClickListener {
            loadOrders()
        }
    }

    /**
     * Configura o ViewPager2 e TabLayout
     */
    private fun setupViewPager() {
        // Configurar ViewPager2
        viewPagerAdapter = OrdersViewPagerAdapter(this, isProviderContext = true)
        binding.viewPager.adapter = viewPagerAdapter
        
        // Configurar TabLayout
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            // Prestador: 0 Disponíveis, 1 Aceitos, 2 Concluídos
            tab.text = when (position) {
                0 -> "Disponíveis"
                1 -> "Aceitos"
                2 -> "Concluídos"
                else -> ""
            }
        }.attach()
        
        // Obter referências dos fragments após a configuração
        // Atualizar fragments via ViewModel (substitui coleta manual por tag)
        binding.viewPager.post {
            ordersViewModel.setOrders(allOrders)
        }
    }

    /**
     * Carrega os pedidos disponíveis para o prestador
     */
    private fun loadOrders() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showErrorMessage("Usuário não autenticado")
            return
        }
        
        android.util.Log.d("ProviderOrders", "🔄 Carregando pedidos para prestador: ${currentUser.uid}")
        setLoadingState(true)
        
        lifecycleScope.launch {
            try {
                // Verificar se o usuário é prestador
                val userData = authManager.getLocalUserData()
                if (userData?.userType != FirebaseAuthManager.USER_TYPE_PROVIDER) {
                    showErrorMessage("Acesso restrito a prestadores")
                    return@launch
                }
                
                // Verificar status de verificação do prestador
                val verificationManager = ProviderVerificationManager()
                val verificationStatus = verificationManager.getVerificationStatus(currentUser.uid)
                
                // BLOQUEAR ACESSO SE NÃO ESTIVER APROVADO
                if (verificationStatus == null || verificationStatus.status != ProviderVerificationManager.VerificationStatus.APPROVED) {
                    setLoadingState(false)
                    // Remover listeners se não estiver aprovado
                    removeRealtimeListeners()
                    // Limpar lista de pedidos
                    allOrders = emptyList()
                    ordersViewModel.setOrders(emptyList())
                    updateStatistics()
                    // Mostrar mensagem apropriada
                    when (verificationStatus?.status) {
                        ProviderVerificationManager.VerificationStatus.PENDING -> {
                            showDocumentsPendingMessage(verificationStatus.status)
                        }
                        ProviderVerificationManager.VerificationStatus.UNDER_REVIEW -> {
                            showDocumentsPendingMessage(verificationStatus.status)
                        }
                        ProviderVerificationManager.VerificationStatus.REJECTED -> {
                            showDocumentsPendingMessage(verificationStatus.status)
                        }
                        ProviderVerificationManager.VerificationStatus.EXPIRED -> {
                            showDocumentsPendingMessage(verificationStatus.status)
                        }
                        else -> {
                            showDocumentsPendingMessage(null)
                        }
                    }
                    return@launch
                }

                providerServices = loadProviderServices(currentUser.uid)
                providerServicesNormalized = ServiceNicheCatalog.normalizeProviderServices(providerServices)

                // Buscar pedidos atribuídos AO prestador + pedidos disponíveis (distributing/pending)
                val assignedSnap = db.collection("orders")
                    .whereEqualTo("assignedProvider", currentUser.uid)
                    .get()
                    .await()

                val availableSnap = db.collection("orders")
                    .whereIn("status", listOf(
                        OrderData.STATUS_DISTRIBUTING,
                        OrderData.STATUS_PENDING,
                        OrderData.STATUS_DISTRIBUTING.uppercase(),
                        OrderData.STATUS_PENDING.uppercase()
                    ))
                    .get()
                    .await()
                
                val assignedOrders = assignedSnap.documents.mapNotNull { doc ->
                    try {
                        val order = doc.toObject(OrderData::class.java)?.copy(id = doc.id)
                        android.util.Log.d("ProviderOrders", "📄 Pedido carregado: ${order?.id} - Status: ${order?.status}")
                        order
                    } catch (e: Exception) {
                        android.util.Log.e("ProviderOrders", "❌ Erro ao carregar pedido ${doc.id}: ${e.message}")
                        null
                    }
                }

                val availableOrders = availableSnap.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(OrderData::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) { null }
                }
                val filteredAvailableOrders = availableOrders.filter { order ->
                    ServiceNicheCatalog.matchesProviderServices(providerServicesNormalized, order)
                }

                // Merge e dedup por id
                val merged = (assignedOrders + filteredAvailableOrders)
                allOrders = merged.distinctBy { it.id }
                
                // Inicializar baseline dos IDs disponíveis para detectar somente entradas novas
                knownAvailableOrderIds.clear()
                knownAvailableOrderIds.addAll(filteredAvailableOrders.map { it.id })
                
                android.util.Log.d("ProviderOrders", "✅ Total de pedidos carregados: ${allOrders.size}")
                android.util.Log.d("ProviderOrders", "📊 Status dos pedidos: ${allOrders.map { it.status }}")
                
                updateStatistics()
                ordersViewModel.setOrders(allOrders)
                setLoadingState(false)
                
                // Configurar listener em tempo real APENAS se prestador estiver aprovado
                setupRealtimeListener()
                
                // Mostrar mensagem de sucesso se houver pedidos
                if (allOrders.isNotEmpty()) {
                    showSuccessMessage("📋 ${allOrders.size} pedido(s) carregado(s)")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("ProviderOrders", "❌ Erro ao carregar pedidos: ${e.message}")
                android.util.Log.e("ProviderOrders", "Stack trace: ${e.stackTraceToString()}")
                setLoadingState(false)
                showErrorMessage("Erro ao carregar pedidos: ${e.message}")
            }
        }
    }

    /**
     * Atualiza as estatísticas
     */
    private fun updateStatistics() {
        val acceptedOrders = allOrders.count { 
            val status = it.status.lowercase()
            status == OrderData.STATUS_ASSIGNED || 
            status == OrderData.STATUS_IN_PROGRESS
        }
        
        val completedOrders = allOrders.count { 
            it.status.lowercase() == OrderData.STATUS_COMPLETED 
        }

        binding.tvActiveOrders.text = acceptedOrders.toString()
        binding.tvCompletedOrders.text = completedOrders.toString()
        binding.tvTotalOrders.text = (acceptedOrders + completedOrders).toString()

        // Caso exista um campo dedicado para disponíveis no layout, poderemos atribuir aqui no futuro.
    }

    /**
     * Atualiza todos os fragments com os novos dados
     */
    private fun updateAllFragments() {
        fragments.forEach { fragment ->
            fragment.updateOrders(allOrders)
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
            layoutEmptyMessage.visibility = View.VISIBLE
            tvEmptyMessage.text = message
            viewPager.visibility = View.GONE
            tabLayout.visibility = View.GONE
            
            // Botão para ir para upload de documentos
            btnUploadDocuments.visibility = View.VISIBLE
            btnUploadDocuments.setOnClickListener {
                val intent = Intent(this@ProviderOrdersActivity, DocumentUploadActivity::class.java)
                startActivity(intent)
            }
        }
    }

    /**
     * Configura listener em tempo real para atualizações
     * IMPORTANTE: Só configura listeners se prestador estiver APROVADO
     */
    private fun setupRealtimeListener() {
        val currentUser = auth.currentUser ?: return
        
        // VERIFICAR STATUS DE VERIFICAÇÃO ANTES DE CONFIGURAR LISTENERS
        lifecycleScope.launch setup@{
            try {
                val verificationManager = ProviderVerificationManager()
                val verificationStatus = verificationManager.getVerificationStatus(currentUser.uid)
                
                // SÓ CONFIGURAR LISTENERS SE PRESTADOR ESTIVER APROVADO
                if (verificationStatus == null || verificationStatus.status != ProviderVerificationManager.VerificationStatus.APPROVED) {
                    android.util.Log.w("ProviderOrders", "⚠️ Prestador não aprovado - não configurando listeners de pedidos")
                    // Remover listeners anteriores se existirem
                    removeRealtimeListeners()
                    return@setup
                }
                
                android.util.Log.d("ProviderOrders", "✅ Prestador aprovado - configurando listeners de pedidos")
                
                // Remover listeners anteriores se existirem
                removeRealtimeListeners()

                // Listener 1: pedidos atribuídos ao prestador
                assignedOrdersListener = db.collection("orders")
                    .whereEqualTo("assignedProvider", currentUser.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            android.util.Log.e("ProviderOrders", "❌ Erro no listener (assigned): ${error.message}")
                            return@addSnapshotListener
                        }

                        lifecycleScope.launch assignedUpdate@{
                            // Verificar novamente o status antes de processar (pode ter mudado)
                            val currentVerificationStatus = verificationManager.getVerificationStatus(currentUser.uid)
                            if (currentVerificationStatus == null || currentVerificationStatus.status != ProviderVerificationManager.VerificationStatus.APPROVED) {
                                android.util.Log.w("ProviderOrders", "⚠️ Status mudou - ignorando atualização de pedidos")
                                return@assignedUpdate
                            }
                            
                            val assigned = snapshot?.documents?.mapNotNull { doc ->
                                try { doc.toObject(OrderData::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                            } ?: emptyList()

                            // Atualizar lista de pedidos atribuídos
                            val currentAssigned = assigned
                            
                            // Buscar pedidos disponíveis também
                            val availableSnap = db.collection("orders")
                                .whereIn("status", listOf(
                                    OrderData.STATUS_DISTRIBUTING,
                                    OrderData.STATUS_PENDING
                                ))
                                .get()
                                .await()
                            
                            val available = availableSnap.documents.mapNotNull { doc ->
                                try { doc.toObject(OrderData::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                            }
                            val filteredAvailable = available.filter { order ->
                                ServiceNicheCatalog.matchesProviderServices(providerServicesNormalized, order)
                            }

                            allOrders = (currentAssigned + filteredAvailable).distinctBy { it.id }
                            updateStatistics()
                            ordersViewModel.setOrders(allOrders)
                            android.util.Log.d("ProviderOrders", "🔄 Realtime update: ${allOrders.size} pedidos")
                        }
                    }

                // Listener 2: pedidos disponíveis (distributing/pending)
                availableOrdersListener = db.collection("orders")
                    .whereIn("status", listOf(
                        OrderData.STATUS_DISTRIBUTING,
                        OrderData.STATUS_PENDING,
                        OrderData.STATUS_DISTRIBUTING.uppercase(),
                        OrderData.STATUS_PENDING.uppercase()
                    ))
                    .addSnapshotListener { availSnap, availErr ->
                        if (availErr != null) {
                            android.util.Log.e("ProviderOrders", "❌ Erro no listener (available): ${availErr.message}")
                            return@addSnapshotListener
                        }

                        lifecycleScope.launch availableUpdate@{
                            // Verificar novamente o status antes de processar
                            val currentVerificationStatus = verificationManager.getVerificationStatus(currentUser.uid)
                            if (currentVerificationStatus == null || currentVerificationStatus.status != ProviderVerificationManager.VerificationStatus.APPROVED) {
                                android.util.Log.w("ProviderOrders", "⚠️ Status mudou - ignorando atualização de pedidos disponíveis")
                                return@availableUpdate
                            }
                            
                            val available = availSnap?.documents?.mapNotNull { doc ->
                                try { doc.toObject(OrderData::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                            } ?: emptyList()
                            val filteredAvailable = available.filter { order ->
                                ServiceNicheCatalog.matchesProviderServices(providerServicesNormalized, order)
                            }

                            // Detectar novos pedidos disponíveis por diferença de IDs
                            val currentAvailableIds = filteredAvailable.map { it.id }.toSet()
                            val newOrderIds = currentAvailableIds - knownAvailableOrderIds
                            if (newOrderIds.isNotEmpty()) {
                                val newCount = newOrderIds.size
                                NewOrderSoundHelper.playNewOrderSound(this@ProviderOrdersActivity)
                                val msg = if (newCount == 1) "🆕 Novo pedido disponível!" else "🆕 $newCount novos pedidos disponíveis!"
                                runOnUiThread { Toast.makeText(this@ProviderOrdersActivity, msg, Toast.LENGTH_SHORT).show() }
                            }
                            knownAvailableOrderIds.clear()
                            knownAvailableOrderIds.addAll(currentAvailableIds)

                            // Buscar pedidos atribuídos também
                            val assignedSnap = db.collection("orders")
                                .whereEqualTo("assignedProvider", currentUser.uid)
                                .get()
                                .await()
                            
                            val assigned = assignedSnap.documents.mapNotNull { doc ->
                                try { doc.toObject(OrderData::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                            }

                            allOrders = (assigned + filteredAvailable).distinctBy { it.id }
                            updateStatistics()
                            ordersViewModel.setOrders(allOrders)
                            android.util.Log.d("ProviderOrders", "🔄 Realtime update (available): ${allOrders.size} pedidos")
                        }
                    }
                    
            } catch (e: Exception) {
                android.util.Log.e("ProviderOrders", "❌ Erro ao configurar listeners: ${e.message}")
            }
        }
    }
    
    /**
     * Remove listeners em tempo real
     */
    private fun removeRealtimeListeners() {
        assignedOrdersListener?.remove()
        assignedOrdersListener = null
        availableOrdersListener?.remove()
        availableOrdersListener = null
        knownAvailableOrderIds.clear()
        android.util.Log.d("ProviderOrders", "🗑️ Listeners removidos")
    }

    private suspend fun loadProviderServices(providerId: String): List<String> {
        return try {
            val providerDoc = db.collection("providers")
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
    
    /**
     * Mostra mensagem de documentos pendentes
     */
    private fun showDocumentsPendingMessage(status: String) {
        setLoadingState(false)
        
        val message = when (status) {
            "pending" -> "⏳ Seus documentos estão sendo analisados pela equipe administrativa. Você receberá uma notificação quando forem aprovados."
            "rejected" -> "❌ Seus documentos foram rejeitados. Por favor, envie novamente documentos válidos e legíveis."
            else -> "📋 Envie seus documentos para começar a receber pedidos."
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Documentos Pendentes")
            .setMessage(message)
            .setPositiveButton("Enviar Documentos") { _, _ ->
                val intent = Intent(this, ProviderDocumentUploadActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Fechar", null)
            .show()
    }
} 
