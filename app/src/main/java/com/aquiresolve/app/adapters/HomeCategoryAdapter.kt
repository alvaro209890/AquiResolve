package com.aquiresolve.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.R
import com.aquiresolve.app.databinding.ItemHomeCategoryBinding

/**
 * Adapter da lista horizontal de Categorias (nichos) da Home.
 *
 * Alimentado pelo catálogo dinâmico ([CatalogRepository] → [ServiceNicheCatalog]); reaproveita o
 * mapeamento de ícones de [ServiceCategoriesAdapter.resolveIcon]. O campo `icon` do catálogo pode ser:
 *  - um **emoji literal** (ex.: "⚡") → renderizado direto no `TextView` (reflete sem novo APK), ou
 *  - um **slug de drawable** (ex.: "electrician") → mapeado para um `@drawable` via `resolveIcon`.
 *
 * O último item é sintético ("Ver todos") e dispara [onSeeAll] em vez de [onNicheClick].
 */
class HomeCategoryAdapter(
    private var niches: List<Item>,
    private val onNicheClick: (Item) -> Unit,
    private val onSeeAll: () -> Unit
) : RecyclerView.Adapter<HomeCategoryAdapter.CategoryViewHolder>() {

    /** Item da lista; `seeAll = true` representa o atalho sintético "Ver todos". */
    data class Item(val name: String, val icon: String, val seeAll: Boolean = false)

    inner class CategoryViewHolder(private val binding: ItemHomeCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item) {
            binding.tvCategoryName.text = item.name

            if (item.seeAll) {
                binding.tvCategoryEmoji.visibility = View.GONE
                binding.ivCategoryIcon.visibility = View.VISIBLE
                binding.ivCategoryIcon.setImageResource(R.drawable.ic_arrow_right)
                itemView.setOnClickListener { onSeeAll() }
                return
            }

            if (isEmoji(item.icon)) {
                binding.ivCategoryIcon.visibility = View.GONE
                binding.tvCategoryEmoji.visibility = View.VISIBLE
                binding.tvCategoryEmoji.text = item.icon
            } else {
                binding.tvCategoryEmoji.visibility = View.GONE
                binding.ivCategoryIcon.visibility = View.VISIBLE
                binding.ivCategoryIcon.setImageResource(
                    ServiceCategoriesAdapter.resolveIcon(item.icon)
                )
            }
            itemView.setOnClickListener { onNicheClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemHomeCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) =
        holder.bind(niches[position])

    override fun getItemCount(): Int = niches.size

    fun updateItems(newItems: List<Item>) {
        niches = newItems
        notifyDataSetChanged()
    }

    companion object {
        /**
         * Heurística: o `icon` é um emoji literal quando o primeiro code point está fora do ASCII
         * (slugs como "electrician"/"wrench" são sempre ASCII). Pictogramas/emoji ficam acima de 0x2000.
         */
        fun isEmoji(icon: String): Boolean {
            if (icon.isBlank()) return false
            return icon.codePointAt(0) > 0x2000
        }
    }
}
