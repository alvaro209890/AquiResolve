package com.aquiresolve.app.utils

import com.aquiresolve.app.models.CatalogService
import com.aquiresolve.app.models.SearchSuggestion
import com.aquiresolve.app.models.ServicePricing

/**
 * Utilitário de busca inteligente de serviços.
 * 
 * Mapeia texto livre (ex: "troca de torneira", "consertar torneira", "vazamento")
 * para categorias e tipos de serviço do catálogo.
 */
object ServiceSearchHelper {

    /**
     * Resultado da busca
     */
    data class SearchResult(
        val category: String,       // Nome canônico da categoria (ex: "Encanador")
        val serviceType: String,    // Nome do tipo de serviço (ex: "Troca de torneiras")
        val relevance: Int          // Pontuação de relevância (maior = mais relevante)
    )

    // Mapa de termos de busca → (categoria, tipo de serviço)
    // Usado para busca rápida e precisa
    private val serviceIndex = buildList {
        // Encanador / Hidráulica
        addAll(ServicePricing.getServicesForCategory("Encanador").map {
            Triple(canonicalize("$it encanador hidraulica"), "Encanador", it)
        })
        addAll(ServicePricing.getServicesForCategory("Encanador").map {
            Triple(canonicalize("$it torneira pia cozinha banheiro"), "Encanador", it)
        })

        // Elétrica
        addAll(ServicePricing.getServicesForCategory("Elétrica").map {
            Triple(canonicalize("$it eletrica luz energia"), "Elétrica", it)
        })

        // Instalação
        addAll(ServicePricing.getServicesForCategory("Instalação").map {
            Triple(canonicalize("$it instalacao montagem"), "Instalação", it)
        })

        // Outras categorias direto da tabela de preços
        ServicePricing.getAllCategories().forEach { category ->
            val services = ServicePricing.getServicesForCategory(category)
            services.forEach { service ->
                add(Triple(canonicalize("$service $category"), category, service))
            }
        }
    }

    // Sinônimos para expandir a busca
    private val synonymMap = mapOf(
        "torneira" to listOf("torneira", "torneiras", "torneira monobloco", "reparo torneira"),
        "vazamento" to listOf("vazamento", "vazamentos", "infiltracao", "goteira"),
        "encanamento" to listOf("encanamento", "encanador", "hidraulica", "cano"),
        "eletrica" to listOf("eletrica", "eletricista", "luz", "energia", "curto"),
        "estofado" to listOf("estofado", "sofa", "colchao", "tapete", "cadeira"),
        "desentupimento" to listOf("desentupimento", "desentupir", "entupido", "ralo", "pia", "vaso"),
        "chaveiro" to listOf("chaveiro", "chave", "fechadura", "porta"),
        "automotivo" to listOf("automotivo", "carro", "pneu", "veiculo", "oleo"),
        "montagem" to listOf("montagem", "montar", "movel", "guarda roupa", "estante"),
        "ar condicionado" to listOf("ar condicionado", "ar condicionado", "climatizador", "split"),
        "caixa dagua" to listOf("caixa d agua", "caixa d'água", "reservatorio", "boia"),
        "eletrodomestico" to listOf("eletrodomestico", "geladeira", "fogao", "micro-ondas", "maquina de lavar")
    )

