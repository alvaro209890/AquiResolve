package com.aquiresolve.app.models

/**
 * Banner promocional exibido no carrossel rotativo do topo da Home do cliente.
 *
 * Os documentos vivem na coleção Firestore `home_banners`, gerenciada pelo painel admin
 * (`/dashboard/configuracoes/banners`) via Admin SDK — o app apenas lê. Por isso, cadastrar,
 * editar ou desativar um banner **não exige novo APK** (conteúdo é dado, não código).
 *
 * @property actionType destino ao tocar: `niche` | `service` | `combos` | `partners` |
 *   `cashback` | `url` | `none`. Tipos desconhecidos ou `actionValue` inválido caem em "sem ação".
 * @property actionValue nicho/serviço/URL conforme o [actionType]; ignorado em combos/partners/cashback/none.
 * @property backgroundColor cor hex (#RRGGBB) usada como placeholder enquanto a imagem do Glide carrega.
 */
data class HomeBanner(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val imageUrl: String = "",
    val actionType: String = ACTION_NONE,
    val actionValue: String = "",
    val backgroundColor: String = "",
    val active: Boolean = true,
    val displayOrder: Int = 0
) {
    companion object {
        const val ACTION_NICHE = "niche"
        const val ACTION_SERVICE = "service"
        const val ACTION_COMBOS = "combos"
        const val ACTION_PARTNERS = "partners"
        const val ACTION_CASHBACK = "cashback"
        const val ACTION_URL = "url"
        const val ACTION_NONE = "none"
    }
}
