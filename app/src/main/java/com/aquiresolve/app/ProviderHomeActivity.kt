package com.aquiresolve.app

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.aquiresolve.app.adapters.BannerAdapter
import com.aquiresolve.app.adapters.ProviderOrdersAdapter
import com.aquiresolve.app.databinding.ActivityProviderHomeBinding
import com.aquiresolve.app.models.HomeBanner
import com.aquiresolve.app.models.OrderData
import com.aquiresolve.app.utils.LocationPermissionHelper
import com.aquiresolve.app.utils.NewOrderSoundHelper
import com.aquiresolve.app.utils.PermissionHelper
import com.aquiresolve.app.utils.ServiceNicheCatalog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ProviderHomeActivity - Tela principal para prestadores
 *
 * Interface especifica para prestadores com:
 * - Dashboard de pedidos
 * - Estatisticas de trabalho
 * - Pedidos disponiveis
 * - Historico de servicos
 * - Configuracoes de disponibilidade
 */
class ProviderHomeActivity : AppCompatActivity(), InsetsSelfManaged {

    private enum class AvailableOrdersFilter(val label: String) {
        ALL("Todos"),
        PENDING("Somente pendentes"),
        DISTRIBUTING("Somente em distribuicao"),
        HIGH_COMMISSION("Comissao >= R$ 100")
    }

    companion object {
        private const val TAG = "ProviderHome"
        private const val HIGH_COMMISSION_THRESHOLD = 100.0
        private const val AVAILABLE_ORDERS_PREVIEW_LIMIT = 4
    }

    private lateinit var binding: ActivityProviderHomeBinding
    private lateinit var authManager: FirebaseAuthManager
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var allAvailableOrders = emptyList<OrderData>()
    private val displayedOrders = mutableListOf<OrderData>()
    private var availableOrdersAdapter: ProviderOrdersAdapter? = null
    private var selectedAvailableOrdersFilter = AvailableOrdersFilter.ALL
    private var providerServicesNormalized = emptySet<String>()
    private var isProviderAvailable = true
    private var isUpdatingAvailability = false
    private var isLocationTrackingRequested = false
    private var requestedLocationPermissionForTracking = false
    private var requestedNotificationPermissionForTracking = false

    // Carrossel de banners
    private var bannerAdapter: BannerAdapter? = null
    private val bannerHandler = Handler(Looper.getMainLooper())
    private var bannerAutoScroll: Runnable? = null
    private var bannerCount = 0

    // Central AquiResolve (chat Base ↔ Prestador): badge de não lidas
    private val centralChatRepository = CentralChatRepository(isProvider = true)
    private var centralChatUnreadListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshProviderLocationTracking()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Garantir Firebase inicializado
        if (!FirebaseConfig.isInitialized()) {
            FirebaseConfig.initialize(this)
        }

        binding = ActivityProviderHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        authManager = FirebaseAuthManager(this)

        // Marca o papel ativo p/ reabrir o app na conta de PRESTADOR na próxima vez.
        authManager.setActiveRole(FirebaseAuthManager.USER_TYPE_PROVIDER)

