package com.example.loginapp

import android.content.Context
import android.util.Log
import com.example.loginapp.utils.awaitCurrentUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Manager para gerenciar configurações de privacidade no Firebase
 */
class FirebasePrivacyManager(private val context: Context) {
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "FirebasePrivacyManager"
        private const val PRIVACY_COLLECTION = "privacy_settings"
    }
    
    /**
     * Dados de configuração de privacidade.
     * @PropertyName garante mapeamento correto com chaves snake_case no Firestore.
     */
    data class PrivacySettings(
        @get:PropertyName("userId") val userId: String = "",
        @get:PropertyName("notifications_enabled") val notificationsEnabled: Boolean = true,
        @get:PropertyName("data_sharing_enabled") val dataSharingEnabled: Boolean = true,
        @get:PropertyName("location_enabled") val locationEnabled: Boolean = false,
        @get:PropertyName("public_profile_enabled") val publicProfileEnabled: Boolean = false,
        @get:PropertyName("lastUpdated") val lastUpdated: Timestamp = Timestamp.now()
    )
    
    /**
     * Carrega as configurações de privacidade do usuário
     */
    suspend fun loadPrivacySettings(): Result<PrivacySettings> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            val docRef = db.collection(PRIVACY_COLLECTION).document(user.uid)
            val snapshot = docRef.get().await()
            
            if (snapshot.exists()) {
                val settings = snapshot.toObject(PrivacySettings::class.java)
                if (settings != null) {
                    Log.d(TAG, "Configurações de privacidade carregadas")
                    Result.success(settings)
                } else {
                    Result.failure(Exception("Erro ao converter dados"))
                }
            } else {
                // Criar configurações padrão
                val defaultSettings = PrivacySettings(userId = user.uid)
                savePrivacySettings(defaultSettings)
                Log.d(TAG, "Configurações padrão criadas")
                Result.success(defaultSettings)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar configurações: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Salva as configurações de privacidade
     */
    suspend fun savePrivacySettings(settings: PrivacySettings): Result<Unit> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            val updatedSettings = settings.copy(
                userId = user.uid,
                lastUpdated = Timestamp.now()
            )
            
            db.collection(PRIVACY_COLLECTION)
                .document(user.uid)
                .set(updatedSettings)
                .await()
            
            Log.d(TAG, "Configurações de privacidade salvas")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar configurações: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Atualiza uma configuração específica (usa merge para criar doc se não existir)
     */
    suspend fun updatePrivacySetting(
        settingName: String,
        value: Boolean
    ): Result<Unit> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            val updates = mapOf(
                settingName to value,
                "userId" to user.uid,
                "lastUpdated" to Timestamp.now()
            )
            
            db.collection(PRIVACY_COLLECTION)
                .document(user.uid)
                .set(updates, SetOptions.merge())
                .await()
            
            Log.d(TAG, "Configuração $settingName atualizada para $value")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar configuração: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Atualiza uma configuração do tipo String (ex: horários)
     */
    suspend fun updatePrivacySettingString(
        settingName: String,
        value: String
    ): Result<Unit> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            val updates = mapOf(
                settingName to value,
                "userId" to user.uid,
                "lastUpdated" to Timestamp.now()
            )
            
            db.collection(PRIVACY_COLLECTION)
                .document(user.uid)
                .set(updates, SetOptions.merge())
                .await()
            
            Log.d(TAG, "Configuração $settingName atualizada para $value")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar configuração: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Exporta dados do usuário
     */
    suspend fun exportUserData(): Result<String> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            // Buscar dados do usuário
            val userData = db.collection("users").document(user.uid).get().await()
            val privacySettings = db.collection(PRIVACY_COLLECTION).document(user.uid).get().await()
            val orders = db.collection("orders").whereEqualTo("clientId", user.uid).get().await()
            
            // Criar JSON com dados exportados
            val exportData = mapOf(
                "exportDate" to Timestamp.now(),
                "userData" to userData.data,
                "privacySettings" to privacySettings.data,
                "orders" to orders.documents.map { it.data },
                "exportedBy" to "user_request"
            )
            
            // Salvar exportação no Firebase
            val exportId = db.collection("data_exports").document().id
            db.collection("data_exports")
                .document(exportId)
                .set(exportData)
                .await()
            
            Log.d(TAG, "Dados exportados com ID: $exportId")
            Result.success(exportId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao exportar dados: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Exclui conta do usuário
     */
    suspend fun deleteUserAccount(): Result<Unit> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }

            val userId = user.uid

            // 1. Excluir dados de privacidade
            try {
                db.collection(PRIVACY_COLLECTION).document(userId).delete().await()
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao excluir privacy_settings: ${e.message}")
            }

            // 2. Excluir dados do usuário
            try {
                db.collection("users").document(userId).delete().await()
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao excluir users: ${e.message}")
            }

            // 3. Excluir dados do prestador (se existir)
            try {
                db.collection("providers").document(userId).delete().await()
            } catch (e: Exception) {
                Log.w(TAG, "Sem dados de prestador para excluir: ${e.message}")
            }

            // 4. Excluir pedidos do usuário (como cliente)
            try {
                val ordersSnapshot = db.collection("orders")
                    .whereEqualTo("clientId", userId)
                    .get()
                    .await()

                if (ordersSnapshot.documents.isNotEmpty()) {
                    val batch = db.batch()
                    ordersSnapshot.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao excluir pedidos (cliente): ${e.message}")
            }

            // 5. Excluir pedidos atribuídos ao prestador
            try {
                val providerOrders = db.collection("orders")
                    .whereEqualTo("assignedProvider", userId)
                    .get()
                    .await()

                if (providerOrders.documents.isNotEmpty()) {
                    val batch = db.batch()
                    providerOrders.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao excluir pedidos (prestador): ${e.message}")
            }

            // 6. Excluir conversas de chat
            try {
                val chatsSnapshot = db.collection("chats")
                    .whereArrayContains("participants", userId)
                    .get()
                    .await()

                if (chatsSnapshot.documents.isNotEmpty()) {
                    val batch = db.batch()
                    chatsSnapshot.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao excluir chats: ${e.message}")
            }

            // 7. Excluir exportações de dados
            try {
                val exportsSnapshot = db.collection("data_exports")
                    .whereEqualTo("userData.uid", userId)
                    .get()
                    .await()

                if (exportsSnapshot.documents.isNotEmpty()) {
                    val exportBatch = db.batch()
                    exportsSnapshot.documents.forEach { doc ->
                        exportBatch.delete(doc.reference)
                    }
                    exportBatch.commit().await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao excluir data_exports: ${e.message}")
            }

            // 8. Excluir imagens do Storage
            try {
                val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                val profileRef = storage.reference.child("profile_images/$userId")
                profileRef.delete().await()
            } catch (e: Exception) {
                Log.w(TAG, "Sem imagem de perfil para excluir: ${e.message}")
            }

            try {
                val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                val docsRef = storage.reference.child("documents/$userId")
                val docsList = docsRef.listAll().await()
                docsList.items.forEach { it.delete().await() }
            } catch (e: Exception) {
                Log.w(TAG, "Sem documentos para excluir: ${e.message}")
            }

            // 9. Excluir conta do Firebase Auth
            user.delete().await()

            Log.d(TAG, "Conta do usuário excluída com sucesso")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao excluir conta: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Verifica se uma configuração está habilitada.
     * Carrega do documento Firestore para suportar todas as chaves de notificação.
     */
    suspend fun isSettingEnabled(settingName: String): Boolean {
        return try {
            val user = auth.awaitCurrentUser() ?: return getDefaultForSetting(settingName)
            
            val doc = db.collection(PRIVACY_COLLECTION).document(user.uid).get().await()
            if (doc.exists()) {
                when (val value = doc.get(settingName)) {
                    is Boolean -> value
                    else -> getDefaultForSetting(settingName)
                }
            } else {
                getDefaultForSetting(settingName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar configuração: ${e.message}")
            getDefaultForSetting(settingName)
        }
    }
    
    /**
     * Obtém valor String de uma configuração (ex: quiet_hours_start)
     */
    suspend fun getSettingString(settingName: String, default: String = ""): String {
        return try {
            val user = auth.awaitCurrentUser() ?: return default
            val doc = db.collection(PRIVACY_COLLECTION).document(user.uid).get().await()
            doc.getString(settingName) ?: default
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter configuração: ${e.message}")
            default
        }
    }
    
    private fun getDefaultForSetting(settingName: String): Boolean {
        return when (settingName) {
            "notifications_enabled", "notification_sound_enabled", "notification_vibration_enabled",
            "order_notifications_enabled", "chat_notifications_enabled", "payment_notifications_enabled",
            "data_sharing_enabled" -> true
            "quiet_hours_enabled", "location_enabled", "public_profile_enabled" -> false
            else -> false
        }
    }
}

