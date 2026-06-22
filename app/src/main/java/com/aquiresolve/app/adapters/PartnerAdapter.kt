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

/**
 * Adapter da seção horizontal "Parceiros AquiResolve" da Home.
 *
 * Cada card mostra o logo do parceiro (Glide, fitCenter em fundo branco p/ não distorcer),
 * o nome e o rótulo de benefício em pill verde. O clique dispara [onPartnerClick] para abrir
 * o detalhe do parceiro.
 */
class PartnerAdapter(
    private var partners: List<Partner>,
    private val onPartnerClick: (Partner) -> Unit
) : RecyclerView.Adapter<PartnerAdapter.PartnerViewHolder>() {

    inner class PartnerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val logo: ImageView = itemView.findViewById(R.id.ivPartnerLogo)
        val name: TextView = itemView.findViewById(R.id.tvPartnerName)
        val benefit: TextView = itemView.findViewById(R.id.tvPartnerBenefit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartnerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_partner, parent, false)
        return PartnerViewHolder(view)
    }

    override fun getItemCount(): Int = partners.size

    override fun onBindViewHolder(holder: PartnerViewHolder, position: Int) {
        val partner = partners[position]

        holder.name.text = partner.name

        if (partner.benefitLabel.isNotBlank()) {
            holder.benefit.text = partner.benefitLabel
            holder.benefit.visibility = View.VISIBLE
        } else {
            holder.benefit.visibility = View.GONE
        }

        if (partner.logoUrl.isNotBlank()) {
            Glide.with(holder.logo.context)
                .load(partner.logoUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .into(holder.logo)
        } else {
            holder.logo.setImageResource(R.mipmap.ic_launcher)
        }

        holder.itemView.setOnClickListener { onPartnerClick(partner) }
    }

    fun updateItems(newItems: List<Partner>) {
        partners = newItems
        notifyDataSetChanged()
    }
}
