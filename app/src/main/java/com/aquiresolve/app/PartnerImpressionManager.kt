package com.aquiresolve.app

import android.util.Log
import com.aquiresolve.app.models.Partner
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Controla o **limite diário de impressões** dos banners de parceiros na Home.
 *
 * Cada parceiro só pode aparecer para [Partner.dailyImpressionCap] clientes **distintos por dia**.
 * O estado vive em `partner_impressions/{partnerId}_{yyyy-MM-dd}` com
 * `{ partnerId, date, count, clientIds[] }` — `clientIds` são os uids que já viram o parceiro
 * naquele dia (uso para deduplicar e para manter visível a quem já viu).
 *
 * Escrita via client SDK protegida por regra estreita (o cliente só pode somar o **próprio** uid e
 * `count == clientIds.size()`). Tudo é **fail-open**: qualquer erro de rede/permissão nunca quebra a
 * Home — no filtro devolve a lista inteira, no registro apenas loga.
 */
object PartnerImpressionManager {

    private const val TAG = "PartnerImpressions"
    private const val COLLECTION = "partner_impressions"

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val dayFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Chave do dia (fuso local do device; o app é BR) usada no id do doc de impressões. */
    fun todayKey(): String = dayFormat.format(Date())

    /**
     * A campanha do parceiro está ativa hoje? Janela = [`startDate`, `startDate` + `campaignDays`).
     * `startDate` vazio → sem limite de início; `campaignDays` <= 0 → sem expiração. Fail-open.
     */
    fun isCampaignActive(partner: Partner): Boolean {
        val start = partner.startDate.trim()
        if (start.isEmpty()) return true // sem início definido → sempre ativa (legado/ilimitado)
        return try {
            val fmt = dayFormat
            val startMs = fmt.parse(start)?.time ?: return true
            val todayMs = fmt.parse(todayKey())?.time ?: return true
            if (todayMs < startMs) return false                  // ainda não começou
            if (partner.campaignDays <= 0) return true            // sem expiração
            val daysElapsed = ((todayMs - startMs) / 86_400_000L).toInt()
            daysElapsed < partner.campaignDays
        } catch (e: Exception) {
            true
        }
    }

    private fun docId(partnerId: String): String = "${partnerId}_${todayKey()}"

    @Suppress("UNCHECKED_CAST")
    private fun readClientIds(value: Any?): List<String> =
        (value as? List<*>)?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) } ?: emptyList()

    /**
     * Filtra [all] mantendo só os parceiros que ainda podem aparecer hoje para [uid]:
     * `count < cap` **ou** o uid já está em `clientIds` (quem já viu continua vendo no mesmo dia).
     * Lê os docs em paralelo. Em qualquer falha, considera o parceiro disponível (fail-open).
     */
    suspend fun availablePartners(all: List<Partner>, uid: String): List<Partner> {
        // 1) Janela da campanha (puro/rápido): fora do período de dias → não exibe.
        val inCampaign = all.filter { isCampaignActive(it) }
        if (inCampaign.isEmpty()) return inCampaign
        // 2) Limite diário de clientes (lê impressões em paralelo).
        return coroutineScope {
            inCampaign.map { partner ->
                async {
                    partner to isAvailable(partner, uid)
                }
            }.awaitAll()
        }.filter { it.second }.map { it.first }
    }

    private suspend fun isAvailable(partner: Partner, uid: String): Boolean {
        return try {
            val snap = db.collection(COLLECTION).document(docId(partner.id)).get().await()
            if (!snap.exists()) return true
            val ids = readClientIds(snap.get("clientIds"))
            val count = (snap.getLong("count") ?: ids.size.toLong()).toInt()
            count < partner.dailyImpressionCap || (uid.isNotBlank() && ids.contains(uid))
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao ler impressões de ${partner.id}, considerando disponível: ${e.message}")
            true
        }
    }

    /**
     * Registra uma impressão (banner exibido ao cliente). Idempotente: se o uid já contou hoje ou o
     * limite já foi atingido, não faz nada. Transação garante a contagem distinta sob concorrência.
     */
    suspend fun registerImpression(partner: Partner, uid: String) {
        if (uid.isBlank() || partner.id.isBlank()) return
        val ref = db.collection(COLLECTION).document(docId(partner.id))
        val today = todayKey()
        try {
            db.runTransaction<Void?> { txn ->
                val snap = txn.get(ref)
                val existing = readClientIds(snap.get("clientIds"))
                if (existing.contains(uid)) return@runTransaction null      // já contou hoje
                if (existing.size >= partner.dailyImpressionCap) return@runTransaction null // limite atingido
                val updated = existing + uid
                txn.set(
                    ref,
                    mapOf(
                        "partnerId" to partner.id,
                        "date" to today,
                        "count" to updated.size,
                        "clientIds" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                null
            }.await()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao registrar impressão de ${partner.id}: ${e.message}")
        }
    }
}