    /**
     * Sugestões instantâneas para a Busca Inteligente da Home (sem IA — matching textual em memória).
     *
     * Casa o texto digitado contra o catálogo dinâmico: [services] (de `catalog_services`) e [niches]
     * (de `service_categories`/fallback). Tolerante a acento e caixa (via [canonicalize]). Ranking:
     * match exato > começa-com > contém > todas as palavras presentes. Quando o catálogo dinâmico
     * ainda não carregou (ex.: offline no primeiro uso), complementa com a base estática [search]
     * (sinônimos), garantindo sugestões mesmo assim. Limita a [limit] itens.
     */
    fun suggest(
        query: String,
        niches: List<String>,
        services: List<CatalogService>,
        limit: Int = 8
    ): List<SearchSuggestion> {
        val q = canonicalize(query)
        if (q.isBlank()) return emptyList()
        val qWords = q.split(" ").filter { it.isNotBlank() }

        // Pontua um texto-alvo contra a query (0 = não casa).
        fun score(target: String): Int {
            val t = canonicalize(target)
            return when {
                t == q -> 100
                t.startsWith(q) -> 80
                t.contains(q) -> 60
                qWords.isNotEmpty() && qWords.all { t.contains(it) } -> 40
                else -> 0
            }
        }

        data class Scored(val suggestion: SearchSuggestion, val relevance: Int)
        val out = mutableListOf<Scored>()
        val seen = HashSet<String>() // dedup por "type|label|niche" (canônico)

        fun add(label: String, niche: String, type: SearchSuggestion.Type, relevance: Int) {
            if (relevance <= 0 || label.isBlank()) return
            val key = "$type|${canonicalize(label)}|${canonicalize(niche)}"
            if (!seen.add(key)) return
            out.add(Scored(SearchSuggestion(label, niche, type), relevance))
        }

        // 1. Nichos que casam → sugestão NICHE (leve bônus para subir um pouco acima de serviços iguais).
        for (niche in niches) {
            add(niche, niche, SearchSuggestion.Type.NICHE, score(niche).let { if (it > 0) it + 5 else 0 })
        }

        // 2. Serviços que casam (pelo nome) → sugestão SERVICE.
        for (s in services) {
            add(s.name, s.niche, SearchSuggestion.Type.SERVICE, score(s.name))
        }

        // 3. Complemento estático (sinônimos) quando o catálogo dinâmico rendeu pouco.
        if (out.size < limit) {
            for (r in search(query)) {
                add(r.serviceType, r.category, SearchSuggestion.Type.SERVICE, 35)
                if (out.size >= limit * 2) break
            }
        }

        return out
            .sortedWith(compareByDescending<Scored> { it.relevance }.thenBy { it.suggestion.label.lowercase() })
            .map { it.suggestion }
            .take(limit)
    }

    /**
     * Busca serviços por texto livre.
     * Retorna resultados ordenados por relevância.
     */
    fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val normalized = canonicalize(query)
        val expandedTerms = expandQuery(normalized)

        val results = mutableMapOf<String, SearchResult>() // key = "$category|$serviceType"

        // 1. Busca exata no índice
        for ((indexText, category, serviceType) in serviceIndex) {
            if (indexText.contains(normalized)) {
                val key = "$category|$serviceType"
                val existing = results[key]
                val relevance = if (indexText.startsWith(normalized)) 100 else 50
                if (existing == null || existing.relevance < relevance) {
                    results[key] = SearchResult(category, serviceType, relevance)
                }
            }
        }

        // 2. Busca por termos expandidos (sinônimos)
        for (term in expandedTerms) {
            for ((indexText, category, serviceType) in serviceIndex) {
                if (indexText.contains(term)) {
                    val key = "$category|$serviceType"
                    val existing = results[key]
                    val relevance = 30
                    if (existing == null || existing.relevance < relevance) {
                        results[key] = SearchResult(category, serviceType, relevance)
                    }
                }
            }
        }

        // 3. Se não achou nenhum serviço específico, tentar match por categoria
        if (results.isEmpty()) {
            for ((categoryName, synonyms) in synonymMap) {
                if (synonyms.any { normalized.contains(it) } ||
                    normalized.contains(canonicalize(categoryName))
                ) {
                    val services = ServicePricing.getServicesForCategory(
                        categoryName.replaceFirstChar { it.uppercase() }
                    )
                    // Pegar o primeiro serviço da categoria como sugestão
                    if (services.isNotEmpty()) {
                        results["$categoryName|${services.first()}"] = SearchResult(
                            category = when {
                                categoryName.contains("estofado", ignoreCase = true) -> "Limpeza de estofados"
                                categoryName.contains("encanamento", ignoreCase = true) -> "Encanador"
                                categoryName.contains("caixa", ignoreCase = true) -> "Caixa d'água"
                                categoryName.contains("ar condicionado", ignoreCase = true) -> "Ar condicionado"
                                categoryName.contains("eletrica", ignoreCase = true) -> "Elétrica"
                                categoryName.contains("eletrodomestico", ignoreCase = true) -> "Eletrodomésticos"
                                categoryName.contains("chaveiro", ignoreCase = true) -> "Chaveiro residencial"
                                categoryName.contains("automotivo", ignoreCase = true) -> "Serviços automotivos"
                                categoryName.contains("montagem", ignoreCase = true) -> "Montagem de móveis"
                                categoryName.contains("faxina", ignoreCase = true) -> "Faxina"
                                categoryName.contains("limpeza", ignoreCase = true) -> "Faxina"
                                categoryName.contains("desentupimento", ignoreCase = true) -> "Desentupimento manual"
                                else -> categoryName.replaceFirstChar { it.uppercase() }
                            },
                            serviceType = services.first(),
                            relevance = 10
                        )
                    }
                }
            }
        }

