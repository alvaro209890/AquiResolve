package com.aquiresolve.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aquiresolve.app.databinding.ActivityPartnerDetailBinding
import com.aquiresolve.app.models.Partner
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

/**
 * Detalhe de um parceiro patrocinador (plano 04).
 *
 * Mostra banner/logo, descrição e o benefício em destaque. Quando o benefício é um cupom, exibe
 * o código com botão "Copiar" ([ClipboardManager]); quando há `url`, exibe "Visitar site"
 * ([Intent.ACTION_VIEW]). Nenhuma validação real de cupom é feita (fora do escopo — só exibe/copia).
 *
 * O parceiro é lido do cache ([PartnerRepository.cachedPartnerById]) via o extra [EXTRA_PARTNER_ID],
 * evitando passar objeto grande por Intent.
 */
class PartnerDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PARTNER_ID = "partner_id"
    }

    private lateinit var binding: ActivityPartnerDetailBinding
    private var partner: Partner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPartnerDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val partnerId = intent.getStringExtra(EXTRA_PARTNER_ID).orEmpty()
        val current = PartnerRepository.cachedPartnerById(partnerId)
        if (current == null) {
            Toast.makeText(this, "Parceiro indisponível", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        partner = current

        binding.btnBack.setOnClickListener { finish() }
        bind(current)
    }

    private fun bind(partner: Partner) {
        binding.tvPartnerName.text = partner.name

        // Banner se houver; senão o logo; senão esconde.
        val image = partner.bannerUrl.ifBlank { partner.logoUrl }
        if (image.isNotBlank()) {
            binding.ivPartnerBanner.visibility = View.VISIBLE
            Glide.with(this)
                .load(image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .into(binding.ivPartnerBanner)
        } else {
            binding.ivPartnerBanner.visibility = View.GONE
        }

        if (partner.benefitLabel.isNotBlank()) {
            binding.tvPartnerBenefit.text = partner.benefitLabel
            binding.tvPartnerBenefit.visibility = View.VISIBLE
        } else {
            binding.tvPartnerBenefit.visibility = View.GONE
        }

        if (partner.description.isNotBlank()) {
            binding.tvPartnerDescription.text = partner.description
            binding.tvPartnerDescription.visibility = View.VISIBLE
        } else {
            binding.tvPartnerDescription.visibility = View.GONE
        }

        // Cupom copiável.
        if (partner.hasCoupon()) {
            binding.sectionCoupon.visibility = View.VISIBLE
            binding.tvCouponCode.text = partner.couponCode
            binding.btnCopyCoupon.setOnClickListener { copyCoupon(partner) }
        } else {
            binding.sectionCoupon.visibility = View.GONE
        }

        // Link externo.
        if (partner.hasUrl()) {
            binding.btnVisitSite.visibility = View.VISIBLE
            binding.btnVisitSite.setOnClickListener { openSite(partner) }
        } else {
            binding.btnVisitSite.visibility = View.GONE
        }
    }

    private fun copyCoupon(partner: Partner) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Cupom ${partner.name}", partner.couponCode))
            Toast.makeText(this, "Cupom copiado: ${partner.couponCode}", Toast.LENGTH_SHORT).show()
            logEvent("parceiro_cupom_copiado", partner)
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível copiar o cupom", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSite(partner: Partner) {
        try {
            var url = partner.url.trim()
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                url = "https://$url"
            }
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            logEvent("parceiro_link_aberto", partner)
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível abrir o site", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logEvent(name: String, partner: Partner) {
        try {
            FirebaseConfig.getAnalytics()?.logEvent(
                name,
                Bundle().apply {
                    putString("partnerId", partner.id)
                    putString("partnerName", partner.name)
                }
            )
        } catch (_: Exception) {
        }
    }
}
