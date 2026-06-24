package com.aquiresolve.app.adapters

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.CatalogServiceRepository
import com.aquiresolve.app.R
import com.aquiresolve.app.models.HomeCombo
import com.aquiresolve.app.models.HomeComboItem
import com.aquiresolve.app.models.ServicePricing
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.card.MaterialCardView
import java.util.Locale

/**
 * Adapter da vitrine horizontal de Combos Promocionais da Home.
 *
 * Cada card mostra foto (Glide, com fallback na cor primária), nome, descrição, preço cheio
 * riscado, preço promocional em destaque e o badge de economia. O clique dispara [onComboClick]
 * para abrir o detalhe do combo.
 *
 * Preços são calculados ao vivo do [CatalogServiceRepository] (já em cache pelo AppApplication),
 * nunca dos campos estáticos fullPrice/promoPrice/savings do combo — esses podem estar
 * desatualizados se o admin tiver alterado preços no catálogo após criar o combo.
 */
class HomeComboAdapter(
    private var combos: List<HomeCombo>,
    private val onComboClick: (HomeCombo) -> Unit
) : RecyclerView.Adapter<HomeComboAdapter.ComboViewHolder>() {

    inner class ComboViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
        val image: ImageView = itemView.findViewById(R.id.ivComboImage)
        val name: TextView = itemView.findViewById(R.id.tvComboName)
        val description: TextView = itemView.findViewById(R.id.tvComboDescription)
        val fullPrice: TextView = itemView.findViewById(R.id.tvComboFullPrice)
        val promoPrice: TextView = itemView.findViewById(R.id.tvComboPromoPrice)
        val savings: TextView = itemView.findViewById(R.id.tvComboSavings)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComboViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_combo, parent, false)
        return ComboViewHolder(view)
    }

    override fun getItemCount(): Int = combos.size

    override fun onBindViewHolder(holder: ComboViewHolder, position: Int) {
        val combo = combos[position]

        holder.name.text = combo.name

        if (combo.description.isNotBlank()) {
            holder.description.text = combo.description
            holder.description.visibility = View.VISIBLE
        } else {
            holder.description.visibility = View.GONE
        }

        // Preços calculados ao vivo do catálogo em cache — nunca dos campos estáticos do combo.
        val liveFullPrice = combo.items.sumOf { resolveItemPrice(it) }
        val livePromoPrice = if (combo.discountPercent > 0)
            Math.round(liveFullPrice * (1.0 - combo.discountPercent / 100.0) * 100) / 100.0
        else liveFullPrice
        val liveSavings = Math.round((liveFullPrice - livePromoPrice) * 100) / 100.0

        if (liveFullPrice > 0 && liveFullPrice > livePromoPrice) {
            holder.fullPrice.text = formatMoney(liveFullPrice)
            holder.fullPrice.paintFlags = holder.fullPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.fullPrice.visibility = View.VISIBLE
        } else {
            holder.fullPrice.visibility = View.GONE
        }

        val displayPromo = if (livePromoPrice > 0) livePromoPrice else liveFullPrice
        if (displayPromo > 0) {
            holder.promoPrice.text = formatMoney(displayPromo)
            holder.promoPrice.visibility = View.VISIBLE
        } else {
            holder.promoPrice.visibility = View.GONE
        }

        if (liveSavings > 0) {
            holder.savings.text = "Economize ${formatMoney(liveSavings)}"
            holder.savings.visibility = View.VISIBLE
        } else {
            holder.savings.visibility = View.GONE
        }

        if (combo.imageUrl.isNotBlank()) {
            holder.image.visibility = View.VISIBLE
            Glide.with(holder.image.context)
                .load(combo.imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(holder.image)
        } else {
            // Sem imagem: mantém o fundo da cor primária do holder (placeholder visual).
            holder.image.visibility = View.GONE
        }

        holder.card.setOnClickListener { onComboClick(combo) }
    }

    fun updateItems(newItems: List<HomeCombo>) {
        combos = newItems
        notifyDataSetChanged()
    }

    /** Resolve o preço do item pelo catálogo dinâmico em cache; fallback na tabela estática. */
    private fun resolveItemPrice(item: HomeComboItem): Double {
        val fromCatalog = CatalogServiceRepository.findService(item.niche, item.serviceName)?.estimatedPrice
        if (fromCatalog != null && fromCatalog > 0) return fromCatalog
        return ServicePricing.getPrice(item.niche, item.serviceName)
            ?: ServicePricing.getDefaultPrice(item.niche)
    }

    private fun formatMoney(value: Double): String =
        String.format(Locale("pt", "BR"), "R$ %.2f", value)
}
