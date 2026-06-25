package com.aquiresolve.app.models

/**
 * Parceiro patrocinador exibido no carrossel de banners "Parceiros AquiResolve" da Home
 * (coleção Firestore `partners`, gerida pelo painel admin em
 * `/dashboard/configuracoes/parceiros` via Admin SDK — o app só lê).
 *
 * Cada parceiro vira um **banner largo** ([bannerImage]) que roda no carrossel a cada
 * [rotationSeconds] segundos. Cada parceiro só aparece para [dailyImpressionCap] clientes
 * distintos por dia (controle em `partner_impressions`, ver `PartnerImpressionManager`).
 *
 * Ao tocar, o cliente vê os contatos do parceiro: [whatsapp], [instagram] e [url] (site).
 * O benefício/cupom continua sendo informativo/vitrine: [benefitType] define como o cliente
 * aproveita (desconto/cashback/cupom/link); quando `coupon`, mostra o [couponCode] copiável.
 */
data class Partner(
    val id: String = "",
    val name: String = "",
    val logoUrl: String = "",
    val bannerUrl: String = "",
    val description: String = "",
    val benefitType: String = "link",
    val benefitLabel: String = "",
    val couponCode: String = "",
    val url: String = "",
    val whatsapp: String = "",
    val instagram: String = "",
    val rotationSeconds: Int = DEFAULT_ROTATION_SECONDS,
    val dailyImpressionCap: Int = DEFAULT_DAILY_CAP,
    val startDate: String = "",
    val campaignDays: Int = 0,
    val active: Boolean = true,
    val displayOrder: Int = 0
) {
    companion object {
        const val TYPE_DISCOUNT = "discount"
        const val TYPE_CASHBACK = "cashback"
        const val TYPE_COUPON = "coupon"
        const val TYPE_LINK = "link"

        const val DEFAULT_ROTATION_SECONDS = 6
        const val DEFAULT_DAILY_CAP = 10
        const val DEFAULT_CAMPAIGN_DAYS = 30
    }

    /** Imagem usada no banner do carrossel (banner largo; cai no logo se não houver banner). */
    fun bannerImage(): String = bannerUrl.ifBlank { logoUrl }

    /** Tem cupom copiável (tipo cupom + código preenchido). */
    fun hasCoupon(): Boolean = benefitType.equals(TYPE_COUPON, ignoreCase = true) && couponCode.isNotBlank()

    /** Tem link externo para "Visitar site". */
    fun hasUrl(): Boolean = url.isNotBlank()

    /** Tem WhatsApp (telefone com dígitos suficientes). */
    fun hasWhatsapp(): Boolean = whatsapp.filter { it.isDigit() }.length >= 10

    /** Tem Instagram (handle ou URL). */
    fun hasInstagram(): Boolean = instagram.isNotBlank()
}
