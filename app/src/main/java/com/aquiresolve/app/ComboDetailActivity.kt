package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aquiresolve.app.adapters.ComboServiceItemAdapter
import com.aquiresolve.app.databinding.ActivityComboDetailBinding
import com.aquiresolve.app.models.CartItemData
import com.aquiresolve.app.models.HomeCombo
import com.aquiresolve.app.models.SavedAddress
import com.aquiresolve.app.models.ServicePricing
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Detalhe de um Combo Promocional (vitrine — plano 03).
 *
 * Mostra os serviços incluídos com preço (de exibição, resolvido do catálogo dinâmico) e permite
 * "Adicionar combo ao carrinho": cada item vira um [CartItemData] no carrinho do cliente, usando
 * **o mesmo fluxo de carrinho já existente**. O desconto NÃO é forçado pelo combo — ao cair no
 * carrinho com as categorias certas, [PromotionManager.computeDiscount] aplica o % automaticamente,
 * garantindo que o valor anunciado == valor cobrado (fonte de verdade: catalog_services + backend).
 *
 * O combo é lido do cache ([ComboRepository.cachedComboById]) via o extra [EXTRA_COMBO_ID],
 * evitando passar objeto grande por Intent.
 */
class ComboDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COMBO_ID = "combo_id"
    }

    private lateinit var binding: ActivityComboDetailBinding
    private val cartManager = FirebaseCartManager()
    private val auth = FirebaseAuth.getInstance()

    private var combo: HomeCombo? = null
    private var resolvedRows: List<ComboServiceItemAdapter.Row> = emptyList()
    private var isAdding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComboDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val comboId = intent.getStringExtra(EXTRA_COMBO_ID).orEmpty()
        val current = ComboRepository.cachedComboById(comboId)
        if (current == null) {
            showToast("Combo indisponível")
            finish()
            return
        }
        combo = current

        binding.btnBack.setOnClickListener { finish() }
        bindHeader(current)
        binding.btnAddComboToCart.setOnClickListener { onAddClicked() }

        loadItemsAndSummary(current)
    }

    private fun bindHeader(combo: HomeCombo) {
        binding.tvComboName.text = combo.name
        if (combo.description.isNotBlank()) {
            binding.tvComboDescription.text = combo.description
            binding.tvComboDescription.visibility = View.VISIBLE
        } else {
            binding.tvComboDescription.visibility = View.GONE
        }
        if (combo.imageUrl.isNotBlank()) {
            binding.ivComboImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(combo.imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(binding.ivComboImage)
        } else {
            binding.ivComboImage.visibility = View.GONE
        }
    }

    /** Resolve o preço de cada item pelo catálogo e monta o resumo (cheio/economia/promo). */
    private fun loadItemsAndSummary(combo: HomeCombo) {
        binding.rvComboItems.layoutManager = LinearLayoutManager(this)
        lifecycleScope.launch {
            // Garante o catálogo em cache para resolver preços (idempotente com o AppApplication).
            withContext(Dispatchers.IO) {
                try {
                    if (CatalogServiceRepository.allCachedServices().isEmpty()) {
                        CatalogServiceRepository.loadAll()
                    }
                } catch (_: Exception) {
                }
            }

            val rows = combo.items.map { item ->
                val price = resolveItemPrice(item.niche, item.serviceName)
                ComboServiceItemAdapter.Row(name = item.serviceName, niche = item.niche, price = price)
            }
            resolvedRows = rows
            binding.rvComboItems.adapter = ComboServiceItemAdapter(rows)

            renderSummary(combo, rows)
        }
    }

    private fun resolveItemPrice(niche: String, serviceName: String): Double {
        val fromCatalog = CatalogServiceRepository.findService(niche, serviceName)?.estimatedPrice
        if (fromCatalog != null && fromCatalog > 0) return fromCatalog
        return ServicePricing.getPrice(niche, serviceName)
            ?: ServicePricing.getDefaultPrice(niche)
    }

    /**
     * Mostra o resumo. Prioriza os valores curados do combo; quando ausentes, calcula a partir dos
     * preços resolvidos e do `discountPercent` (mantendo coerência com o que o carrinho aplicará).
     */
    private fun renderSummary(combo: HomeCombo, rows: List<ComboServiceItemAdapter.Row>) {
        // Preços sempre calculados ao vivo do catálogo — os campos armazenados no combo
        // (fullPrice/promoPrice/savings) podem divergir se o admin alterar preços no catálogo
        // após a criação do combo, gerando expectativa diferente do valor realmente cobrado.
        val full = rows.sumOf { it.price }
        val promo = if (combo.discountPercent > 0) round2(full * (1.0 - combo.discountPercent / 100.0)) else full
        val savings = round2(full - promo)

        binding.tvSummaryFull.text = money(full)
        binding.tvSummaryPromo.text = money(promo)

        if (savings > 0) {
            binding.rowSummarySavings.visibility = View.VISIBLE
            val pct = if (combo.discountPercent > 0) " (${combo.discountPercent}%)" else ""
            binding.tvSummarySavings.text = "- ${money(savings)}$pct"
        } else {
            binding.rowSummarySavings.visibility = View.GONE
        }

        binding.tvDiscountNote.text =
            "O desconto é aplicado automaticamente no carrinho pelas categorias dos serviços. " +
                "O valor cobrado é sempre o do catálogo oficial."
    }

    private fun onAddClicked() {
        if (isAdding) return
        val combo = combo ?: return
        if (auth.currentUser == null) {
            showToast("Faça login para adicionar combos ao carrinho")
            return
        }

        lifecycleScope.launch {
            val addressesResult = FirebaseAddressManager().getUserAddresses()
            val addresses = addressesResult.getOrNull().orEmpty()
            if (addresses.isEmpty()) {
                showToast("Cadastre um endereço para continuar")
                startActivity(Intent(this@ComboDetailActivity, AddressManagementActivity::class.java))
                return@launch
            }
            promptAddress(combo, addresses)
        }
    }

    private fun promptAddress(combo: HomeCombo, addresses: List<SavedAddress>) {
        val labels = addresses.map { "${it.name} — ${it.getShortAddress()}" }.toTypedArray()
        val defaultIndex = addresses.indexOfFirst { it.isDefault }.coerceAtLeast(0)
        var chosen = defaultIndex

        AlertDialog.Builder(this)
            .setTitle("Endereço do atendimento")
            .setSingleChoiceItems(labels, defaultIndex) { _, which -> chosen = which }
            .setPositiveButton("Adicionar") { dialog, _ ->
                dialog.dismiss()
                addComboToCart(combo, addresses[chosen])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addComboToCart(combo: HomeCombo, address: SavedAddress) {
        if (address.coordinates == null) {
            showToast("Esse endereço está sem localização. Edite-o e tente de novo.")
            startActivity(Intent(this, AddressManagementActivity::class.java))
            return
        }

        isAdding = true
        binding.btnAddComboToCart.isEnabled = false
        binding.btnAddComboToCart.text = "Adicionando…"

        lifecycleScope.launch {
            var added = 0
            var failed = 0
            for (item in combo.items) {
                val price = resolveItemPrice(item.niche, item.serviceName)
                val cartItem = CartItemData(
                    serviceType = item.serviceName,
                    serviceNiche = item.niche,
                    description = "Combo: ${combo.name}",
                    address = address.address,
                    zipCode = address.zipCode,
                    complement = address.complement,
                    city = address.city,
                    state = address.state,
                    coordinates = address.coordinates,
                    estimatedPrice = price
                )
                val result = cartManager.addItem(cartItem)
                if (result.isSuccess) added++ else failed++
            }

            isAdding = false
            binding.btnAddComboToCart.isEnabled = true
            binding.btnAddComboToCart.text = "Adicionar combo ao carrinho"

            if (added == 0) {
                showToast("Não foi possível adicionar o combo. Tente novamente.")
                return@launch
            }

            logComboAddCart(combo, added)
            showToast(
                if (failed == 0) "Combo adicionado ao carrinho!"
                else "$added de ${combo.items.size} serviços adicionados ao carrinho"
            )
            startActivity(Intent(this@ComboDetailActivity, ClientCartActivity::class.java))
            finish()
        }
    }

    private fun logComboAddCart(combo: HomeCombo, addedCount: Int) {
        try {
            FirebaseConfig.getAnalytics()?.logEvent(
                "combo_add_cart",
                android.os.Bundle().apply {
                    putString("comboId", combo.id)
                    putString("comboName", combo.name)
                    putInt("itemsAdded", addedCount)
                }
            )
        } catch (_: Exception) {
        }
    }

    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    private fun money(value: Double): String = String.format(Locale("pt", "BR"), "R$ %.2f", value)

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
