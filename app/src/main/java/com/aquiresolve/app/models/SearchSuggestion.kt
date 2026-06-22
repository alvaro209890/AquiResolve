package com.aquiresolve.app.models

/**
 * Sugestão exibida pela Busca Inteligente da Home enquanto o cliente digita.
 *
 * Vem do catálogo dinâmico (nichos de `service_categories` + serviços de `catalog_services`),
 * sem IA — é matching textual em memória. Tocar numa sugestão leva ao fluxo de pedido já
 * pré-preenchido (ver `ClientHomeActivity` / `CreateOrderActivity`).
 *
 * @property label texto exibido (nome do serviço, ex.: "Troca de torneiras", ou do nicho).
 * @property niche nicho ao qual a sugestão pertence (usado no roteamento como `service_category_name`).
 * @property type SERVICE pré-seleciona também o serviço; NICHE abre o nicho.
 */
data class SearchSuggestion(
    val label: String,
    val niche: String,
    val type: Type
) {
    enum class Type { SERVICE, NICHE }
}
