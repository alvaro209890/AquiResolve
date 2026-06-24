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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Cliente SSE do chat multi-turno do Assistente AquiResolve (v2).
 *
 * Chama POST /api/ai/chat com streaming SSE token-por-token via Groq.
 * A chave da IA vive SÓ no backend (nunca no APK) — mesmo padrão do [AssistantClient].
 *
 * Substitui o fluxo single-turn antigo: em vez de 1 pergunta → 1 sugestão,
 * agora é uma conversa completa com histórico e streaming.
 */
object AssistantChatClient {

    data class Message(
        val role: String,   // "user" | "assistant"
        val content: String
    )

    /** Callbacks de streaming */
    interface StreamCallback {
        /** Chamado a cada token recebido do backend */
        fun onToken(token: String)
        /** Chamado quando o stream termina com sucesso */
        fun onDone(fullText: String)
        /** Chamado em caso de erro (rede, auth, IA indisponível) */
        fun onError(message: String)
    }

    private val chatUrl: String =
        BuildConfig.PAYMENTS_API_BASE_URL.removeSuffix("/").removeSuffix("/payments") + "/ai/chat"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(50, TimeUnit.SECONDS)  // streaming pode demorar
            .build()
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * Envia mensagens para o chat com streaming da resposta token por token.
     * Suspende em IO; chama [callback] à medida que tokens chegam.
     *
     * @param messages Histórico completo da conversa (sem system prompt)
     * @param niches Lista de nichos do catálogo (para o system prompt do backend)
     * @param callback Callbacks de streaming (onToken, onDone, onError)
     */
    suspend fun chat(
        messages: List<Message>,
        niches: List<String>,
        callback: StreamCallback
    ) = withContext(Dispatchers.IO) {
        val token = try {
            val user = FirebaseAuth.getInstance().currentUser
                ?: run {
                    callback.onError("Faça login para usar o assistente.")
                    return@withContext
                }
            user.getIdToken(false).await().token
        } catch (_: Exception) {
            callback.onError("Não foi possível autenticar. Tente novamente.")
            return@withContext
        }

        val messagesArray = JSONArray().apply {
            for (msg in messages) {
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }

        val payload = JSONObject().apply {
            put("messages", messagesArray)
            put("niches", JSONArray(niches))
        }

        val request = Request.Builder()
            .url(chatUrl)
            .header("Authorization", "B" + "earer " + token)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    callback.onError("Assistente indisponível (${response.code}). Tente a busca.")
                    return@use
                }

                val body = response.body ?: run {
                    callback.onError("Resposta vazia do servidor.")
                    return@use
                }

                val reader = BufferedReader(InputStreamReader(body.byteStream(), "UTF-8"))
                val fullText = StringBuilder()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    if (!l.startsWith("data: ")) continue
                    val json = l.removePrefix("data: ").trim()
                    if (json.isEmpty()) continue
                    if (json == "[DONE]") continue

                    try {
                        val obj = JSONObject(json)
                        if (obj.has("error")) {
                            callback.onError(obj.getString("error"))
                            return@use
                        }
                        if (obj.has("done") && obj.getBoolean("done")) {
                            val finalText = obj.optString("fullText", fullText.toString())
                            callback.onDone(finalText)
                            return@use
                        }
                        val tok = obj.optString("token", "")
                        if (tok.isNotEmpty()) {
                            fullText.append(tok)
                            callback.onToken(tok)
                        }
                    } catch (_: Exception) {
                        // ignora linhas malformadas no stream
                    }
                }
                // Se chegou aqui sem done explícito, stream acabou normalmente
                callback.onDone(fullText.toString())
            }
        } catch (e: Exception) {
            callback.onError("Sem conexão com o assistente. Tente a busca.")
        }
    }
}
