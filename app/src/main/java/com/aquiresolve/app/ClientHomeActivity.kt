package com.aquiresolve.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.aquiresolve.app.adapters.BannerAdapter
import com.aquiresolve.app.adapters.HomeCategoryAdapter
import com.aquiresolve.app.adapters.HomeComboAdapter
import com.aquiresolve.app.adapters.PartnerAdapter
import com.aquiresolve.app.adapters.SearchSuggestionAdapter
import com.aquiresolve.app.databinding.ActivityClientHomeBinding
import com.aquiresolve.app.models.HomeBanner
import com.aquiresolve.app.models.HomeCombo
import com.aquiresolve.app.models.Partner
import com.aquiresolve.app.models.OrderData
import com.aquiresolve.app.models.SearchSuggestion
import com.aquiresolve.app.utils.ServiceSearchHelper
import com.aquiresolve.app.utils.NotificationBadgeHelper
import com.aquiresolve.app.utils.PriceFormatter
import com.aquiresolve.app.utils.ServiceNicheCatalog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ClientHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClientHomeBinding
    private lateinit var authManager: FirebaseAuthManager
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val centralChatRepo = CentralChatRepository()
    private var centralUnreadListener: ListenerRegistration? = null

    // Carrossel de banners
    private var bannerAdapter: BannerAdapter? = null
    private val bannerHandler = Handler(Looper.getMainLooper())
    private var bannerAutoScroll: Runnable? = null
    private var bannerCount = 0

    // Vitrine de Combos Promocionais
    private var comboAdapter: HomeComboAdapter? = null

    // Vitrine de Parceiros AquiResolve
    private var partnerAdapter: PartnerAdapter? = null

    // Busca Inteligente
    private var suggestionAdapter: SearchSuggestionAdapter? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var lastSearchQuery = ""

    companion object {
        private const val BANNER_INTERVAL_MS = 4000L
        private const val SEARCH_DEBOUNCE_MS = 250L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!FirebaseConfig.isInitialized()) {
            FirebaseConfig.initialize(this)
        }

        binding = ActivityClientHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        authManager = FirebaseAuthManager(this)

        setupWindowInsets()
        setupUI()
        setupClickListeners()
        setupCategories()
        setupBannerCarousel()
        setupCombos()
        setupPartners()
        setupSearchSuggestions()
        loadProfileImage()
        loadRecentOrders()
    }

    override fun onResume() {
        super.onResume()
        ProviderNewOrderAlertManager.refreshMonitoring()
        loadProfileImage()
        loadRecentOrders()
        loadCashbackBalance()

        // Iniciar monitoramento de notificações para mostrar badge
        NotificationBadgeHelper.startListening(
            bottomNav = binding.bottomNavigation,
            menuItemId = R.id.navigation_orders
        )

        startCentralBadgeListener()
        startBannerAutoScroll()
    }

    override fun onPause() {
        super.onPause()
        // Parar listener quando sair da tela pra evitar vazamento
        NotificationBadgeHelper.stopListening()
        centralUnreadListener?.remove()
        centralUnreadListener = null
        stopBannerAutoScroll()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Garante que o loop do carrossel não vaze (mesmo cuidado dos listeners)
        stopBannerAutoScroll()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }

    private fun startCentralBadgeListener() {
        val uid = auth.currentUser?.uid ?: return
        centralUnreadListener?.remove()
        centralUnreadListener = centralChatRepo.observeUnreadByClient(uid) { count ->
            if (count > 0) {
                binding.tvCentralBadge.visibility = View.VISIBLE
                binding.tvCentralBadge.text = if (count > 99) "99+" else count.toString()
            } else {
                binding.tvCentralBadge.visibility = View.GONE
            }
        }
    }

    private fun setupUI() {
        binding.bottomNavigation.selectedItemId = R.id.navigation_home
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        binding.tvWelcome.text = "Olá! Que tipo de serviço você precisa?"
        binding.tvRecentOrders.text = "Seus Pedidos Recentes"
    }

    private fun setupWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.appBarLayout.updatePadding(top = systemBars.top)
            binding.contentScroll.updatePadding(bottom = dpToPx(96) + systemBars.bottom)
            binding.bottomNavigation.updatePadding(bottom = systemBars.bottom)

            windowInsets
        }
    }

    /**
     * Carrega a foto de perfil no botão do header
     */
    private fun loadProfileImage() {
        val profileImageUrl = authManager.getLocalUserData()?.profileImageUrl
        // Remover tint para que a foto carregue com cores corretas (evita aparência cinza)
        binding.btnProfile.imageTintList = null
        
        val requestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .placeholder(R.drawable.ic_person)
            .error(R.drawable.ic_person)
        
        Glide.with(this)
            .load(profileImageUrl?.takeIf { it.isNotEmpty() })
            .apply(requestOptions)
            .into(binding.btnProfile)
    }

    private fun setupClickListeners() {
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }

        binding.ivFilter.setOnClickListener {
            showToast("Filtros em desenvolvimento...")
        }

        binding.btnNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationHistoryActivity::class.java))
        }

        binding.btnCentralChat.setOnClickListener {
            startActivity(Intent(this, ClientCentralChatActivity::class.java))
        }

        binding.cardCashback.setOnClickListener {
            startActivity(Intent(this, CashbackActivity::class.java))
        }

        binding.btnCart.setOnClickListener {
            startActivity(Intent(this, ClientCartActivity::class.java))
        }

        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.btnMakeOrder.setOnClickListener {
            // Pedidos só podem ser feitos pela aba Serviços
            startActivity(Intent(this, ServicesActivity::class.java))
        }

        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> true
                R.id.navigation_orders -> {
                    startActivity(Intent(this, ClientOrdersActivity::class.java))
                    true
                }
                R.id.navigation_services -> {
                    startActivity(Intent(this, ServicesActivity::class.java))
                    true
                }
                R.id.navigation_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Monta a seção de Categorias (nichos) em scroll horizontal na Home.
     *
     * Fonte: catálogo dinâmico ([CatalogRepository] → [ServiceNicheCatalog]), com fallback estático
     * automático quando o Firestore está vazio/offline (a seção nunca aparece vazia). Tocar num nicho
     * abre [CreateOrderActivity] já com o nicho pré-selecionado (mesmo extra `service_category_name`
     * usado por [ServicesActivity]); o item final "Ver todos" abre a lista completa em [ServicesActivity].
     */
    private fun setupCategories() {
        val adapter = HomeCategoryAdapter(
            niches = emptyList(),
            onNicheClick = { item ->
                logCategoryClick(item.name)
                startActivity(
                    Intent(this, CreateOrderActivity::class.java)
                        .putExtra("service_category_name", item.name)
                )
            },
            onSeeAll = {
                startActivity(Intent(this, ServicesActivity::class.java))
            }
        )

        binding.rvCategories.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvCategories.adapter = adapter
        // Fallback imediato (cache em memória) para não piscar vazio enquanto o Firestore carrega.
        adapter.updateItems(buildCategoryItems())

        lifecycleScope.launch {
            try {
                CatalogRepository.load()
            } catch (_: Exception) {
            }
            adapter.updateItems(buildCategoryItems())
        }
    }

    /** Converte o catálogo (com ícone) em itens do adapter e acrescenta o atalho "Ver todos". */
    private fun buildCategoryItems(): List<HomeCategoryAdapter.Item> {
        val items = ServiceNicheCatalog.selectableNichesWithIcons().map {
            HomeCategoryAdapter.Item(name = it.name, icon = it.icon)
        }
        return items + HomeCategoryAdapter.Item(name = "Ver todos", icon = "", seeAll = true)
    }

    private fun logCategoryClick(niche: String) {
        try {
            FirebaseConfig.getAnalytics()?.logEvent(
                "home_categoria_click",
                android.os.Bundle().apply { putString("niche", niche) }
            )
        } catch (_: Exception) {
        }
    }

    // ───────────────────────────── Carrossel de banners ─────────────────────────────

    /**
     * Monta o carrossel rotativo de banners no topo da Home.
     *
     * Fonte: [BannerRepository] (coleção `home_banners`, gerida pelo painel). Quando não há banners
     * ativos (vazio/offline/erro), a seção inteira fica `GONE` — a Home nunca exibe espaço vazio.
     * Cada banner roteia conforme `actionType` ([onBannerClicked]); auto-scroll a cada ~4s, pausando
     * em `onPause`/`onDestroy` e reiniciando o timer a cada troca de página (inclusive swipe manual).
     */
    private fun setupBannerCarousel() {
        bannerAdapter = BannerAdapter(emptyList()) { banner -> onBannerClicked(banner) }
        binding.bannerPager.adapter = bannerAdapter

        binding.bannerPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateBannerDots(position)
                // Reinicia o timer ao trocar de página — vale tanto para o auto-scroll
                // quanto para o swipe manual do usuário (que assim "ganha" tempo na página).
                restartBannerAutoScroll()
            }
        })

        // Cache imediato (o AppApplication pré-carrega) para não piscar enquanto o Firestore responde.
        applyBanners(BannerRepository.cachedBanners())

        lifecycleScope.launch {
            val banners = try {
                BannerRepository.load()
            } catch (_: Exception) {
                emptyList()
            }
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
        val size = dpToPx(8)
        val margin = dpToPx(3)
        repeat(count) {
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginStart = margin
            lp.marginEnd = margin
            dot.layoutParams = lp
            dot.setBackgroundResource(R.drawable.banner_dot_inactive)
            binding.bannerDots.addView(dot)
        }
        // Com 1 banner, dots não fazem sentido.
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
            // A troca dispara onPageSelected → reagenda o próximo passo (loop contínuo).
            binding.bannerPager.setCurrentItem(next, true)
        }
        bannerAutoScroll = runnable
        bannerHandler.postDelayed(runnable, BANNER_INTERVAL_MS)
    }

    private fun restartBannerAutoScroll() {
        stopBannerAutoScroll()
        startBannerAutoScroll()
    }

    private fun stopBannerAutoScroll() {
        bannerAutoScroll?.let { bannerHandler.removeCallbacks(it) }
    }

    /** Roteia o toque no banner conforme `actionType`, tratando valor inválido como "sem ação". */
    private fun onBannerClicked(banner: HomeBanner) {
        logBannerClick(banner)
        when (banner.actionType) {
            HomeBanner.ACTION_CASHBACK ->
                startActivity(Intent(this, CashbackActivity::class.java))

            HomeBanner.ACTION_NICHE -> {
                val niche = banner.actionValue.trim()
                val known = CatalogRepository.cachedNicheNames().any { it.equals(niche, ignoreCase = true) }
                if (niche.isNotEmpty() && known) {
                    startActivity(
                        Intent(this, CreateOrderActivity::class.java)
                            .putExtra("service_category_name", niche)
                    )
                } else {
                    // Nicho inválido/removido → fallback seguro (não crashar): lista de serviços.
                    startActivity(Intent(this, ServicesActivity::class.java))
                }
            }

            HomeBanner.ACTION_SERVICE -> {
                val intent = Intent(this, ServicesActivity::class.java)
                if (banner.actionValue.isNotBlank()) {
                    intent.putExtra("search_query", banner.actionValue.trim())
                }
                startActivity(intent)
            }

            HomeBanner.ACTION_URL -> openExternalUrl(banner.actionValue)

            // Seções futuras (combos = plano 03, parceiros = plano 04): fallback p/ navegação de serviços.
            HomeBanner.ACTION_COMBOS, HomeBanner.ACTION_PARTNERS ->
                startActivity(Intent(this, ServicesActivity::class.java))

            else -> {
                // none / tipo desconhecido → banner apenas informativo, sem ação.
            }
        }
    }

    private fun openExternalUrl(url: String) {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        } catch (_: Exception) {
            showToast("Não foi possível abrir o link")
        }
    }

    private fun logBannerClick(banner: HomeBanner) {
        try {
            FirebaseConfig.getAnalytics()?.logEvent(
                "home_banner_click",
                android.os.Bundle().apply {
                    putString("actionType", banner.actionType)
                    putString("actionValue", banner.actionValue)
                    putString("bannerId", banner.id)
                }
            )
        } catch (_: Exception) {
        }
    }

    // ───────────────────────────── Combos Promocionais ─────────────────────────────

    /**
     * Monta a vitrine horizontal de Combos Promocionais (plano 03). Fonte: [ComboRepository]
     * (coleção `home_combos`, gerida pelo painel). Sem combos ativos, a seção fica `GONE`.
     * Tocar num combo abre [ComboDetailActivity] (detalhe + adicionar ao carrinho).
     */
    private fun setupCombos() {
        comboAdapter = HomeComboAdapter(emptyList()) { combo -> onComboClicked(combo) }
        binding.rvCombos.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvCombos.adapter = comboAdapter

        // Primeiro o cache (instantâneo), depois recarrega do Firestore.
        applyCombos(ComboRepository.cachedCombos())
        lifecycleScope.launch {
            val combos = try {
                ComboRepository.load()
            } catch (_: Exception) {
                ComboRepository.cachedCombos()
            }
            applyCombos(combos)
        }
    }

    private fun applyCombos(combos: List<HomeCombo>) {
        if (combos.isEmpty()) {
            binding.sectionCombos.visibility = View.GONE
            return
        }
        binding.sectionCombos.visibility = View.VISIBLE
        comboAdapter?.updateItems(combos)
    }

    private fun onComboClicked(combo: HomeCombo) {
        logComboClick(combo)
        startActivity(
            Intent(this, ComboDetailActivity::class.java)
                .putExtra(ComboDetailActivity.EXTRA_COMBO_ID, combo.id)
        )
    }

    private fun logComboClick(combo: HomeCombo) {
        try {
            FirebaseConfig.getAnalytics()?.logEvent(
                "home_combo_click",
                android.os.Bundle().apply {
                    putString("comboId", combo.id)
                    putString("comboName", combo.name)
                }
            )
        } catch (_: Exception) {
        }
    }

    // ───────────────────────────── Parceiros AquiResolve ─────────────────────────────

    /**
     * Monta a seção horizontal de Parceiros AquiResolve (plano 04). Fonte: [PartnerRepository]
     * (coleção `partners`, gerida pelo painel). Sem parceiros ativos, a seção fica `GONE`.
     * Tocar num parceiro abre [PartnerDetailActivity] (descrição + cupom/copiar + visitar site).
     */
    private fun setupPartners() {
        partnerAdapter = PartnerAdapter(emptyList()) { partner -> onPartnerClicked(partner) }
        binding.rvPartners.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvPartners.adapter = partnerAdapter

        // Primeiro o cache (instantâneo), depois recarrega do Firestore.
        applyPartners(PartnerRepository.cachedPartners())
        lifecycleScope.launch {
            val partners = try {
                PartnerRepository.load()
            } catch (_: Exception) {
                PartnerRepository.cachedPartners()
            }
            applyPartners(partners)
        }
    }

    private fun applyPartners(partners: List<Partner>) {
        if (partners.isEmpty()) {
            binding.sectionPartners.visibility = View.GONE
            return
        }
        binding.sectionPartners.visibility = View.VISIBLE
        partnerAdapter?.updateItems(partners)
    }

    private fun onPartnerClicked(partner: Partner) {
        logPartnerClick(partner)
        startActivity(
            Intent(this, PartnerDetailActivity::class.java)
                .putExtra(PartnerDetailActivity.EXTRA_PARTNER_ID, partner.id)
        )
    }

    private fun logPartnerClick(partner: Partner) {
        try {
            FirebaseConfig.getAnalytics()?.logEvent(
                "parceiro_click",
                android.os.Bundle().apply {
                    putString("partnerId", partner.id)
                    putString("partnerName", partner.name)
                }
            )
        } catch (_: Exception) {
        }
    }

    // ───────────────────────────── Busca Inteligente ─────────────────────────────

    /**
     * Liga a busca instantânea (sem IA): ao digitar em [etSearch], com debounce de ~250ms, mostra
     * sugestões de serviços/nichos do catálogo em cache ([ServiceSearchHelper.suggest]) num dropdown
     * abaixo do campo. Tocar leva ao pedido pré-preenchido; sem resultado, um CTA abre a lista completa.
     * Nada de Firestore por tecla — o catálogo é pré-aquecido (aqui e no `AppApplication`).
     */
    private fun setupSearchSuggestions() {
        suggestionAdapter = SearchSuggestionAdapter(emptyList()) { s -> onSuggestionClick(s) }
        binding.rvSearchSuggestions.layoutManager = LinearLayoutManager(this)
        binding.rvSearchSuggestions.adapter = suggestionAdapter

        binding.tvSearchEmptyCta.setOnClickListener {
            logSearchNoResult(lastSearchQuery)
            hideSuggestions()
            startActivity(
                Intent(this, ServicesActivity::class.java).apply {
                    if (lastSearchQuery.isNotBlank()) putExtra("search_query", lastSearchQuery)
                }
            )
        }

        binding.etSearch.addTextChangedListener(afterTextChanged = { editable ->
            val query = editable?.toString().orEmpty()
            searchRunnable?.let { searchHandler.removeCallbacks(it) }
            if (query.isBlank()) {
                hideSuggestions()
                return@addTextChangedListener
            }
            val runnable = Runnable { runSearchSuggestions(query) }
            searchRunnable = runnable
            searchHandler.postDelayed(runnable, SEARCH_DEBOUNCE_MS)
        })

        // Garante o catálogo de serviços em cache para a busca (idempotente com o AppApplication).
        lifecycleScope.launch {
            try {
                CatalogServiceRepository.loadAll()
            } catch (_: Exception) {
            }
        }
    }

    private fun runSearchSuggestions(query: String) {
        lastSearchQuery = query
        val suggestions = try {
            ServiceSearchHelper.suggest(
                query = query,
                niches = CatalogRepository.cachedNicheNames(),
                services = CatalogServiceRepository.allCachedServices()
            )
        } catch (_: Exception) {
            emptyList()
        }

        binding.sectionSearchSuggestions.visibility = View.VISIBLE
        if (suggestions.isEmpty()) {
            binding.rvSearchSuggestions.visibility = View.GONE
            binding.tvSearchEmptyCta.visibility = View.VISIBLE
        } else {
            binding.tvSearchEmptyCta.visibility = View.GONE
            binding.rvSearchSuggestions.visibility = View.VISIBLE
            suggestionAdapter?.updateItems(suggestions)
        }
    }

    private fun hideSuggestions() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        binding.sectionSearchSuggestions.visibility = View.GONE
        binding.tvSearchEmptyCta.visibility = View.GONE
    }

    private fun onSuggestionClick(s: SearchSuggestion) {
        logSearchSuggestionClick(s)
        hideSuggestions()
        // Não tenta esconder o teclado aqui: a próxima Activity assume o foco.
        val intent = Intent(this, CreateOrderActivity::class.java)
            .putExtra("service_category_name", s.niche)
        if (s.type == SearchSuggestion.Type.SERVICE) {
            // `preselect_service` pré-seleciona o serviço exato no dropdown (o nicho já vai correto);
            // `search_query` fica como fallback para o matcher estático.
            intent.putExtra("preselect_service", s.label)
            intent.putExtra("search_query", s.label)
        }
        startActivity(intent)
    }

    private fun logSearchSuggestionClick(s: SearchSuggestion) {
        try {
            FirebaseConfig.getAnalytics()?.logEvent(
                "busca_sugestao_click",
                android.os.Bundle().apply {
                    putString("label", s.label)
                    putString("niche", s.niche)
                    putString("type", s.type.name)
                }
            )
        } catch (_: Exception) {
        }
    }

    private fun logSearchNoResult(query: String) {
        try {
            FirebaseConfig.getAnalytics()?.logEvent(
                "busca_sem_resultado",
                android.os.Bundle().apply { putString("query", query) }
            )
        } catch (_: Exception) {
        }
    }

    private fun loadRecentOrders() {
        val currentUser = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val snapshot = db.collection("orders")
                    .whereEqualTo("clientId", currentUser.uid)
                    .get()
                    .await()

                val orders = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(OrderData::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                }.sortedByDescending { it.createdAt.toDate().time }
                    .take(3)

                binding.containerRecentOrders.removeAllViews()

                if (orders.isEmpty()) {
                    binding.tvEmptyOrders.visibility = View.VISIBLE
                    binding.containerRecentOrders.visibility = View.GONE
                } else {
                    binding.tvEmptyOrders.visibility = View.GONE
                    binding.containerRecentOrders.visibility = View.VISIBLE

                    for (order in orders) {
                        val card = LayoutInflater.from(this@ClientHomeActivity)
                            .inflate(R.layout.item_recent_order, binding.containerRecentOrders, false)

                        card.findViewById<TextView>(R.id.tvOrderService).text =
                            order.serviceType.ifEmpty { order.serviceName.ifEmpty { "Serviço" } }
                        card.findViewById<TextView>(R.id.tvOrderStatus).text =
                            getStatusText(order.status)
                        card.findViewById<TextView>(R.id.tvOrderPrice).text =
                            PriceFormatter.formatOrderPrice(order)

                        card.setOnClickListener {
                            val intent = Intent(this@ClientHomeActivity, OrderDetailsActivity::class.java)
                            intent.putExtra("order_id", order.id)
                            startActivity(intent)
                        }

                        binding.containerRecentOrders.addView(card)
                    }
                }
            } catch (e: Exception) {
                binding.tvEmptyOrders.visibility = View.VISIBLE
                binding.containerRecentOrders.visibility = View.GONE
            }
        }
    }

    private fun getStatusText(status: String): String {
        return when (status) {
            OrderData.STATUS_AWAITING_PAYMENT -> "Aguardando Pagamento"
            OrderData.STATUS_PENDING -> "Pendente"
            OrderData.STATUS_DISTRIBUTING -> "Em Distribuição"
            OrderData.STATUS_ASSIGNED -> "Atribuído"
            OrderData.STATUS_IN_PROGRESS -> "Em Andamento"
            OrderData.STATUS_COMPLETED -> "Concluído"
            OrderData.STATUS_CANCELLED -> "Cancelado"
            else -> status
        }
    }

    private fun performSearch() {
        val searchQuery = binding.etSearch.text.toString().trim()
        if (searchQuery.isNotEmpty()) {
            // Redirecionar para ServicesActivity com a busca
            val intent = Intent(this, ServicesActivity::class.java).apply {
                putExtra("search_query", searchQuery)
            }
            startActivity(intent)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun loadCashbackBalance() {
        val uid = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val snap = db.collection("users").document(uid).get().await()
                val balance = snap.getDouble("cashbackBalance") ?: 0.0
                binding.tvCashbackBalance.text = String.format(java.util.Locale("pt", "BR"), "R$ %.2f", balance)
            } catch (_: Exception) {}
        }
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
