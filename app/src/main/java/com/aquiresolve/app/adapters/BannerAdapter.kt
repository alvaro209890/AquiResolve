package com.aquiresolve.app.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.R
import com.aquiresolve.app.models.HomeBanner
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.card.MaterialCardView

/**
 * Adapter do carrossel de banners da Home, plugado no `ViewPager2`.
 *
 * Carrega a imagem com Glide ([DiskCacheStrategy.ALL] + dimensão fixa pelo pager — recomenda-se
 * banners ~1200×500). Enquanto a imagem chega, usa a `backgroundColor` do banner como placeholder.
 * Título/subtítulo só aparecem quando preenchidos. O clique dispara [onBannerClick] para roteamento.
 */
class BannerAdapter(
    private var banners: List<HomeBanner>,
    private val onBannerClick: (HomeBanner) -> Unit
) : RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

    inner class BannerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: MaterialCardView = itemView as MaterialCardView
        val bannerRoot: View = itemView.findViewById(R.id.bannerRoot)
        val image: android.widget.ImageView = itemView.findViewById(R.id.ivBannerImage)
        val title: android.widget.TextView = itemView.findViewById(R.id.tvBannerTitle)
        val subtitle: android.widget.TextView = itemView.findViewById(R.id.tvBannerSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_banner, parent, false)
        return BannerViewHolder(view)
    }

    override fun getItemCount(): Int = banners.size

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        val banner = banners[position]

        // Placeholder com a cor de fundo do banner enquanto a imagem carrega.
        val placeholderColor = parseColorOrNull(banner.backgroundColor)
        if (placeholderColor != null) {
            holder.bannerRoot.setBackgroundColor(placeholderColor)
        }

        Glide.with(holder.image.context)
            .load(banner.imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .into(holder.image)

        if (banner.title.isNotBlank()) {
            holder.title.text = banner.title
            holder.title.visibility = View.VISIBLE
        } else {
            holder.title.visibility = View.GONE
        }

        if (banner.subtitle.isNotBlank()) {
            holder.subtitle.text = banner.subtitle
            holder.subtitle.visibility = View.VISIBLE
        } else {
            holder.subtitle.visibility = View.GONE
        }

        holder.root.setOnClickListener { onBannerClick(banner) }
    }

    fun updateItems(newItems: List<HomeBanner>) {
        banners = newItems
        notifyDataSetChanged()
    }

    private fun parseColorOrNull(hex: String): Int? {
        if (hex.isBlank()) return null
        return try {
            Color.parseColor(hex.trim())
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
