package com.example.loginapp

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.loginapp.databinding.ActivityUserDashboardBinding

import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

class UserDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserDashboardBinding
    private lateinit var authManager: FirebaseAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Garantir Firebase inicializado
        if (!FirebaseConfig.isInitialized()) {
            FirebaseConfig.initialize(this)
        }
        
        binding = ActivityUserDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = FirebaseAuthManager(this)
        setupToolbar()
        setupClickListeners()
        loadUserData()
        loadDashboardData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Dashboard"
        }
    }

    private fun setupClickListeners() {
        // Cards de ação rápida
        binding.cardCreateOrder.setOnClickListener {
            // Pedidos só podem ser feitos pela aba Serviços
            val intent = Intent(this, ServicesActivity::class.java)
            startActivity(intent)
        }

        binding.cardMyOrders.setOnClickListener {
            val intent = Intent(this, ClientOrdersActivity::class.java)
            startActivity(intent)
        }

        binding.cardFavorites.setOnClickListener {
            showToast("❤️ Favoritos em desenvolvimento...")
        }

        binding.cardProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        // Botões de ação
        binding.btnViewAllOrders.setOnClickListener {
            val intent = Intent(this, ClientOrdersActivity::class.java)
            startActivity(intent)
        }



        binding.btnViewAllBadges.setOnClickListener {
            showBadgesDialog()
        }

        // Cards de estatísticas
        binding.cardStatistics.setOnClickListener {
            showDetailedStatistics()
        }

        binding.cardSavings.setOnClickListener {
            showSavingsDetails()
        }
    }

    private fun loadUserData() {
        val user = authManager.getLocalUserData()
        if (user != null) {
            binding.tvUserName.text = "Olá, ${user.username}!"
            binding.tvUserLevel.text = getUserLevel(user.username)
            binding.tvUserLevelBadge.text = getUserLevel(user.username)
        }
    }

    private fun loadDashboardData() {
        lifecycleScope.launch {
            try {
                loadStatistics()
                loadRecentOrders()
                loadExpensesAnalysis()
                loadBadges()
                loadRealTimeStatus()
            } catch (e: Exception) {
                showToast("❌ Erro ao carregar dados: ${e.message}")
            }
        }
    }

    private fun loadStatistics() {
        // Estatísticas simuladas - em produção viriam do Firebase
        val totalOrders = 15
        val totalSpent = 1850.0
        val savedMoney = 320.0
        val favoriteProviders = 5
        val completedOrders = 12
        val pendingOrders = 3

        binding.tvTotalOrders.text = totalOrders.toString()
        binding.tvTotalSpent.text = formatCurrency(totalSpent)
        binding.tvSavedMoney.text = formatCurrency(savedMoney)
        binding.tvFavoriteProviders.text = favoriteProviders.toString()
        binding.tvCompletedOrders.text = completedOrders.toString()
        binding.tvPendingOrders.text = pendingOrders.toString()

        // Calcular economia percentual
        val savingsPercentage = ((savedMoney / (totalSpent + savedMoney)) * 100).toInt()
        binding.tvSavingsPercentage.text = "$savingsPercentage%"

        // Configurar cores baseadas nos valores
        binding.tvTotalSpent.setTextColor(ContextCompat.getColor(this, R.color.primary_color))
        binding.tvSavedMoney.setTextColor(ContextCompat.getColor(this, R.color.success_green))
        binding.tvCompletedOrders.setTextColor(ContextCompat.getColor(this, R.color.success_green))
        binding.tvPendingOrders.setTextColor(ContextCompat.getColor(this, R.color.warning_orange))
    }

    private fun loadRecentOrders() {
        // Simular pedidos recentes com status em tempo real
        val recentOrders = listOf(
            OrderStatus("Pedido #1234", "Elétrica", "Concluído", R.color.success_green),
            OrderStatus("Pedido #1235", "Limpeza", "Em andamento", R.color.warning_orange),
            OrderStatus("Pedido #1236", "Pintura", "Aguardando", R.color.primary_color),
            OrderStatus("Pedido #1237", "Encanador", "Agendado", R.color.info_blue)
        )

        binding.tvRecentOrder1.text = "${recentOrders[0].id} - ${recentOrders[0].service}"
        binding.tvRecentOrder1.setTextColor(ContextCompat.getColor(this, recentOrders[0].color))
        binding.tvRecentStatus1.text = recentOrders[0].status

        binding.tvRecentOrder2.text = "${recentOrders[1].id} - ${recentOrders[1].service}"
        binding.tvRecentOrder2.setTextColor(ContextCompat.getColor(this, recentOrders[1].color))
        binding.tvRecentStatus2.text = recentOrders[1].status

        binding.tvRecentOrder3.text = "${recentOrders[2].id} - ${recentOrders[2].service}"
        binding.tvRecentOrder3.setTextColor(ContextCompat.getColor(this, recentOrders[2].color))
        binding.tvRecentStatus3.text = recentOrders[2].status

        binding.tvRecentOrder4.text = "${recentOrders[3].id} - ${recentOrders[3].service}"
        binding.tvRecentOrder4.setTextColor(ContextCompat.getColor(this, recentOrders[3].color))
        binding.tvRecentStatus4.text = recentOrders[3].status
    }

    private fun loadExpensesAnalysis() {
        // Dados de gastos mensais
        val monthlyExpenses = listOf(250.0, 320.0, 180.0, 450.0, 380.0, 270.0)
        val currentMonth = 270.0
        val monthlySavings = 45.0
        
        // Atualizar dados do mês atual
        binding.tvMonthlyTotal.text = formatCurrency(currentMonth)
        binding.tvMonthlySavings.text = formatCurrency(monthlySavings)
        
        // Atualizar mês atual
        val calendar = Calendar.getInstance()
        val monthNames = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", 
                               "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
        val currentMonthName = monthNames[calendar.get(Calendar.MONTH)]
        val currentYear = calendar.get(Calendar.YEAR)
        binding.tvCurrentMonth.text = "$currentMonthName $currentYear"
    }

    private fun loadBadges() {
        // Simular badges/conquistas
        val badges = listOf(
            Badge("Primeiro Pedido", "🎯", "Realizou o primeiro pedido", true),
            Badge("Cliente Fiel", "💎", "5 pedidos realizados", true),
            Badge("Economista", "💰", "Economizou R$ 100+", true),
            Badge("Avaliador", "⭐", "Avaliou 3 prestadores", true),
            Badge("Explorador", "🗺️", "Usou 5 categorias diferentes", false),
            Badge("VIP", "👑", "10 pedidos realizados", false)
        )

        // Mostrar badges desbloqueadas
        val unlockedBadges = badges.filter { it.unlocked }
        binding.tvBadge1.text = "${unlockedBadges[0].emoji} ${unlockedBadges[0].name}"
        binding.tvBadge2.text = "${unlockedBadges[1].emoji} ${unlockedBadges[1].name}"
        binding.tvBadge3.text = "${unlockedBadges[2].emoji} ${unlockedBadges[2].name}"
        binding.tvBadge4.text = "${unlockedBadges[3].emoji} ${unlockedBadges[3].name}"

        // Mostrar progresso para próximo badge
        binding.tvNextBadge.text = "Próximo: ${badges[4].emoji} ${badges[4].name}"
        binding.tvNextBadgeDescription.text = badges[4].description
    }

    private fun loadRealTimeStatus() {
        // Simular status em tempo real
        binding.tvActiveOrders.text = "2 pedidos ativos"
        binding.tvNextAppointment.text = "Limpeza - Amanhã 14:00"
        binding.tvPendingPayments.text = "R$ 150,00 pendente"
    }



    private fun getUserLevel(username: String): String {
        // Simular sistema de níveis baseado no username
        return when {
            username.length > 10 -> "Diamante"
            username.length > 8 -> "Ouro"
            username.length > 6 -> "Prata"
            else -> "Bronze"
        }
    }

    private fun showBadgesDialog() {
        val badges = listOf(
            Badge("Primeiro Pedido", "🎯", "Realizou o primeiro pedido", true),
            Badge("Cliente Fiel", "💎", "5 pedidos realizados", true),
            Badge("Economista", "💰", "Economizou R$ 100+", true),
            Badge("Avaliador", "⭐", "Avaliou 3 prestadores", true),
            Badge("Explorador", "🗺️", "Usou 5 categorias diferentes", false),
            Badge("VIP", "👑", "10 pedidos realizados", false),
            Badge("Mestre", "🏆", "20 pedidos realizados", false)
        )

        val message = badges.joinToString("\n") { badge ->
            "${badge.emoji} ${badge.name} - ${badge.description} ${if (badge.unlocked) "✅" else "🔒"}"
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🏆 Badges & Conquistas")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showDetailedStatistics() {
        showToast("📊 Estatísticas detalhadas em desenvolvimento...")
    }

    private fun showSavingsDetails() {
        showToast("💰 Detalhes de economia em desenvolvimento...")
    }

    private fun formatCurrency(value: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        return formatter.format(value)
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    // Classes de dados
    data class OrderStatus(
        val id: String,
        val service: String,
        val status: String,
        val color: Int
    )

    data class Badge(
        val name: String,
        val emoji: String,
        val description: String,
        val unlocked: Boolean
    )
}
