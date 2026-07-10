package com.aquiresolve.app.models

/**
 * Serviço que compõe um [HomeCombo]. Aponta para um item do catálogo dinâmico
 * (`catalog_services`) pelo par nicho+nome (o `serviceId` é o id do doc, usado só para
 * referência/rastreio — o app casa por nicho+nome para resolver o preço atual no carrinho).
 *
 * [quantity] = quantas unidades desse serviço o combo inclui (ex.: "1 chuveiro + 3 tomadas").
 * Combos antigos não têm o campo → default 1 (compatível). Ao adicionar ao carrinho, cada
 * unidade vira um item separado — o mesmo fluxo de quem adiciona o serviço N vezes à mão.
 */
data class HomeComboItem(
    val niche: String = "",
    val serviceName: String = "",
    val serviceId: String = "",
    val quantity: Int = 1
)

/**
 * Combo promocional "curado" exibido na vitrine da Home (coleção Firestore `home_combos`,
 * gerida pelo painel admin em `/dashboard/servicos/combos` via Admin SDK — o app só lê).
 *
 * **Preço é exibição, não cobrança.** Os campos [fullPrice]/[promoPrice]/[savings]/[discountPercent]
 * são apenas para a vitrine/curadoria; o valor realmente cobrado continua vindo do fluxo
 * carrinho→backend (`catalog_services` + `PromotionManager.computeDiscount` pelas categorias dos
 * itens). Por isso o combo deve conter itens cujos nichos disparem o desconto anunciado.
 */
data class HomeCombo(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val items: List<HomeComboItem> = emptyList(),
    val fullPrice: Double = 0.0,
    val promoPrice: Double = 0.0,
    val savings: Double = 0.0,
    val discountPercent: Int = 0,
    val active: Boolean = true,
    val displayOrder: Int = 0
)
