package com.aquiresolve.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.R
import com.aquiresolve.app.models.Partner
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.card.MaterialCardView

/**
 * Adapter do carrossel de **banners de parceiros** da Home, plugado no `ViewPager2`.
 *
 * Espelha [BannerAdapter]: carrega o banner do parceiro com Glide ([DiskCacheStrategy.ALL] +
 * dimensão fixa pelo pager — recomenda-se ~1200×500). Usa [Partner.bannerImage] (banner ou,
 * na falta, o logo). Nome sempre visível; benefício só quando preenchido. O clique dispara
 * [onPartnerClick] para abrir o detalhe com os contatos do parceiro.
 */
class PartnerBannerAdapter(
    private var partners: List<Partner>,
    private val onPartnerClick: (Partner) -> Unit
) : RecyclerView.Adapter<PartnerBannerAdapter.PartnerBannerViewHolder>() {

    inner class PartnerBannerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: MaterialCardView = itemView as MaterialCardView
        val image: ImageView = itemView.findViewById(R.id.ivPartnerBannerImage)
        val name: TextView = itemView.findViewById(R.id.tvPartnerBannerName)
        val benefit: TextView = itemView.findViewById(R.id.tvPartnerBannerBenefit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartnerBannerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_partner_banner, parent, false)
        return PartnerBannerViewHolder(view)
    }

    override fun getItemCount(): Int = partners.size

    override fun onBindViewHolder(holder: PartnerBannerViewHolder, position: Int) {
        val partner = partners[position]

        Glide.with(holder.image.context)
            .load(partner.bannerImage())
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .into(holder.image)

        holder.name.text = partner.name

        if (partner.benefitLabel.isNotBlank()) {
            holder.benefit.text = partner.benefitLabel
            holder.benefit.visibility = View.VISIBLE
        } else {
            holder.benefit.visibility = View.GONE
        }

        holder.root.setOnClickListener { onPartnerClick(partner) }
    }

    fun updateItems(newItems: List<Partner>) {
        partners = newItems
        notifyDataSetChanged()
    }
}
