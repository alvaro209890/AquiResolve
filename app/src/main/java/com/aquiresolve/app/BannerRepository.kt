package com.aquiresolve.app

import android.util.Log
import com.aquiresolve.app.models.HomeBanner
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Fonte única dos banners do carrossel da Home (coleção Firestore `home_banners`).
 *
 * Espelha o padrão de [CatalogRepository]: lê a coleção, filtra os ativos, ordena por
 * `displayOrder`, cacheia em memória e **nunca lança** — se o Firestore estiver vazio/offline,
 * devolve lista vazia (a seção do carrossel simplesmente some, sem quebrar a Home).
 *
 * Campos lidos de forma defensiva (o painel pode gravar variações redundantes de ativo/ordem),
 * igual ao [CatalogRepository].
 */
object BannerRepository {

    private const val TAG = "BannerRepository"
    private const val COLLECTION = "home_banners"

    @Volatile
    private var cache: List<HomeBanner>? = null

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /** Banners em cache (lista vazia se ainda não carregou ou se não há banners ativos). */
    fun cachedBanners(): List<HomeBanner> = cache ?: emptyList()

    fun hasCache(): Boolean = cache != null

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

    private fun readString(vararg values: Any?): String {
        for (v in values) {
            val s = v?.toString()?.trim()
            if (!s.isNullOrEmpty()) return s
        }
        return ""
    }

    /**
     * Carrega os banners do Firestore. Retorna a lista pronta para o carrossel (já filtrada e
     * ordenada). Em caso de falha, mantém o último cache (ou lista vazia) — nunca propaga exceção.
     */
    suspend fun load(): List<HomeBanner> {
        return try {
            val snapshot = db.collection(COLLECTION).get().await()
            val banners = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                if (!readBoolean(data["active"] ?: data["isActive"] ?: data["enabled"])) {
                    return@mapNotNull null
                }
                val imageUrl = readString(data["imageUrl"], data["image"], data["url"])
                if (imageUrl.isEmpty()) return@mapNotNull null // banner sem imagem é inútil no carrossel
                HomeBanner(
                    id = doc.id,
                    title = readString(data["title"]),
                    subtitle = readString(data["subtitle"]),
                    imageUrl = imageUrl,
                    actionType = readString(data["actionType"]).ifEmpty { HomeBanner.ACTION_NONE },
                    actionValue = readString(data["actionValue"]),
                    backgroundColor = readString(data["backgroundColor"]),
                    active = true,
                    displayOrder = readInt(data["displayOrder"], data["order"], data["sortOrder"])
                )
            }.sortedBy { it.displayOrder }

            cache = banners
            Log.d(TAG, "Banners carregados do Firestore: ${banners.size} ativos")
            banners
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao carregar banners: ${e.message}")
            cache ?: emptyList()
        }
    }
}
