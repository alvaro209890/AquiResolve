package com.aquiresolve.app

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente do Assistente IA (plano 06). Fala SÓ com o nosso backend (`/api/ai/classify`), que
 * guarda a chave da Groq — a chave NUNCA fica no APK (mesmo padrão do [RouteClient]/mini-mapa).
 *
 * Envia a descrição do problema do cliente + a lista de nichos do catálogo; o backend devolve
 * o nicho sugerido (sempre dentro da lista), a confiança e uma mensagem amigável. Qualquer falha
 * vira `Result.Error` — a tela cai no fallback (busca/lista), nunca trava a contratação.
 */
object AssistantClient {

    data class Suggestion(
        val niche: String?,
        val serviceType: String?,
        val confidence: Double,
        val message: String
    )

    sealed class Result {
        data class Ok(val suggestion: Suggestion) : Result()
        data class Error(val message: String) : Result()
    }

    private val classifyUrl: String =
        BuildConfig.PAYMENTS_API_BASE_URL.removeSuffix("/").removeSuffix("/payments") + "/ai/classify"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * Classifica [description] em um dos [niches]. Suspende em IO; nunca lança.
     */
    suspend fun classify(description: String, niches: List<String>): Result = withContext(Dispatchers.IO) {
        val token = try {
            val user = FirebaseAuth.getInstance().currentUser
                ?: return@withContext Result.Error("Faça login para usar o assistente.")
            user.getIdToken(false).await().token
        } catch (_: Exception) {
            null
        } ?: return@withContext Result.Error("Não foi possível autenticar. Tente novamente.")

        val payload = JSONObject().apply {
            put("description", description)
            put("niches", JSONArray(niches))
        }

        val request = Request.Builder()
            .url(classifyUrl)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "AquiResolve/1.0")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    return@withContext Result.Error("Assistente indisponível agora. Tente a busca.")
                }
                val json = JSONObject(body)
                if (!json.optBoolean("ok", false)) {
                    return@withContext Result.Error("Assistente indisponível agora. Tente a busca.")
                }
                val niche = json.optString("niche").takeIf { it.isNotBlank() && it != "null" }
                val serviceType = json.optString("serviceType").takeIf { it.isNotBlank() && it != "null" }
                val confidence = json.optDouble("confidence", 0.0)
                val message = json.optString("message").ifBlank {
                    if (niche != null) "Acho que é um caso de $niche." else "Não consegui identificar o serviço."
                }
                Result.Ok(Suggestion(niche = niche, serviceType = serviceType, confidence = confidence, message = message))
            }
        } catch (_: Exception) {
            Result.Error("Sem conexão com o assistente. Tente a busca.")
        }
    }
}