        return results.values
            .distinctBy { it.serviceType }
            .sortedByDescending { it.relevance }
    }

    /**
     * Busca apenas categorias (para filtrar os cards).
     * Retorna o nome da categoria se houver match.
     */
    fun searchCategory(query: String): String? {
        if (query.isBlank()) return null

        val normalized = canonicalize(query)

        // Verificar match direto com nomes de categorias
        for ((categoryName, synonyms) in synonymMap) {
            val canonicalName = canonicalize(categoryName)
            if (canonicalName.contains(normalized) || normalized.contains(canonicalName) ||
                synonyms.any { canonicalize(it).contains(normalized) }
            ) {
                return when {
                    categoryName.contains("estofado", ignoreCase = true) -> "Limpeza de estofados"
                    categoryName.contains("encanamento", ignoreCase = true) -> "Encanador"
                    categoryName.contains("caixa", ignoreCase = true) -> "Caixa d'água"
                    categoryName.contains("ar condicionado", ignoreCase = true) -> "Ar condicionado"
                    categoryName.contains("eletrica", ignoreCase = true) -> "Elétrica"
                    categoryName.contains("eletrodomestico", ignoreCase = true) -> "Eletrodomésticos"
                    categoryName.contains("chaveiro", ignoreCase = true) -> "Chaveiro residencial"
                    categoryName.contains("automotivo", ignoreCase = true) -> "Serviços automotivos"
                    categoryName.contains("montagem", ignoreCase = true) -> "Montagem de móveis"
                    categoryName.contains("faxina", ignoreCase = true) -> "Faxina"
                    categoryName.contains("limpeza", ignoreCase = true) -> "Faxina"
                    categoryName.contains("desentupimento", ignoreCase = true) -> "Desentupimento manual"
                    categoryName.contains("eletrica", ignoreCase = true) -> "Elétrica"
                    else -> categoryName.replaceFirstChar { it.uppercase() }
                }
            }
        }

        // Match com nome do card na ServicesActivity
        val cardNames = listOf(
            "Limpeza de estofados", "Encanador", "Elétrica", "Instalação",
            "Caixa d'água", "Desentupimento manual", "Desentupimento com maquinário até 2 m",
            "Caça-vazamentos", "Ar condicionado", "Eletrodomésticos",
            "Chaveiro residencial", "Serviços automotivos", "Montagem de móveis", "Faxina"
        )
        for (name in cardNames) {
            if (canonicalize(name).contains(normalized) || normalized.contains(canonicalize(name))) {
                return name
            }
        }

        return null
    }

    /**
     * Obtém a categoria correta a partir de um termo de busca.
     */
    fun getCategoryForSearch(query: String): String? {
        val results = search(query)
        return results.firstOrNull()?.category
            ?: searchCategory(query)
    }

    /**
     * Normaliza texto: remove acentos, lowercase, remove pontuação
     */
    private fun canonicalize(text: String): String {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(java.util.Locale.ROOT)
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }

    /**
     * Expande a query com sinônimos
     */
    private fun expandQuery(normalized: String): Set<String> {
        val terms = mutableSetOf(normalized)
        val words = normalized.split(" ")

        for (word in words) {
            for ((_, synonyms) in synonymMap) {
                for (synonym in synonyms) {
                    if (canonicalize(synonym).contains(word)) {
                        terms.add(canonicalize(synonym))
                    }
                }
            }
        }

        return terms
    }
}
