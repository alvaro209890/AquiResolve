package com.aquiresolve.app.models

/**
 * Parceiro patrocinador exibido na seção "Parceiros AquiResolve" da Home
 * (coleção Firestore `partners`, gerida pelo painel admin em
 * `/dashboard/configuracoes/parceiros` via Admin SDK — o app só lê).
 *
 * O benefício é apenas informativo/vitrine: [benefitType] define como o cliente aproveita
 * (desconto/cashback/cupom/link). Quando [benefitType] = `coupon`, o app mostra o [couponCode]
 * com botão "Copiar"; quando há [url], mostra "Visitar site". Nenhuma validação real de cupom é
 * feita aqui (fora do escopo — ver plano 04).
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
    val active: Boolean = true,
    val displayOrder: Int = 0
) {
    companion object {
        const val TYPE_DISCOUNT = "discount"
        const val TYPE_CASHBACK = "cashback"
        const val TYPE_COUPON = "coupon"
        const val TYPE_LINK = "link"
    }

    /** Tem cupom copiável (tipo cupom + código preenchido). */
    fun hasCoupon(): Boolean = benefitType.equals(TYPE_COUPON, ignoreCase = true) && couponCode.isNotBlank()

    /** Tem link externo para "Visitar site". */
    fun hasUrl(): Boolean = url.isNotBlank()
}
