package com.aquiresolve.app.utils

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
        "pintura" to listOf("pintura", "pintar", "parede", "retoque", "tinta"),
        "limpeza" to listOf("limpeza", "limpar", "faxina", "higienizacao"),
        "estofado" to listOf("estofado", "sofa", "colchao", "tapete", "cadeira"),
        "desentupimento" to listOf("desentupimento", "desentupir", "entupido", "ralo", "pia", "vaso"),
        "chaveiro" to listOf("chaveiro", "chave", "fechadura", "porta"),
        "jardim" to listOf("jardim", "jardinagem", "grama", "poda", "planta"),
        "automotivo" to listOf("automotivo", "carro", "pneu", "veiculo", "oleo"),
        "montagem" to listOf("montagem", "montar", "movel", "guarda roupa", "estante"),
        "ar condicionado" to listOf("ar condicionado", "ar condicionado", "climatizador", "split"),
        "caixa dagua" to listOf("caixa d agua", "caixa d'água", "reservatorio", "boia"),
        "eletrodomestico" to listOf("eletrodomestico", "geladeira", "fogao", "micro-ondas", "maquina de lavar")
    )

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
                                categoryName.contains("jardim", ignoreCase = true) -> "Jardinagem"
                                categoryName.contains("ar condicionado", ignoreCase = true) -> "Ar condicionado"
                                categoryName.contains("eletrica", ignoreCase = true) -> "Elétrica"
                                categoryName.contains("eletrodomestico", ignoreCase = true) -> "Eletrodomésticos"
                                categoryName.contains("chaveiro", ignoreCase = true) -> "Chaveiro residencial"
                                categoryName.contains("automotivo", ignoreCase = true) -> "Serviços automotivos"
                                categoryName.contains("montagem", ignoreCase = true) -> "Montagem de móveis"
                                categoryName.contains("pintura", ignoreCase = true) -> "Pintura"
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
                    categoryName.contains("jardim", ignoreCase = true) -> "Jardinagem"
                    categoryName.contains("ar condicionado", ignoreCase = true) -> "Ar condicionado"
                    categoryName.contains("eletrica", ignoreCase = true) -> "Elétrica"
                    categoryName.contains("eletrodomestico", ignoreCase = true) -> "Eletrodomésticos"
                    categoryName.contains("chaveiro", ignoreCase = true) -> "Chaveiro residencial"
                    categoryName.contains("automotivo", ignoreCase = true) -> "Serviços automotivos"
                    categoryName.contains("montagem", ignoreCase = true) -> "Montagem de móveis"
                    categoryName.contains("pintura", ignoreCase = true) -> "Pintura"
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
