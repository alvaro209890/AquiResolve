package com.aquiresolve.app

import android.util.Log
import com.aquiresolve.app.models.CatalogService
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Fonte dos SERVIÇOS de cada nicho exibidos no app — coleção `catalog_services`,
 * mantida pelo painel admin (/dashboard/servicos/catalogo-app › aba Serviços).
 *
 * Espelha o padrão do [CatalogRepository] (nichos): leitura defensiva, cache em memória e
 * fallback silencioso. Quando o Firestore não tem serviços para o nicho, o
 * [CreateOrderActivity] mantém a lista hardcoded antiga (zero regressão offline).
 *
 * Campos lidos de forma tolerante (o painel grava variações redundantes):
 *  - nome:   `name` | `title` | `label`
 *  - ativo:  `active` | `isActive` | `enabled`
 *  - ordem:  `displayOrder` | `order` | `sortOrder`
 */
object CatalogServiceRepository {

    private const val TAG = "CatalogServiceRepo"

    // Cache por nome de nicho (normalizado em minúsculas).
    private val cache = ConcurrentHashMap<String, List<CatalogService>>()

    // Lista achatada de TODOS os serviços ativos (alimenta a Busca Inteligente da Home).
    @Volatile
    private var allCache: List<CatalogService>? = null

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private fun key(niche: String): String = niche.trim().lowercase()

    /** Serviços já carregados para o nicho (vazio se ainda não buscado). */
    fun cachedForNiche(niche: String): List<CatalogService> = cache[key(niche)] ?: emptyList()

    fun hasCacheForNiche(niche: String): Boolean = cache[key(niche)]?.isNotEmpty() == true

    /** Busca, no cache, o serviço pelo nome (case-insensitive). */
    fun findService(niche: String, serviceType: String): CatalogService? {
        val target = serviceType.trim().lowercase()
        return cache[key(niche)]?.firstOrNull { it.name.trim().lowercase() == target }
    }

    /**
     * Todos os serviços já em cache (de [loadAll] ou da soma dos nichos carregados).
     * Usado pela Busca Inteligente da Home; vazio se nada foi carregado ainda.
     */
    fun allCachedServices(): List<CatalogService> =
        allCache ?: cache.values.flatten().takeIf { it.isNotEmpty() } ?: emptyList()

    private fun readBoolean(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        is Number -> value.toInt() != 0
        null -> true
        else -> true
    }

    private fun readInt(vararg values: Any?): Int {
        for (v in values) {
            when (v) {
                is Number -> return v.toInt()
                is String -> v.toIntOrNull()?.let { return it }
                else -> {}
            }
        }
        return 0
    }

    private fun readDouble(value: Any?): Double = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    private fun readString(vararg values: Any?): String {
        for (v in values) {
            val s = v?.toString()?.trim()
            if (!s.isNullOrEmpty()) return s
        }
        return ""
    }

    /**
     * Carrega os serviços ativos do nicho do Firestore e cacheia.
     * Retorna a lista carregada; em falha/coleção vazia retorna cache-ou-vazio (mantém fallback).
     */
    suspend fun loadForNiche(niche: String): List<CatalogService> {
        return try {
            val snapshot = db.collection("catalog_services")
                .whereEqualTo("niche", niche)
                .get()
                .await()

            val services = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                if (!readBoolean(data["active"] ?: data["isActive"] ?: data["enabled"])) {
                    return@mapNotNull null
                }
                val name = readString(data["name"], data["title"], data["label"])
                if (name.isEmpty()) return@mapNotNull null
                CatalogService(
                    niche = readString(data["niche"]).ifEmpty { niche },
                    name = name,
                    description = readString(data["description"]),
                    estimatedTime = readString(data["estimatedTime"]),
                    estimatedPrice = readDouble(data["estimatedPrice"]),
                    providerCommissionPercent = readInt(data["providerCommissionPercent"]),
                    providerCommission = readDouble(data["providerCommission"]),
                    isConsult = data["isConsult"] == true,
                    active = true,
                    displayOrder = readInt(data["displayOrder"], data["order"], data["sortOrder"])
                )
            }.sortedBy { it.displayOrder }

            if (services.isNotEmpty()) {
                cache[key(niche)] = services
                Log.d(TAG, "Serviços carregados para '$niche': ${services.size}")
            } else {
                Log.d(TAG, "Sem serviços ativos em catalog_services para '$niche' — usando fallback")
            }
            services
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao carregar serviços de '$niche': ${e.message}")
            cache[key(niche)] ?: emptyList()
        }
    }

    /**
     * Carrega TODOS os serviços ativos de `catalog_services` de uma vez e popula os caches
     * (por nicho + lista achatada). Usado para pré-aquecer a Busca Inteligente da Home, evitando
     * uma consulta ao Firestore por tecla. Nunca lança — em falha mantém o cache atual.
     */
    suspend fun loadAll(): List<CatalogService> {
        return try {
            val snapshot = db.collection("catalog_services").get().await()
            val services = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                if (!readBoolean(data["active"] ?: data["isActive"] ?: data["enabled"])) {
                    return@mapNotNull null
                }
                val name = readString(data["name"], data["title"], data["label"])
                if (name.isEmpty()) return@mapNotNull null
                val niche = readString(data["niche"])
                if (niche.isEmpty()) return@mapNotNull null
                CatalogService(
                    niche = niche,
                    name = name,
                    description = readString(data["description"]),
                    estimatedTime = readString(data["estimatedTime"]),
                    estimatedPrice = readDouble(data["estimatedPrice"]),
                    providerCommissionPercent = readInt(data["providerCommissionPercent"]),
                    providerCommission = readDouble(data["providerCommission"]),
                    isConsult = data["isConsult"] == true,
                    active = true,
                    displayOrder = readInt(data["displayOrder"], data["order"], data["sortOrder"])
                )
            }.sortedBy { it.displayOrder }

            if (services.isNotEmpty()) {
                allCache = services
                // Reagrupa por nicho para reaproveitar o cache por nicho do CreateOrderActivity.
                services.groupBy { key(it.niche) }.forEach { (k, list) -> cache[k] = list }
                Log.d(TAG, "Catálogo de serviços carregado por inteiro: ${services.size}")
            } else {
                Log.d(TAG, "catalog_services vazio — Busca Inteligente usa fallback estático")
            }
            services
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao carregar todos os serviços: ${e.message}")
            allCachedServices()
        }
    }
}
