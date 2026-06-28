package com.aquiresolve.app

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Gerencia tokens FCM dos usuários no Firestore para envio de push notifications.
 * Salva o token no documento do usuário e em uma coleção dedicada para consulta.
 */
object FirebaseNotificationManager {

    private const val TAG = "FCMNotificationMgr"
    private val db = FirebaseFirestore.getInstance()

    /**
     * Salva o token FCM no Firestore (users/{uid} e fcm_tokens/{uid}).
     */
    suspend fun saveUserToken(userId: String) = withContext(Dispatchers.IO) {
        try {
            // IMPORTANTE: usar o token de REGISTRO do FCM (FirebaseMessaging.getToken),
            // NÃO o token do Firebase Installations (FIS). O FIS retorna um JWT de auth
            // interno que NÃO serve para enviar push — o backend não consegue entregar
            // notificações a ele (era a causa do prestador não receber som/alerta com o
            // app fechado: o token salvo era inválido para FCM).
            val token = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .token.await()

            saveToken(userId, token)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar token FCM para $userId: ${e.message}", e)
        }
    }

    /**
     * Persiste um token FCM já conhecido (ex.: o recebido em onNewToken), sem
     * re-consultar o FirebaseMessaging.
     */
    suspend fun saveToken(userId: String, token: String?) = withContext(Dispatchers.IO) {
        try {
            if (token.isNullOrBlank()) {
                Log.w(TAG, "Token FCM vazio para usuário $userId")
                return@withContext
            }

            // Salva no doc do usuário
            db.collection("users").document(userId)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .await()

            // Salva em coleção dedicada (índice rápido para queries)
            db.collection("fcm_tokens").document(userId)
                .set(mapOf(
                    "token" to token,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                ), SetOptions.merge())
                .await()

            Log.d(TAG, "Token FCM salvo para $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar token FCM para $userId: ${e.message}", e)
        }
    }
}