        setupWindowInsets()
        setupUI()
        setupClickListeners()
        setupBannerCarousel()
        setupCentralChatBadge()
        loadProviderData()
    }

    /**
     * Observa o contador `unreadByProvider` do chat com a Central e mostra um badge
     * no ícone de chat do topo. Marca como lido ao abrir [ProviderCentralChatActivity].
     */
    private fun setupCentralChatBadge() {
        val uid = auth.currentUser?.uid ?: return
        centralChatUnreadListener?.remove()
        centralChatUnreadListener = centralChatRepository.observeUnreadByClient(uid) { count ->
            runOnUiThread {
                val badge = binding.tvCentralChatBadge
                if (count > 0) {
                    badge.text = if (count > 99) "99+" else count.toString()
                    badge.visibility = View.VISIBLE
                } else {
                    badge.visibility = View.GONE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ProviderNewOrderAlertManager.refreshMonitoring()
        startBannerAutoScroll()

        // Recarrega apos voltar para refletir mudancas recentes no Firestore.
        lifecycleScope.launch {
            delay(500)
            loadProviderStats()
            loadAvailabilityState()
            loadAvailableOrders()
            refreshProviderLocationTracking()
        }
    }

    override fun onPause() {
        super.onPause()
        stopBannerAutoScroll()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBannerAutoScroll()
        centralChatUnreadListener?.remove()
        centralChatUnreadListener = null
    }

    /**
     * Configura a interface especifica para prestadores
     */
    private fun setupUI() {
        // Status bar usa a cor do AppBarLayout (definido no XML)
        window.statusBarColor = ContextCompat.getColor(this, R.color.secondary_color)

        // Configurar titulo especifico para prestadores
        binding.tvWelcome.text = "Bem-vindo de volta!"
        binding.tvDashboardTitle.text = "Dashboard"
        binding.tvAvailableOrders.text = "Pedidos Disponiveis"
        setupAvailableOrdersList()
        applyAvailabilityUiState()
    }

    /**
     * Lista de pedidos disponiveis na Home (cards). "Ver pedido" abre os
     * detalhes (onde o prestador aceita) e "Rejeitar" recusa so para ele.
     */
    private fun setupAvailableOrdersList() {
        availableOrdersAdapter = ProviderOrdersAdapter(
            orders = displayedOrders,
            onOrderClick = { order -> openOrderDetails(order) },
            onRejectOrder = { order -> rejectAvailableOrder(order) }
        )
        binding.rvAvailableOrders.apply {
            layoutManager = LinearLayoutManager(this@ProviderHomeActivity)
            adapter = availableOrdersAdapter
            isNestedScrollingEnabled = false
        }
        binding.btnSeeAllOrders.setOnClickListener {
            startActivity(Intent(this, ProviderOrdersActivity::class.java))
        }
    }

    private fun openOrderDetails(order: OrderData) {
        val intent = Intent(this, OrderDetailsActivity::class.java)
        intent.putExtra("order_id", order.id)
        intent.putExtra("is_provider_view", true)
        startActivity(intent)
    }

    /**
     * Recusa um pedido so para este prestador (adiciona o uid em rejectedBy).
     * So 'rejectedBy' muda -> nao cancela para os demais (ver validProviderRejectUpdate).
     */
    private fun rejectAvailableOrder(order: OrderData) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            showToast("Usuario nao autenticado.")
            return
        }
        lifecycleScope.launch {
            try {
                db.collection("orders").document(order.id)
                    .update("rejectedBy", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
                    .await()
                NewOrderSoundHelper.stopSound(order.id)
                // Remove da lista local na hora (feedback imediato)
                allAvailableOrders = allAvailableOrders.filter { it.id != order.id }
                applyAvailableOrdersFilters(showToastResult = false)
                showToast("Pedido recusado.")
            } catch (e: Exception) {
                showToast("Erro ao recusar pedido: ${e.message}")
            }
        }
    }

    /**
     * Aplica os insets do sistema (barra de status + navegacao) para que
     * a BottomNavigationView nao fique escondida atras dos botoes do sistema
     * em dispositivos com navegacao por gestos ou 3 botoes (ex: Realme C73).
     */
    private fun setupWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.updatePadding(top = systemBars.top)
            binding.contentScroll.updatePadding(bottom = dpToPx(96) + systemBars.bottom)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    /**
     * Configura os listeners especificos para prestadores
     */
    private fun setupClickListeners() {
        // Barra de pesquisa de pedidos
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }

        // Botao de filtro
        binding.ivFilter.setOnClickListener {
            showFilterDialog()
        }

        // Botao de disponibilidade
        binding.btnAvailability.setOnClickListener {
            toggleAvailability()
        }

        // Botao de ver todos os pedidos
        binding.btnViewAllOrders.setOnClickListener {
            val intent = Intent(this, ProviderOrdersActivity::class.java)
            startActivity(intent)
        }

        // Botao financeiro
        binding.btnFinancial.setOnClickListener {
            startActivity(Intent(this, ProviderFinancialActivity::class.java))
        }

        // Banner de verificação — clique abre detalhes
        binding.tvVerificationDetails.setOnClickListener {
            startActivity(Intent(this, ProviderVerificationStatusActivity::class.java))
        }

        // Navegacao inferior especifica para prestadores
        binding.bottomNavigation.menu.clear()
        binding.bottomNavigation.inflateMenu(R.menu.bottom_nav_menu_provider)
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    // Ja estamos na home
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

        // Central AquiResolve (chat Base ↔ Prestador)
        binding.btnCentralChat.setOnClickListener {
            startActivity(Intent(this, ProviderCentralChatActivity::class.java))
        }

        // Botao de notificacoes
        binding.btnNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationSettingsActivity::class.java))
        }

        // Botao de configuracoes
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
            val firstName = user.fullName.ifEmpty { user.username }
                .trim()
                .split(" ")
                .firstOrNull()
                ?: "Prestador"
            binding.tvWelcome.text = "Bem-vindo de volta, $firstName!"
        }

        // Carregar estatisticas do prestador
        loadProviderStats()
        loadAvailabilityState()

        // Carregar pedidos disponiveis
        loadAvailableOrders()
    }

    /**
     * Carrega as estatisticas do prestador
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
                    // Preferir providerBalance (acumulado pelo painel admin) com fallback para totalEarnings
                    val balance = providerDoc.getDouble("providerBalance")
                        ?: providerDoc.getDouble("totalEarnings")
                        ?: 0.0

                    // Contar pedidos ativos (assigned ou in_progress)
                    // SEM whereIn no Firestore para evitar FAILED_PRECONDITION (índice composto)
                    val activeOrdersSnap = db.collection("orders")
                        .whereEqualTo("assignedProvider", currentUser.uid)
                        .get()
                        .await()

                    val activeOrders = activeOrdersSnap.documents.count { doc ->
                        val status = doc.getString("status") ?: ""
                        status == OrderData.STATUS_ASSIGNED || status == OrderData.STATUS_IN_PROGRESS
                    }

                    // Carregar nota media
                    val rating = providerDoc.getDouble("rating") ?: 0.0
                    val totalRatings = (providerDoc.getLong("totalRatings") ?: 0L).toInt()

                    // Status de verificação → banner
                    val verificationStatus = providerDoc.getString("verificationStatus") ?: "pending"
                    updateVerificationBanner(verificationStatus, providerDoc.getString("rejectionReason"))

                    // Atualizar interface
                    binding.tvCompletedServices.text = completedJobs.toString()
                    binding.tvActiveOrders.text = activeOrders.toString()
                    binding.tvEarnings.text = formatCurrency(balance)
                    binding.tvProviderRating.text = if (rating > 0) String.format("%.1f", rating) else "-"
                    binding.tvProviderRatingCount.text = if (totalRatings > 0) {
                        "Nota media ($totalRatings avaliacoes)"
                    } else {
                        "Sem avaliacoes ainda"
                    }

                    android.util.Log.d(TAG, "Estatisticas carregadas com sucesso")
                } else {
                    // Prestador nao encontrado, usar valores padrao
                    binding.tvCompletedServices.text = "0"
                    binding.tvActiveOrders.text = "0"
                    binding.tvEarnings.text = "R$ 0,00"
                    updateVerificationBanner("pending", null)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erro ao carregar estatisticas: ${e.message}", e)
                binding.tvCompletedServices.text = "0"
                binding.tvActiveOrders.text = "0"
                binding.tvEarnings.text = "R$ 0,00"
            }
        }
    }

    /**
     * Exibe ou oculta o banner de status de verificação do prestador.
     */
    private fun updateVerificationBanner(status: String, rejectionReason: String?) {
        when (status.lowercase()) {
            // Aceita todos os sinônimos de "aprovado" usados no histórico do banco
            // (mesma normalização de ProviderVerificationManager.getVerificationStatus).
            // Sem isso, prestadores gravados como "verificado"/"verified" apareciam
            // eternamente "em análise" mesmo já verificados.
            "approved", "aprovado", "verified", "verificado" -> {
                binding.cardVerificationBanner.visibility = android.view.View.GONE
            }
            "rejected", "rejeitado" -> {
                binding.cardVerificationBanner.visibility = android.view.View.VISIBLE
                binding.cardVerificationBanner.setCardBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_red_light).let {
                        android.graphics.Color.argb(30, 211, 47, 47)
                    }
                )
                binding.cardVerificationBanner.strokeColor =
                    ContextCompat.getColor(this, R.color.error_color)
                binding.tvVerificationIcon.text = "❌"
                binding.tvVerificationTitle.text = "Verificação reprovada"
                binding.tvVerificationSubtitle.text =
                    if (!rejectionReason.isNullOrBlank()) rejectionReason
                    else "Seus documentos foram reprovados. Toque em Ver para detalhes."
            }
            else -> { // pending ou qualquer outro
                binding.cardVerificationBanner.visibility = android.view.View.VISIBLE
                binding.tvVerificationIcon.text = "⏳"
                binding.tvVerificationTitle.text = "Verificação em análise"
                binding.tvVerificationSubtitle.text = "Seus documentos estão sendo avaliados"
            }
        }
    }

    /**
     * Formata valor monetario
     */
    private fun formatCurrency(value: Double): String {
        return "R$ ${String.format("%.2f", value).replace(".", ",")}"
    }

    /**
     * Carrega os pedidos disponiveis
     */
    private fun loadAvailableOrders() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            allAvailableOrders = emptyList()
            binding.tvNoAvailableOrders.text = "Usuario nao autenticado."
            return
        }

        lifecycleScope.launch {
            binding.tvNoAvailableOrders.text = "Carregando pedidos disponiveis..."
            try {
                val verificationStatus = ProviderVerificationManager().getVerificationStatus(currentUser.uid)
                if (verificationStatus?.status != ProviderVerificationManager.VerificationStatus.APPROVED) {
                    allAvailableOrders = emptyList()
                    binding.tvNoAvailableOrders.text = "Seu perfil ainda nao foi aprovado para receber pedidos."
                    return@launch
                }

                providerServicesNormalized = loadProviderServices(currentUser.uid)
                if (providerServicesNormalized.isEmpty()) {
                    allAvailableOrders = emptyList()
                    binding.tvNoAvailableOrders.text = "Configure seus servicos no perfil para receber pedidos."
                    return@launch
                }

                val statuses = listOf(
                    OrderData.STATUS_PENDING,
                    OrderData.STATUS_DISTRIBUTING,
                    "available",
                    OrderData.STATUS_PENDING.uppercase(),
                    OrderData.STATUS_DISTRIBUTING.uppercase(),
                    "AVAILABLE"
                )

                val snapshot = db.collection("orders")
                    .whereIn("status", statuses)
                    .get()
                    .await()

                val orders = snapshot.documents.mapNotNull { document ->
                    try {
                        document.toObject(OrderData::class.java)?.copy(id = document.id)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Erro ao converter pedido ${document.id}: ${e.message}")
                        null
                    }
                }

                allAvailableOrders = orders
                    .filter { shouldIncludeOrderForProvider(it, currentUser.uid) }
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt.toDate().time }

                applyAvailableOrdersFilters(showToastResult = false)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erro ao carregar pedidos disponiveis: ${e.message}", e)
                allAvailableOrders = emptyList()
                binding.tvNoAvailableOrders.text = "Erro ao carregar pedidos disponiveis."
            }
        }
    }

    /**
     * Executa a pesquisa de pedidos
     */
    private fun performSearch() {
        applyAvailableOrdersFilters(showToastResult = true)
    }

    /**
     * Mostra o dialogo de filtros
     */
    private fun showFilterDialog() {
        val options = AvailableOrdersFilter.entries.toTypedArray()
        val labels = options.map { it.label }.toTypedArray()
        var selectedIndex = selectedAvailableOrdersFilter.ordinal

        AlertDialog.Builder(this)
            .setTitle("Filtrar pedidos")
            .setSingleChoiceItems(labels, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar") { _, _ ->
                selectedAvailableOrdersFilter = options[selectedIndex]
                applyAvailableOrdersFilters(showToastResult = true)
            }
            .show()
    }

    /**
     * Alterna a disponibilidade do prestador
     */
    private fun toggleAvailability() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showToast("Usuario nao autenticado")
            return
        }
        if (isUpdatingAvailability) return

        val nextAvailability = !isProviderAvailable
        isUpdatingAvailability = true
        binding.btnAvailability.isEnabled = false

        lifecycleScope.launch {
            try {
                db.collection("providers")
                    .document(currentUser.uid)
                    .set(
                        mapOf(
                            "isAvailable" to nextAvailability,
                            "updatedAt" to Timestamp.now()
                        ),
                        SetOptions.merge()
                    )
                    .await()

                isProviderAvailable = nextAvailability
                applyAvailabilityUiState()
                applyAvailableOrdersFilters(showToastResult = false)
                ProviderNewOrderAlertManager.refreshMonitoring()
                refreshProviderLocationTracking()

                val message = if (nextAvailability) {
                    "Voce esta disponivel para novos pedidos."
                } else {
                    "Voce esta indisponivel para novos pedidos."
                }
                showToast(message)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erro ao atualizar disponibilidade: ${e.message}", e)
                showToast("Erro ao atualizar disponibilidade.")
            } finally {
                isUpdatingAvailability = false
                binding.btnAvailability.isEnabled = true
            }
        }
    }

    private fun loadAvailabilityState() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            isProviderAvailable = false
            applyAvailabilityUiState()
            return
        }

        lifecycleScope.launch {
            try {
                val providerDoc = db.collection("providers")
                    .document(currentUser.uid)
                    .get()
                    .await()

                isProviderAvailable = providerDoc.getBoolean("isAvailable") ?: true
                applyAvailabilityUiState()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erro ao carregar disponibilidade: ${e.message}", e)
                isProviderAvailable = true
                applyAvailabilityUiState()
            }
        }
    }

    private fun applyAvailabilityUiState() {
        val color = if (isProviderAvailable) {
            ContextCompat.getColor(this, R.color.success_color)
        } else {
            ContextCompat.getColor(this, R.color.error_color)
        }

        binding.btnAvailability.text = if (isProviderAvailable) "Disponivel" else "Indisponivel"
        binding.btnAvailability.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun refreshProviderLocationTracking() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            stopProviderLocationTracking("usuario nao autenticado")
            return
        }

        lifecycleScope.launch {
            try {
                val verificationStatus = ProviderVerificationManager().getVerificationStatus(currentUser.uid)
                if (verificationStatus?.status != ProviderVerificationManager.VerificationStatus.APPROVED) {
                    stopProviderLocationTracking("prestador nao aprovado")
                    return@launch
                }

                val providerDoc = db.collection("providers")
                    .document(currentUser.uid)
                    .get()
                    .await()
                val available = providerDoc.getBoolean("isAvailable") ?: true

                val activeOrdersSnap = db.collection("orders")
                    .whereEqualTo("assignedProvider", currentUser.uid)
                    .whereIn("status", listOf(OrderData.STATUS_ASSIGNED, OrderData.STATUS_IN_PROGRESS))
                    .get()
                    .await()
                val hasActiveOrder = !activeOrdersSnap.isEmpty

                if (!available && !hasActiveOrder) {
                    stopProviderLocationTracking("prestador indisponivel e sem pedido ativo")
                    return@launch
                }

                if (!LocationPermissionHelper.hasLocationPermission(this@ProviderHomeActivity)) {
                    stopProviderLocationTracking("sem permissao de localizacao")
                    requestLocationPermissionForTracking()
                    return@launch
                }

                if (!LocationPermissionHelper.isLocationEnabled(this@ProviderHomeActivity)) {
                    stopProviderLocationTracking("gps desligado")
                    LocationPermissionHelper.showEnableLocationDialog(this@ProviderHomeActivity)
                    return@launch
                }

                if (
                    PermissionHelper.needsNotificationPermission() &&
                    !PermissionHelper.isNotificationPermissionGranted(this@ProviderHomeActivity) &&
                    !requestedNotificationPermissionForTracking
                ) {
                    requestedNotificationPermissionForTracking = true
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@launch
                }

                ProviderLocationForegroundService.start(this@ProviderHomeActivity)
                isLocationTrackingRequested = true
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erro ao atualizar rastreamento de localizacao: ${e.message}", e)
            }
        }
    }

    private fun requestLocationPermissionForTracking() {
        if (requestedLocationPermissionForTracking) {
            return
        }

        requestedLocationPermissionForTracking = true
        if (LocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
            LocationPermissionHelper.showPermissionRationaleDialog(this) {
                LocationPermissionHelper.requestLocationPermission(this)
            }
        } else {
            LocationPermissionHelper.requestLocationPermission(this)
        }
    }

    private fun stopProviderLocationTracking(reason: String) {
        android.util.Log.d(TAG, "Parando rastreamento de localizacao: $reason")
        ProviderLocationForegroundService.stop(this)
        isLocationTrackingRequested = false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LocationPermissionHelper.LOCATION_PERMISSION_REQUEST_CODE) {
            if (LocationPermissionHelper.hasLocationPermission(this)) {
                refreshProviderLocationTracking()
            } else if (!LocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                LocationPermissionHelper.showPermissionDeniedDialog(this)
            } else {
                showToast("Permissao de localizacao necessaria para compartilhar rota em pedidos.")
            }
        }
    }

    private suspend fun loadProviderServices(providerId: String): Set<String> {
        return try {
            val providerDoc = db.collection("providers")
                .document(providerId)
                .get()
                .await()

            val rawServices = (providerDoc.get("services") as? List<*>)
                ?.mapNotNull { it as? String }
                ?: emptyList()

            ServiceNicheCatalog.normalizeProviderServices(rawServices)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Erro ao carregar servicos do prestador: ${e.message}", e)
            emptySet()
        }
    }

    private fun shouldIncludeOrderForProvider(order: OrderData, providerId: String): Boolean {
        val assignedProvider = order.assignedProvider
        if (!assignedProvider.isNullOrBlank() && assignedProvider != providerId) {
            return false
        }

        // Excluir pedidos que este prestador já rejeitou
        if (order.rejectedBy.contains(providerId)) {
            return false
        }

        return ServiceNicheCatalog.matchesProviderServices(providerServicesNormalized, order)
    }

    private fun applyAvailableOrdersFilters(showToastResult: Boolean) {
        val query = binding.etSearch.text.toString().trim()
        var filteredOrders = allAvailableOrders

        filteredOrders = when (selectedAvailableOrdersFilter) {
            AvailableOrdersFilter.ALL -> filteredOrders
            AvailableOrdersFilter.PENDING -> filteredOrders.filter {
                it.status.equals(OrderData.STATUS_PENDING, ignoreCase = true)
            }
            AvailableOrdersFilter.DISTRIBUTING -> filteredOrders.filter {
                it.status.equals(OrderData.STATUS_DISTRIBUTING, ignoreCase = true)
            }
            AvailableOrdersFilter.HIGH_COMMISSION -> filteredOrders.filter {
                it.providerCommission >= HIGH_COMMISSION_THRESHOLD
            }
        }

        if (query.isNotEmpty()) {
            filteredOrders = filteredOrders.filter { order ->
                order.protocol.contains(query, ignoreCase = true) ||
                    order.serviceType.contains(query, ignoreCase = true) ||
                    order.serviceName.contains(query, ignoreCase = true) ||
                    order.description.contains(query, ignoreCase = true) ||
                    order.clientName.contains(query, ignoreCase = true) ||
                    order.address.contains(query, ignoreCase = true) ||
                    order.id.contains(query, ignoreCase = true)
            }
        }

        updateAvailableOrdersSummary(filteredOrders, query)
        if (showToastResult) {
            val message = if (filteredOrders.isEmpty()) {
                "Nenhum pedido encontrado."
            } else {
                "${filteredOrders.size} pedido(s) encontrado(s)."
            }
            showToast(message)
        }
    }

    private fun updateAvailableOrdersSummary(orders: List<OrderData>, query: String) {
        // Caminhos de estado vazio: mostra o card de mensagem e esconde a lista.
        val emptyMessage: String? = when {
            !isProviderAvailable ->
                "Voce esta indisponivel. Ative para receber novos pedidos."
            providerServicesNormalized.isEmpty() ->
                "Configure seus servicos no perfil para receber pedidos."
            orders.isEmpty() -> if (query.isNotEmpty()) {
                "Nenhum pedido encontrado para \"$query\"."
            } else {
                "Nenhum pedido disponivel no momento."
            }
            else -> null
        }

        if (emptyMessage != null) {
            displayedOrders.clear()
            availableOrdersAdapter?.notifyDataSetChanged()
            binding.rvAvailableOrders.visibility = View.GONE
            binding.cardNoOrders.visibility = View.VISIBLE
            binding.tvNoAvailableOrders.text = emptyMessage
            binding.tvAvailableOrdersCount.visibility = View.GONE
            binding.btnSeeAllOrders.visibility = View.GONE
            return
        }

        // Há pedidos: popula os cards (limitando o preview na Home).
        val preview = orders.take(AVAILABLE_ORDERS_PREVIEW_LIMIT)
        displayedOrders.clear()
        displayedOrders.addAll(preview)
        availableOrdersAdapter?.notifyDataSetChanged()

        binding.rvAvailableOrders.visibility = View.VISIBLE
        binding.cardNoOrders.visibility = View.GONE

        binding.tvAvailableOrdersCount.visibility = View.VISIBLE
        binding.tvAvailableOrdersCount.text = orders.size.toString()

        // Botao "Ver todos" so quando há mais pedidos do que cabem no preview.
        binding.btnSeeAllOrders.visibility =
            if (orders.size > preview.size) View.VISIBLE else View.GONE
    }

    /**
     * Mostra configuracoes
     */
    private fun showSettings() {
        startActivity(Intent(this, ProfileActivity::class.java))
    }

    /**
     * Exibe uma mensagem toast
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Carrossel de banners (igual ao da Home do cliente)
    // ═══════════════════════════════════════════════════════════════════

    private fun setupBannerCarousel() {
        bannerAdapter = BannerAdapter(emptyList()) { banner -> onBannerClicked(banner) }
        binding.bannerPager.adapter = bannerAdapter

        binding.bannerPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateBannerDots(position)
                restartBannerAutoScroll()
            }
        })

        applyBanners(ProviderBannerRepository.cachedBanners())

        lifecycleScope.launch {
            val banners = try { ProviderBannerRepository.load() } catch (_: Exception) { emptyList() }
            applyBanners(banners)
        }
    }

    private fun applyBanners(banners: List<HomeBanner>) {
        bannerCount = banners.size
        if (banners.isEmpty()) {
            binding.sectionBanners.visibility = View.GONE
            stopBannerAutoScroll()
            return
        }
        binding.sectionBanners.visibility = View.VISIBLE
        bannerAdapter?.updateItems(banners)
        buildBannerDots(banners.size)
        updateBannerDots(binding.bannerPager.currentItem)
        startBannerAutoScroll()
    }

    private fun buildBannerDots(count: Int) {
        binding.bannerDots.removeAllViews()
        val size = (8 * resources.displayMetrics.density).toInt()
        val margin = (3 * resources.displayMetrics.density).toInt()
        repeat(count) {
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginStart = margin
            lp.marginEnd = margin
            dot.layoutParams = lp
            dot.setBackgroundResource(R.drawable.banner_dot_inactive)
            binding.bannerDots.addView(dot)
        }
        binding.bannerDots.visibility = if (count > 1) View.VISIBLE else View.GONE
    }

    private fun updateBannerDots(active: Int) {
        for (i in 0 until binding.bannerDots.childCount) {
            binding.bannerDots.getChildAt(i).setBackgroundResource(
                if (i == active) R.drawable.banner_dot_active else R.drawable.banner_dot_inactive
            )
        }
    }

    private fun startBannerAutoScroll() {
        if (bannerCount <= 1) return
        stopBannerAutoScroll()
        val runnable = Runnable {
            if (bannerCount <= 1) return@Runnable
            val next = (binding.bannerPager.currentItem + 1) % bannerCount
            binding.bannerPager.setCurrentItem(next, true)
        }
        bannerAutoScroll = runnable
        bannerHandler.postDelayed(runnable, 4000L)
    }

    private fun restartBannerAutoScroll() {
        stopBannerAutoScroll()
        startBannerAutoScroll()
    }

    private fun stopBannerAutoScroll() {
        bannerAutoScroll?.let { bannerHandler.removeCallbacks(it) }
    }

    private fun onBannerClicked(banner: HomeBanner) {
        when (banner.actionType) {
            HomeBanner.ACTION_CASHBACK -> {} // prestador não tem cashback
            HomeBanner.ACTION_NICHE -> {
                val niche = banner.actionValue.trim()
                if (niche.isNotEmpty()) {
                    startActivity(Intent(this, ProviderNichesActivity::class.java))
                }
            }
            HomeBanner.ACTION_SERVICE -> {
                startActivity(Intent(this, ProviderOrdersActivity::class.java))
            }
            HomeBanner.ACTION_URL -> {
                val u = banner.actionValue.trim()
                if (u.startsWith("http://") || u.startsWith("https://")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } catch (_: Exception) {}
                }
            }
            else -> {} // none ou desconhecido
        }
    }
}
