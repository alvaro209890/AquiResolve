package com.aquiresolve.app

import android.util.Log
import com.aquiresolve.app.models.HomeBanner
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Fonte única dos banners do carrossel da Home do PRESTADOR (coleção `provider_banners`).
 *
 * Espelha o padrão de [BannerRepository]: lê a coleção, filtra os ativos, ordena por
 * `displayOrder`, cacheia em memória e nunca lança.
 */
object ProviderBannerRepository {

    private const val TAG = "ProvBannerRepo"
    private const val COLLECTION = "provider_banners"

    @Volatile
    private var cache: List<HomeBanner>? = null

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun cachedBanners(): List<HomeBanner> = cache ?: emptyList()
    fun hasCache(): Boolean = cache != null

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

    private fun readString(vararg values: Any?): String {
        for (v in values) {
            val s = v?.toString()?.trim()
            if (!s.isNullOrEmpty()) return s
        }
        return ""
    }

    suspend fun load(): List<HomeBanner> {
        return try {
            val snapshot = db.collection(COLLECTION).get().await()
            val banners = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                if (!readBoolean(data["active"] ?: data["isActive"] ?: data["enabled"])) {
                    return@mapNotNull null
                }
                val imageUrl = readString(data["imageUrl"], data["image"], data["url"])
                if (imageUrl.isEmpty()) return@mapNotNull null
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
            Log.d(TAG, "Banners de prestador carregados: ${banners.size} ativos")
            banners
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao carregar banners de prestador: ${e.message}")
            cache ?: emptyList()
        }
    }
}
