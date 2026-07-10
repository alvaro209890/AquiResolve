package com.aquiresolve.app

import android.util.Log
import com.aquiresolve.app.models.HomeCombo
import com.aquiresolve.app.models.HomeComboItem
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Fonte única dos combos promocionais da Home (coleção Firestore `home_combos`).
 *
 * Espelha o padrão de [BannerRepository]: lê a coleção, filtra os ativos, ordena por
 * `displayOrder`, cacheia em memória e **nunca lança** — se o Firestore estiver vazio/offline,
 * devolve lista vazia (a seção de combos simplesmente some, sem quebrar a Home).
 *
 * Campos lidos de forma defensiva (o painel pode gravar variações redundantes de ativo/ordem).
 */
object ComboRepository {

    private const val TAG = "ComboRepository"
    private const val COLLECTION = "home_combos"

    @Volatile
    private var cache: List<HomeCombo>? = null

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /** Combos em cache (lista vazia se ainda não carregou ou se não há combos ativos). */
    fun cachedCombos(): List<HomeCombo> = cache ?: emptyList()

    fun hasCache(): Boolean = cache != null

    /** Combo em cache por id (para a tela de detalhe, sem passar objeto grande por Intent). */
    fun cachedComboById(id: String): HomeCombo? = cache?.firstOrNull { it.id == id }

    private fun readBoolean(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        is Number -> value.toInt() != 0
        null -> true // ausência => considerado ativo
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

    private fun readDouble(vararg values: Any?): Double {
        for (v in values) {
            when (v) {
                is Number -> return v.toDouble()
                is String -> v.toDoubleOrNull()?.let { return it }
                else -> {}
            }
        }
        return 0.0
    }

    private fun readString(vararg values: Any?): String {
        for (v in values) {
            val s = v?.toString()?.trim()
            if (!s.isNullOrEmpty()) return s
        }
        return ""
    }

    @Suppress("UNCHECKED_CAST")
    private fun readItems(value: Any?): List<HomeComboItem> {
        val list = value as? List<*> ?: return emptyList()
        return list.mapNotNull { entry ->
            val map = entry as? Map<String, Any?> ?: return@mapNotNull null
            val niche = readString(map["niche"])
            val serviceName = readString(map["serviceName"], map["name"])
            if (niche.isEmpty() || serviceName.isEmpty()) return@mapNotNull null
            HomeComboItem(
                niche = niche,
                serviceName = serviceName,
                serviceId = readString(map["serviceId"], map["id"]),
                // Combos antigos não têm quantity → 1. Teto defensivo espelha o da API (20).
                quantity = readInt(map["quantity"]).coerceIn(1, 20)
            )
        }
    }

    /**
     * Carrega os combos do Firestore. Retorna a lista pronta para a vitrine (filtrada e ordenada).
     * Em caso de falha, mantém o último cache (ou lista vazia) — nunca propaga exceção.
     */
    suspend fun load(): List<HomeCombo> {
        return try {
            val snapshot = db.collection(COLLECTION).get().await()
            val combos = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                if (!readBoolean(data["active"] ?: data["isActive"] ?: data["enabled"])) {
                    return@mapNotNull null
                }
                val name = readString(data["name"], data["title"])
                val items = readItems(data["items"])
                if (name.isEmpty() || items.isEmpty()) return@mapNotNull null // combo inútil sem nome/itens
                HomeCombo(
                    id = doc.id,
                    name = name,
                    description = readString(data["description"]),
                    imageUrl = readString(data["imageUrl"], data["image"], data["url"]),
                    items = items,
                    fullPrice = readDouble(data["fullPrice"]),
                    promoPrice = readDouble(data["promoPrice"]),
                    savings = readDouble(data["savings"]),
                    discountPercent = readInt(data["discountPercent"]),
                    active = true,
                    displayOrder = readInt(data["displayOrder"], data["order"], data["sortOrder"])
                )
            }.sortedBy { it.displayOrder }

            cache = combos
            Log.d(TAG, "Combos carregados do Firestore: ${combos.size} ativos")
            combos
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao carregar combos: ${e.message}")
            cache ?: emptyList()
        }
    }
}
