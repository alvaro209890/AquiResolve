package com.aquiresolve.app

import android.util.Log
import com.aquiresolve.app.models.Partner
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Fonte única dos parceiros patrocinadores da Home (coleção Firestore `partners`).
 *
 * Espelha o padrão de [BannerRepository]/[ComboRepository]: lê a coleção, filtra os ativos,
 * ordena por `displayOrder`, cacheia em memória e **nunca lança** — se o Firestore estiver
 * vazio/offline, devolve lista vazia (a seção de parceiros simplesmente some, sem quebrar a Home).
 *
 * Campos lidos de forma defensiva (o painel grava variações redundantes de ativo/ordem).
 */
object PartnerRepository {

    private const val TAG = "PartnerRepository"
    private const val COLLECTION = "partners"

    @Volatile
    private var cache: List<Partner>? = null

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /** Parceiros em cache (lista vazia se ainda não carregou ou se não há parceiros ativos). */
    fun cachedPartners(): List<Partner> = cache ?: emptyList()

    fun hasCache(): Boolean = cache != null

    /** Parceiro em cache por id (para a tela de detalhe, sem passar objeto grande por Intent). */
    fun cachedPartnerById(id: String): Partner? = cache?.firstOrNull { it.id == id }

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
     * Carrega os parceiros do Firestore. Retorna a lista pronta para a seção (filtrada e ordenada).
     * Em caso de falha, mantém o último cache (ou lista vazia) — nunca propaga exceção.
     */
    suspend fun load(): List<Partner> {
        return try {
            val snapshot = db.collection(COLLECTION).get().await()
            val partners = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                if (!readBoolean(data["active"] ?: data["isActive"] ?: data["enabled"])) {
                    return@mapNotNull null
                }
                val name = readString(data["name"], data["title"])
                if (name.isEmpty()) return@mapNotNull null // parceiro sem nome é inútil
                Partner(
                    id = doc.id,
                    name = name,
                    logoUrl = readString(data["logoUrl"], data["logo"], data["imageUrl"]),
                    bannerUrl = readString(data["bannerUrl"], data["banner"]),
                    description = readString(data["description"]),
                    benefitType = readString(data["benefitType"]).ifEmpty { Partner.TYPE_LINK },
                    benefitLabel = readString(data["benefitLabel"], data["benefit"]),
                    couponCode = readString(data["couponCode"], data["coupon"]),
                    url = readString(data["url"], data["link"]),
                    active = true,
                    displayOrder = readInt(data["displayOrder"], data["order"], data["sortOrder"])
                )
            }.sortedBy { it.displayOrder }

            cache = partners
            Log.d(TAG, "Parceiros carregados do Firestore: ${partners.size} ativos")
            partners
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao carregar parceiros: ${e.message}")
            cache ?: emptyList()
        }
    }
}
