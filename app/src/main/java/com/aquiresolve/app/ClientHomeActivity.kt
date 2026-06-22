package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.aquiresolve.app.adapters.HomeCategoryAdapter
import com.aquiresolve.app.databinding.ActivityClientHomeBinding
import com.aquiresolve.app.models.OrderData
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
    }

    override fun onPause() {
        super.onPause()
        // Parar listener quando sair da tela pra evitar vazamento
        NotificationBadgeHelper.stopListening()
        centralUnreadListener?.remove()
        centralUnreadListener = null
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
