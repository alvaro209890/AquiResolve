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
 * Cliente da análise de imagem da Helô.
 * Chama POST /api/ai/vision (não-streaming) enviando a foto em base64; o backend
 * usa um modelo de visão da Groq e devolve { text, niche, nicheSlug }.
 */
object AssistantVisionClient {

    data class Result(
        val text: String,
        val niche: String?
    )

    interface Callback {
        fun onResult(result: Result)
        fun onError(message: String)
    }

    private val visionUrl: String =
        BuildConfig.PAYMENTS_API_BASE_URL.removeSuffix("/").removeSuffix("/payments") + "/ai/vision"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(50, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * @param imageBase64 imagem JPEG já reduzida, em base64 (sem prefixo data:)
     * @param text mensagem opcional do cliente junto da foto
     * @param niches nichos do catálogo (para ancorar a resposta)
     */
    suspend fun analyze(
        imageBase64: String,
        text: String?,
        niches: List<String>,
        callback: Callback
    ) = withContext(Dispatchers.IO) {
        val token = try {
            val user = FirebaseAuth.getInstance().currentUser
                ?: run {
                    callback.onError("Faça login para usar o Helô.")
                    return@withContext
                }
            user.getIdToken(false).await().token
        } catch (_: Exception) {
            callback.onError("Não foi possível autenticar. Tente novamente.")
            return@withContext
        }

        val payload = JSONObject().apply {
            put("image", imageBase64)
            put("mimeType", "image/jpeg")
            if (!text.isNullOrBlank()) put("text", text)
            put("niches", JSONArray(niches))
        }

        val request = Request.Builder()
            .url(visionUrl)
            .addHeader("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = try {
                        JSONObject(body).optString("error", "Não consegui analisar a imagem agora.")
                    } catch (_: Exception) {
                        "Não consegui analisar a imagem agora."
                    }
                    callback.onError(msg)
                    return@withContext
                }
                val json = JSONObject(body)
                val resultText = json.optString("text").ifBlank {
                    "Não consegui entender bem a foto. Pode descrever o problema?"
                }
                val niche = json.optString("niche").takeIf { it.isNotBlank() }
                callback.onResult(Result(text = resultText, niche = niche))
            }
        } catch (e: Exception) {
            callback.onError("Falha ao enviar a imagem. Verifique sua conexão.")
        }
    }
}
