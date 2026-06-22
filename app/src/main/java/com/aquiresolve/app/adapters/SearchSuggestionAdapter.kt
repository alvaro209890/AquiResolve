package com.aquiresolve.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.R
import com.aquiresolve.app.models.SearchSuggestion

/**
 * Adapter da Busca Inteligente da Home. Lista [SearchSuggestion] (serviços/nichos do catálogo)
 * abaixo do campo de busca; o clique dispara [onClick] para o roteamento pré-preenchido.
 *
 * Para sugestões de nicho usa um ícone diferente (mais "amplo") só para diferenciar visualmente
 * de um serviço específico.
 */
class SearchSuggestionAdapter(
    private var items: List<SearchSuggestion>,
    private val onClick: (SearchSuggestion) -> Unit
) : RecyclerView.Adapter<SearchSuggestionAdapter.SuggestionViewHolder>() {

    inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.ivSuggestionIcon)
        val label: TextView = itemView.findViewById(R.id.tvSuggestionLabel)
        val niche: TextView = itemView.findViewById(R.id.tvSuggestionNiche)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_suggestion, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val item = items[position]
        holder.label.text = item.label
        if (item.type == SearchSuggestion.Type.NICHE) {
            holder.icon.setImageResource(R.drawable.ic_services)
            holder.niche.text = "Ver serviços de ${item.niche}"
        } else {
            holder.icon.setImageResource(R.drawable.ic_search)
            holder.niche.text = item.niche
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    fun updateItems(newItems: List<SearchSuggestion>) {
        items = newItems
        notifyDataSetChanged()
    }
}
