package com.aquiresolve.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.databinding.ActivityProviderFinancialBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

data class CommissionEntry(
    val orderId: String,
    val protocol: String,
    val serviceName: String,
    val commission: Double,
    val completedAt: com.google.firebase.Timestamp?
)

class ProviderFinancialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProviderFinancialBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val brl = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderFinancialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Financeiro"

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.swipeRefresh.setOnRefreshListener { loadData() }

        loadData()
    }

    private fun loadData() {
        val uid = auth.currentUser?.uid ?: return
        binding.swipeRefresh.isRefreshing = true
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Lê saldo do providers/{uid} com fallback para users/{uid}
                val providerSnap = db.collection("providers").document(uid).get().await()
                val balance = (providerSnap.getDouble("providerBalance") ?: 0.0)
                val totalEarned = (providerSnap.getDouble("providerTotalEarned") ?: 0.0)

                binding.tvBalance.text = brl.format(balance)
                binding.tvTotalEarned.text = brl.format(totalEarned)

                // Busca pedidos concluídos do prestador para exibir histórico de comissões
                val ordersSnap = db.collection("orders")
                    .whereEqualTo("assignedProvider", uid)
                    .whereEqualTo("status", "completed")
                    .orderBy("completedAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .get().await()

                val entries = ordersSnap.documents.mapNotNull { doc ->
                    val commission = doc.getDouble("providerCommission") ?: return@mapNotNull null
                    CommissionEntry(
                        orderId = doc.id,
                        protocol = doc.getString("protocol") ?: doc.id.take(8),
                        serviceName = doc.getString("serviceName") ?: doc.getString("serviceType") ?: "Serviço",
                        commission = commission,
                        completedAt = doc.getTimestamp("completedAt")
                    )
                }

                binding.tvOrderCount.text = "${entries.size} serviço(s) concluído(s)"
                renderList(entries)

            } catch (e: Exception) {
                Toast.makeText(this@ProviderFinancialActivity,
                    "Erro ao carregar dados: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun renderList(entries: List<CommissionEntry>) {
        binding.containerOrders.removeAllViews()

        if (entries.isEmpty()) {
            val empty = layoutInflater.inflate(R.layout.item_empty_state, binding.containerOrders, false)
            binding.containerOrders.addView(empty)
            return
        }

        for (entry in entries) {
            val item = layoutInflater.inflate(R.layout.item_commission, binding.containerOrders, false)
            item.findViewById<android.widget.TextView>(R.id.tvProtocol).text = entry.protocol
            item.findViewById<android.widget.TextView>(R.id.tvServiceName).text = entry.serviceName
            item.findViewById<android.widget.TextView>(R.id.tvCommission).text = brl.format(entry.commission)
            item.findViewById<android.widget.TextView>(R.id.tvDate).text =
                entry.completedAt?.toDate()?.let { sdf.format(it) } ?: "—"
            binding.containerOrders.addView(item)
        }
    }
}
