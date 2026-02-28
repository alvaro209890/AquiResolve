package com.example.loginapp.utils

import com.example.loginapp.models.OrderData
import java.text.Normalizer
import java.util.Locale

/**
 * Catálogo central de nichos e regra de matching entre prestador e pedido.
 */
object ServiceNicheCatalog {

    val providerSelectableNiches = listOf(
        "Elétrica",
        "Encanador",
        "Instalação",
        "Pintura",
        "Jardinagem",
        "Limpeza",
        "Caixa d'água",
        "Desentupimento manual",
        "Desentupimento com maquinário até 2 m",
        "Caça-vazamentos",
        "Limpeza de estofados",
        "Ar condicionado",
        "Eletrodomésticos",
        "Chaveiro residencial",
        "Serviços automotivos",
        "Montagem de móveis",
        "Faxina"
    )

    private val normalizedToCanonical = buildMap {
        providerSelectableNiches.forEach { put(normalizeKey(it), it) }

        // Compatibilidade com nomes legados.
        put("hidraulica", "Encanador")
        put("encanamento", "Encanador")
        put("estofados", "Limpeza de estofados")
    }

    private val keyEletrica = normalizeKey("Elétrica")
    private val keyEncanador = normalizeKey("Encanador")
    private val keyInstalacao = normalizeKey("Instalação")
    private val keyCaixaDagua = normalizeKey("Caixa d'água")
    private val keyDesentupimentoManual = normalizeKey("Desentupimento manual")
    private val keyDesentupimentoMaquinario = normalizeKey("Desentupimento com maquinário até 2 m")
    private val keyCacaVazamentos = normalizeKey("Caça-vazamentos")
    private val keyEstofados = normalizeKey("Limpeza de estofados")
    private val keyArCondicionado = normalizeKey("Ar condicionado")
    private val keyEletrodomesticos = normalizeKey("Eletrodomésticos")
    private val keyChaveiro = normalizeKey("Chaveiro residencial")
    private val keyServicosAutomotivos = normalizeKey("Serviços automotivos")
    private val keyMontagemMoveis = normalizeKey("Montagem de móveis")
    private val keyFaxina = normalizeKey("Faxina")

    fun canonicalizeNiche(raw: String): String {
        if (raw.isBlank()) return ""
        val key = normalizeKey(raw)
        return normalizedToCanonical[key] ?: raw.trim()
    }

    fun canonicalizeProviderServices(services: List<String>): List<String> {
        return services
            .map { canonicalizeNiche(it) }
            .filter { it.isNotBlank() }
            .distinctBy { normalizeKey(it) }
    }

    fun normalizeProviderServices(services: List<String>): Set<String> {
        return canonicalizeProviderServices(services)
            .map { normalizeKey(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun matchesProviderServices(providerServicesNormalized: Set<String>, order: OrderData): Boolean {
        if (providerServicesNormalized.isEmpty()) {
            return false
        }

        val orderNicheKeys = buildList {
            add(candidateToNicheKey(order.serviceName))
            add(candidateToNicheKey(order.serviceType))
        }.filter { it.isNotBlank() }

        if (orderNicheKeys.isEmpty()) return false

        return orderNicheKeys.any(providerServicesNormalized::contains)
    }

    private fun candidateToNicheKey(raw: String): String {
        if (raw.isBlank()) return ""

        val canonical = canonicalizeNiche(raw)
        val directKey = normalizeKey(canonical)
        if (normalizedToCanonical.containsKey(directKey)) {
            return directKey
        }

        val key = normalizeKey(raw)

        if (key.contains("desentupimento") && key.contains("maquinario")) return keyDesentupimentoMaquinario
        if (key.contains("desentupimento")) return keyDesentupimentoManual

        if (containsAny(key, "caca vazamento", "caca vazamentos")) return keyCacaVazamentos

        if (containsAny(key, "torneira", "rabicho", "sifao", "registro", "hidraul", "encan")) return keyEncanador

        if (containsAny(key, "estofad", "sofa", "colchao", "tapete", "carpete", "poltrona", "impermeabiliz")) return keyEstofados

        if (containsAny(key, "ar condicionado", "climatiz")) return keyArCondicionado

        if (containsAny(key, "eletrodomest", "micro ondas", "fogao", "forno", "geladeira")) return keyEletrodomesticos

        if (containsAny(key, "chaveiro", "fechadura", "chave")) return keyChaveiro

        if (containsAny(key, "automotiv", "pneu", "combustivel", "partida eletrica")) return keyServicosAutomotivos

        if (containsAny(key, "montagem", "moveis", "guarda roupa", "escrivaninha", "armario", "comod")) return keyMontagemMoveis

        if (containsAny(key, "faxina")) return keyFaxina

        if (containsAny(key, "caixa d agua", "boia")) return keyCaixaDagua

        if (containsAny(key, "disjuntor", "tomada", "chuveiro", "lampada", "interruptor", "resistencia", "eletric")) return keyEletrica

        if (containsAny(key, "instalacao", "suporte", "ventilador", "cooktop", "purificador", "lava louca", "maquina de lavar")) return keyInstalacao

        return ""
    }

    private fun containsAny(value: String, vararg terms: String): Boolean {
        return terms.any { value.contains(it) }
    }

    private fun normalizeKey(value: String): String {
        val withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")

        return withoutAccents
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }
}
