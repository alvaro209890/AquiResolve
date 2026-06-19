package com.aquiresolve.app

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Configuração de precificação do guincho, mantida pelo painel admin em
 * `app_config/guincho` (escrita só Admin SDK; o app só lê). Fallback embutido
 * para nunca quebrar a tela se o doc não existir.
 *
 * Observação: o preço FINAL cobrado é sempre recalculado no backend
 * (`/pricing/calculate` com distanceKm) — esta config serve para mostrar a
 * prévia na tela do cliente antes do pagamento.
 */
data class TowingConfig(
    val enabled: Boolean = true,
    val baseFee: Double = 180.0,
    val pricePerKm: Double = 3.90,
    val providerPercent: Double = 70.0,
    val minKm: Double = 0.0
) {
    fun estimatePrice(distanceKm: Double): Double {
        val billable = maxOf(if (distanceKm > 0) distanceKm else 0.0, minKm)
        return baseFee + billable * pricePerKm
    }
}

object TowingConfigRepository {

    @Volatile
    private var cached: TowingConfig? = null

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun readDouble(value: Any?, fallback: Double): Double = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: fallback
        else -> fallback
    }

    /** Busca a config (cache em memória). Em falha devolve o default. */
    suspend fun load(): TowingConfig {
        cached?.let { return it }
        val def = TowingConfig()
        val config = try {
            val snap = db.collection("app_config").document("guincho").get().await()
            if (snap.exists()) {
                TowingConfig(
                    enabled = snap.getBoolean("enabled") ?: true,
                    baseFee = readDouble(snap.get("baseFee"), def.baseFee),
                    pricePerKm = readDouble(snap.get("pricePerKm"), def.pricePerKm),
                    providerPercent = readDouble(snap.get("providerPercent"), def.providerPercent)
                        .coerceIn(0.0, 100.0),
                    minKm = readDouble(snap.get("minKm"), def.minKm)
                )
            } else def
        } catch (_: Exception) {
            def
        }
        cached = config
        return config
    }
}
