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
import com.aquiresolve.app.adapters.PartnerBannerAdapter
import com.aquiresolve.app.adapters.SearchSuggestionAdapter
import com.aquiresolve.app.databinding.ActivityClientHomeBinding
import com.aquiresolve.app.models.HomeBanner
import com.aquiresolve.app.models.HomeCombo
import com.aquiresolve.app.models.Partner
import com.aquiresolve.app.models.SearchSuggestion
import com.aquiresolve.app.utils.ServiceSearchHelper
import com.aquiresolve.app.utils.NotificationBadgeHelper
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
    private val floatingMic = FloatingMicHelper()

    // Carrossel de banners
    private var bannerAdapter: BannerAdapter? = null
    private val bannerHandler = Handler(Looper.getMainLooper())
    private var bannerAutoScroll: Runnable? = null
    private var bannerCount = 0

    // Vitrine de Combos Promocionais
    private var comboAdapter: HomeComboAdapter? = null

    // Carrossel de banners de Parceiros (rotação por item + limite diário de clientes)
    private var partnerBannerAdapter: PartnerBannerAdapter? = null
    private val partnerHandler = Handler(Looper.getMainLooper())
    private var partnerAutoScroll: Runnable? = null
    private var partnerBanners: List<Partner> = emptyList()
    private val registeredImpressions = HashSet<String>()

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
        floatingMic.attach(this)
        setupSwipeRefresh()
        setupCategories()
        setupBannerCarousel()
        setupCombos()
        setupPartners()
        setupSearchSuggestions()
        loadProfileImage()
    }

    /**
     * Pull-to-refresh (plano 07): recarrega as seções dinâmicas da Home. Cada seção já trata erro
     * isolado e se esconde quando vazia, então o refresh nunca quebra a tela. O spinner some assim
     * que os carregamentos são disparados (cada seção atualiza sozinha ao concluir).
     */
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary_color, R.color.secondary_color)
        binding.swipeRefresh.setOnRefreshListener {
            setupCategories()
            setupBannerCarousel()
            setupCombos()
            setupPartners()
            loadCashbackBalance()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    override fun onResume() {
        super.onResume()
        ProviderNewOrderAlertManager.refreshMonitoring()
        loadProfileImage()
        loadCashbackBalance()

        // Iniciar monitoramento de notificações para mostrar badge
        NotificationBadgeHelper.startListening(
            bottomNav = binding.bottomNavigation,
            menuItemId = R.id.navigation_orders
        )

        startCentralBadgeListener()
        startBannerAutoScroll()
        startPartnerAutoScroll()
    }

    override fun onPause() {
        super.onPause()
        // Parar listener quando sair da tela pra evitar vazamento
        NotificationBadgeHelper.stopListening()
        centralUnreadListener?.remove()
        centralUnreadListener = null
        stopBannerAutoScroll()
        stopPartnerAutoScroll()
    }

    override fun onDestroy() {
        floatingMic.detach()
        super.onDestroy()
        // Garante que o loop dos carrosséis não vaze (mesmo cuidado dos listeners)
        stopBannerAutoScroll()
        stopPartnerAutoScroll()
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
        // Saudação enxuta de 1 linha (Home Premium), personalizada quando há nome local.
        val firstName = authManager.getLocalUserData()?.fullName?.trim()?.split(" ")?.firstOrNull().orEmpty()
        binding.tvWelcome.text =
            if (firstName.isNotEmpty()) "Olá, $firstName! O que você precisa hoje?"
            else "Olá! O que você precisa hoje?"
    }

    private fun setupWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.appBarLayout.updatePadding(top = systemBars.top)
            binding.contentScroll.updatePadding(bottom = dpToPx(96) + systemBars.bottom)

            windowInsets
        }

        // A BottomNavigationView (Material) já aplica o inset da barra de navegação do sistema
        // sozinha. Antes adicionávamos `systemBars.bottom` TAMBÉM no listener da raiz, o que
        // duplicava o padding em Android antigo (3 botões) e deixava a barra com tamanho bugado.
        // Aqui assumimos o controle com UM listener próprio (substitui o interno do Material),
        // garantindo o inset aplicado exatamente uma vez, em qualquer versão (minSdk 24).
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
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
        
        // ✅ BUG-03: quando não há foto, carregamos o placeholder diretamente em vez de
        // `load(null)` — este último dispara o warning "Glide: Received null model".
        val model: Any = profileImageUrl?.takeIf { it.isNotEmpty() } ?: R.drawable.ic_person
        Glide.with(this)
            .load(model)
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
                R.id.navigation_assistant -> {
                    startActivity(Intent(this, AssistantChatActivity::class.java))
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

            // Combos (plano 03) e Parceiros (plano 04): rola até a seção na própria Home se ela
            // estiver visível; se não houver itens (seção GONE), cai na lista de serviços.
            HomeBanner.ACTION_COMBOS -> scrollToSectionOrFallback(binding.sectionCombos)
            HomeBanner.ACTION_PARTNERS -> scrollToSectionOrFallback(binding.sectionPartnerBanners)

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

    /**
     * Rola a Home até [section] (filha direta do conteúdo do [contentScroll]) quando ela está
     * visível; se a seção estiver `GONE` (sem itens), abre a lista de serviços como fallback.
     */
    private fun scrollToSectionOrFallback(section: View) {
        if (section.visibility == View.VISIBLE) {
            binding.contentScroll.post {
                binding.contentScroll.smoothScrollTo(0, section.top)
            }
        } else {
            startActivity(Intent(this, ServicesActivity::class.java))
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
     * Monta o **carrossel de banners de Parceiros** (logo abaixo do banner do topo). Fonte:
     * [PartnerRepository] (coleção `partners`, gerida pelo painel), filtrado por
     * [PartnerImpressionManager.availablePartners] (limite diário de clientes + janela da campanha).
     * Cada banner roda por `rotationSeconds`; ao virar página, registra a impressão. Sem parceiros
     * disponíveis, a seção fica `GONE`. Tocar abre [PartnerBottomSheet] (WhatsApp/Instagram/Site).
     */
    private fun setupPartners() {
        partnerBannerAdapter = PartnerBannerAdapter(emptyList()) { partner -> onPartnerClicked(partner) }
        binding.partnerPager.adapter = partnerBannerAdapter

        binding.partnerPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePartnerDots(position)
                // Reinicia o timer com o tempo do banner atual e conta a impressão (1ª vez/sessão).
                restartPartnerAutoScroll()
                registerImpressionForPosition(position)
            }
        })

        lifecycleScope.launch {
            val all = try {
                PartnerRepository.load()
            } catch (_: Exception) {
                PartnerRepository.cachedPartners()
            }
            val uid = auth.currentUser?.uid.orEmpty()
            val available = try {
                PartnerImpressionManager.availablePartners(all, uid)
            } catch (_: Exception) {
                all
            }
            applyPartnerBanners(available)
        }
    }

    private fun applyPartnerBanners(partners: List<Partner>) {
        partnerBanners = partners
        if (partners.isEmpty()) {
            binding.sectionPartnerBanners.visibility = View.GONE
            stopPartnerAutoScroll()
            return
        }
        binding.sectionPartnerBanners.visibility = View.VISIBLE
        partnerBannerAdapter?.updateItems(partners)
        buildPartnerDots(partners.size)
        val current = binding.partnerPager.currentItem.coerceIn(0, partners.size - 1)
        updatePartnerDots(current)
        // onPageSelected não dispara de forma confiável para a posição inicial — conta aqui.
        registerImpressionForPosition(current)
        startPartnerAutoScroll()
    }

    /** Conta a impressão do parceiro na posição dada (1ª vez por parceiro nesta sessão). */
    private fun registerImpressionForPosition(position: Int) {
        val partner = partnerBanners.getOrNull(position) ?: return
        if (!registeredImpressions.add(partner.id)) return
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        lifecycleScope.launch {
            PartnerImpressionManager.registerImpression(partner, uid)
        }
    }

    private fun buildPartnerDots(count: Int) {
        binding.partnerDots.removeAllViews()
        val size = dpToPx(8)
        val margin = dpToPx(3)
        repeat(count) {
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginStart = margin
            lp.marginEnd = margin
            dot.layoutParams = lp
            dot.setBackgroundResource(R.drawable.banner_dot_inactive)
            binding.partnerDots.addView(dot)
        }
        binding.partnerDots.visibility = if (count > 1) View.VISIBLE else View.GONE
    }

    private fun updatePartnerDots(active: Int) {
        for (i in 0 until binding.partnerDots.childCount) {
            binding.partnerDots.getChildAt(i).setBackgroundResource(
                if (i == active) R.drawable.banner_dot_active else R.drawable.banner_dot_inactive
            )
        }
    }

    private fun startPartnerAutoScroll() {
        if (partnerBanners.size <= 1) return
        stopPartnerAutoScroll()
        // Cada parceiro fica X segundos (config do painel) antes de rodar para o próximo.
        val current = binding.partnerPager.currentItem
        val seconds = partnerBanners.getOrNull(current)?.rotationSeconds ?: Partner.DEFAULT_ROTATION_SECONDS
        val runnable = Runnable {
            if (partnerBanners.size <= 1) return@Runnable
            val next = (binding.partnerPager.currentItem + 1) % partnerBanners.size
            binding.partnerPager.setCurrentItem(next, true)
        }
        partnerAutoScroll = runnable
        partnerHandler.postDelayed(runnable, seconds * 1000L)
    }

    private fun restartPartnerAutoScroll() {
        stopPartnerAutoScroll()
        startPartnerAutoScroll()
    }

    private fun stopPartnerAutoScroll() {
        partnerAutoScroll?.let { partnerHandler.removeCallbacks(it) }
    }

    private fun onPartnerClicked(partner: Partner) {
        logPartnerClick(partner)
        PartnerBottomSheet.newInstance(partner.id)
            .show(supportFragmentManager, PartnerBottomSheet.TAG)
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
            // Gancho do plano 06: sem resultado textual → leva ao Assistente IA com a busca já
            // preenchida (que por sua vez tem fallback "ver todos os serviços").
            startActivity(
                Intent(this, AssistantChatActivity::class.java).apply {
                    if (lastSearchQuery.isNotBlank()) {
                        putExtra(AssistantChatActivity.EXTRA_PREFILL, lastSearchQuery)
                    }
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
