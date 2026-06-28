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
            val token = com.google.firebase.installations.FirebaseInstallations.getInstance()
                .getToken(false).await().token

            if (token.isBlank()) {
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
