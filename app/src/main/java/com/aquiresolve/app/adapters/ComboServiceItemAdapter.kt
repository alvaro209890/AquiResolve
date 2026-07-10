package com.aquiresolve.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.R
import java.util.Locale

/**
 * Adapter simples da lista de serviços incluídos num combo (tela de detalhe).
 * Cada linha mostra o nome do serviço (prefixado com "N× " quando a quantidade é maior que 1),
 * o nicho e o preço total da linha (unitário × quantidade, resolvido do catálogo).
 */
class ComboServiceItemAdapter(
    private val items: List<Row>
) : RecyclerView.Adapter<ComboServiceItemAdapter.RowViewHolder>() {

    data class Row(val name: String, val niche: String, val price: Double, val quantity: Int = 1) {
        /** Preço total da linha (unitário × quantidade). */
        val totalPrice: Double get() = price * quantity
    }

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.tvItemName)
        val niche: TextView = itemView.findViewById(R.id.tvItemNiche)
        val price: TextView = itemView.findViewById(R.id.tvItemPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_combo_detail_service, parent, false)
        return RowViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val row = items[position]
        holder.name.text = if (row.quantity > 1) "${row.quantity}× ${row.name}" else row.name
        holder.niche.text = row.niche
        if (row.totalPrice > 0) {
            holder.price.text = String.format(Locale("pt", "BR"), "R$ %.2f", row.totalPrice)
            holder.price.visibility = View.VISIBLE
        } else {
            holder.price.visibility = View.GONE
        }
    }
}
