package com.example.loginapp

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Gerenciador de prestadores de serviço
 * 
 * Funcionalidades:
 * - Cadastro e atualização de prestadores
 * - Gerenciamento de perfil profissional
 * - Verificação de status
 * - Busca de prestadores por região e serviço
 */
class FirebaseProviderManager {
    
    private val db = FirebaseFirestore.getInstance()
    
    companion object {
        private const val TAG = "FirebaseProviderManager"
        private const val PROVIDERS_COLLECTION = "providers"
    }
    
    /**
     * Dados de endereço
     */
    data class Address(
        val cep: String,
        val street: String,
        val number: String,
        val complement: String? = null,
        val city: String,
        val state: String,
        val coordinates: Map<String, Double>? = null
    )
    
    /**
     * Dados bancários
     */
    data class BankInfo(
        val bankName: String,
        val agency: String,
        val account: String,
        val accountType: String = "CONTA_CORRENTE" // CONTA_CORRENTE, CONTA_POUPANCA
    )
    
    /**
     * Perfil completo do prestador
     */
    data class ProviderProfile(
        val uid: String,
        val email: String,
        val fullName: String,
        val phone: String,
        val cpf: String? = null,
        val address: Address,
        val bank: BankInfo,
        val services: List<String>,
        val pixKey: String? = null,
        val pixKeyType: String? = null, // "celular" ou "cpf"
        val verificationStatus: String = "pending", // pending, verified, rejected
        val rating: Double = 0.0,
        val totalJobs: Int = 0,
        val completedJobs: Int = 0,
        val totalEarnings: Double = 0.0, // Lucro total do prestador
        val isActive: Boolean = true,
        val bio: String = "",
        val profileImageUrl: String? = null,
        val createdAt: Date = Date(),
        val updatedAt: Date = Date()
    )
    
    /**
     * Resultado de operações
     */
    sealed class ProviderResult {
        object Success : ProviderResult()
        data class Error(val message: String) : ProviderResult()
    }

    /**
     * Verifica se já existe um perfil de prestador para o UID informado
     */
    suspend fun hasProviderProfile(uid: String): Boolean {
        return try {
            Log.d(TAG, "🔍 Verificando se usuário $uid tem perfil de prestador...")
            Log.d(TAG, "📂 Consultando coleção: $PROVIDERS_COLLECTION")
            
            val doc = db.collection(PROVIDERS_COLLECTION)
                .document(uid)
                .get()
                .await()
            
            val exists = doc.exists()
            Log.d(TAG, "📊 Perfil de prestador existe? $exists")
            
            if (exists) {
                Log.d(TAG, "✅ Usuário tem perfil de prestador")
            } else {
                Log.d(TAG, "❌ Usuário não tem perfil de prestador")
            }
            
            exists
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao verificar perfil do prestador: ${e.message}", e)
            false
        }
    }
    
    /**
     * Atualiza perfil do prestador
     */
    suspend fun updateProfile(profile: ProviderProfile): ProviderResult {
        return try {
            Log.d(TAG, "🔄 Atualizando perfil do prestador: ${profile.uid}")
            Log.d(TAG, "📂 Coleção: $PROVIDERS_COLLECTION")
            
            val profileData = mapOf(
                "uid" to profile.uid,
                "email" to profile.email,
                "fullName" to profile.fullName,
                "phone" to profile.phone,
                "cpf" to profile.cpf,
                "address" to mapOf(
                    "cep" to profile.address.cep,
                    "street" to profile.address.street,
                    "number" to profile.address.number,
                    "complement" to profile.address.complement,
                    "city" to profile.address.city,
                    "state" to profile.address.state,
                    "coordinates" to profile.address.coordinates
                ),
                "bank" to mapOf(
                    "bankName" to profile.bank.bankName,
                    "agency" to profile.bank.agency,
                    "account" to profile.bank.account,
                    "accountType" to profile.bank.accountType
                ),
                "services" to profile.services,
                "pixKey" to profile.pixKey,
                "pixKeyType" to profile.pixKeyType,
                "verificationStatus" to profile.verificationStatus,
                "rating" to profile.rating,
                "totalJobs" to profile.totalJobs,
                "completedJobs" to profile.completedJobs,
                "totalEarnings" to profile.totalEarnings,
                "isActive" to profile.isActive,
                "bio" to profile.bio,
                "profileImageUrl" to profile.profileImageUrl,
                "createdAt" to profile.createdAt,
                "updatedAt" to Date()
            )
            
            Log.d(TAG, "📝 Salvando dados na coleção providers...")
            db.collection(PROVIDERS_COLLECTION)
                .document(profile.uid)
                .set(profileData)
                .await()
            
            Log.d(TAG, "✅ Perfil do prestador salvo com sucesso: ${profile.uid}")
            ProviderResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao atualizar perfil: ${e.message}", e)
            ProviderResult.Error("Erro ao atualizar perfil: ${e.message}")
        }
    }
    
    /**
     * Obtém perfil do prestador
     */
    suspend fun getProviderProfile(uid: String): ProviderProfile? {
        return try {
            val doc = db.collection(PROVIDERS_COLLECTION)
                .document(uid)
                .get()
                .await()
            
            if (!doc.exists()) {
                return null
            }
            
            val data = doc.data ?: return null
            val addressData = data["address"] as? Map<String, Any> ?: return null
            val bankData = data["bank"] as? Map<String, Any> ?: return null
            
            ProviderProfile(
                uid = doc.id,
                email = data["email"] as? String ?: "",
                fullName = data["fullName"] as? String ?: "",
                phone = data["phone"] as? String ?: "",
                cpf = data["cpf"] as? String,
                address = Address(
                    cep = addressData["cep"] as? String ?: "",
                    street = addressData["street"] as? String ?: "",
                    number = addressData["number"] as? String ?: "",
                    complement = addressData["complement"] as? String,
                    city = addressData["city"] as? String ?: "",
                    state = addressData["state"] as? String ?: "",
                    coordinates = addressData["coordinates"] as? Map<String, Double>
                ),
                bank = BankInfo(
                    bankName = bankData["bankName"] as? String ?: "",
                    agency = bankData["agency"] as? String ?: "",
                    account = bankData["account"] as? String ?: "",
                    accountType = bankData["accountType"] as? String ?: "CONTA_CORRENTE"
                ),
                services = (data["services"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                pixKey = data["pixKey"] as? String,
                pixKeyType = data["pixKeyType"] as? String,
                verificationStatus = data["verificationStatus"] as? String ?: "pending",
                rating = (data["rating"] as? Double) ?: 0.0,
                totalJobs = (data["totalJobs"] as? Long)?.toInt() ?: 0,
                completedJobs = (data["completedJobs"] as? Long)?.toInt() ?: 0,
                totalEarnings = (data["totalEarnings"] as? Double) ?: 0.0,
                isActive = data["isActive"] as? Boolean ?: true,
                bio = data["bio"] as? String ?: "",
                profileImageUrl = data["profileImageUrl"] as? String,
                createdAt = data["createdAt"] as? Date ?: Date(),
                updatedAt = data["updatedAt"] as? Date ?: Date()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter perfil: ${e.message}")
            null
        }
    }
    
    /**
     * Busca prestadores por região e serviço
     */
    suspend fun findProvidersByRegionAndService(
        cep: String,
        service: String,
        limit: Int = 20
    ): List<ProviderProfile> {
        return try {
            Log.d(TAG, "Buscando prestadores para CEP: $cep, Serviço: $service")
            
            // Buscar por CEP (primeiros 5 dígitos)
            val cepPrefix = cep.take(5)
            
            val snapshot = db.collection(PROVIDERS_COLLECTION)
                .whereEqualTo("isActive", true)
                .whereArrayContains("services", service)
                .limit(limit.toLong())
                .get()
                .await()
            
            val providers = mutableListOf<ProviderProfile>()
            
            snapshot.documents.forEach { doc ->
                try {
                    val data = doc.data ?: return@forEach
                    val addressData = data["address"] as? Map<String, Any> ?: return@forEach
                    val bankData = data["bank"] as? Map<String, Any> ?: return@forEach
                    
                    val providerCep = addressData["cep"] as? String ?: ""
                    val providerCepPrefix = providerCep.take(5)
                    
                    // Filtrar por proximidade do CEP
                    if (providerCepPrefix == cepPrefix) {
                        val profile = ProviderProfile(
                            uid = doc.id,
                            email = data["email"] as? String ?: "",
                            fullName = data["fullName"] as? String ?: "",
                            phone = data["phone"] as? String ?: "",
                            cpf = data["cpf"] as? String,
                            address = Address(
                                cep = providerCep,
                                street = addressData["street"] as? String ?: "",
                                number = addressData["number"] as? String ?: "",
                                complement = addressData["complement"] as? String,
                                city = addressData["city"] as? String ?: "",
                                state = addressData["state"] as? String ?: "",
                                coordinates = addressData["coordinates"] as? Map<String, Double>
                            ),
                            bank = BankInfo(
                                bankName = bankData["bankName"] as? String ?: "",
                                agency = bankData["agency"] as? String ?: "",
                                account = bankData["account"] as? String ?: "",
                                accountType = bankData["accountType"] as? String ?: "CONTA_CORRENTE"
                            ),
                            services = (data["services"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                            verificationStatus = data["verificationStatus"] as? String ?: "pending",
                            rating = (data["rating"] as? Double) ?: 0.0,
                            totalJobs = (data["totalJobs"] as? Long)?.toInt() ?: 0,
                            completedJobs = (data["completedJobs"] as? Long)?.toInt() ?: 0,
                            isActive = data["isActive"] as? Boolean ?: true,
                            bio = data["bio"] as? String ?: "",
                            profileImageUrl = data["profileImageUrl"] as? String,
                            createdAt = data["createdAt"] as? Date ?: Date(),
                            updatedAt = data["updatedAt"] as? Date ?: Date()
                        )
                        providers.add(profile)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao processar prestador: ${e.message}")
                }
            }
            
            Log.d(TAG, "Encontrados ${providers.size} prestadores")
            providers
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar prestadores: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Atualiza a URL da foto de perfil do prestador
     */
    suspend fun updateProfileImage(uid: String, profileImageUrl: String): ProviderResult {
        return try {
            Log.d(TAG, "Atualizando foto de perfil do prestador: $uid")
            
            db.collection(PROVIDERS_COLLECTION)
                .document(uid)
                .update(
                    mapOf(
                        "profileImageUrl" to profileImageUrl,
                        "updatedAt" to Date()
                    )
                )
                .await()
            
            Log.d(TAG, "Foto de perfil atualizada: $uid")
            ProviderResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar foto de perfil: ${e.message}")
            ProviderResult.Error("Erro ao atualizar foto de perfil: ${e.message}")
        }
    }
    
    /**
     * Atualiza status de verificação
     */
    suspend fun updateVerificationStatus(
        uid: String,
        status: String,
        notes: String = ""
    ): ProviderResult {
        return try {
            Log.d(TAG, "Atualizando status de verificação: $uid -> $status")
            
            db.collection(PROVIDERS_COLLECTION)
                .document(uid)
                .update(
                    mapOf(
                        "verificationStatus" to status,
                        "updatedAt" to Date()
                    )
                )
                .await()
            
            Log.d(TAG, "Status de verificação atualizado: $uid")
            ProviderResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar status: ${e.message}")
            ProviderResult.Error("Erro ao atualizar status: ${e.message}")
        }
    }
    
    /**
     * Atualiza rating do prestador
     */
    suspend fun updateRating(
        uid: String,
        newRating: Double,
        totalJobs: Int,
        completedJobs: Int
    ): ProviderResult {
        return try {
            Log.d(TAG, "Atualizando rating do prestador: $uid -> $newRating")
            
            db.collection(PROVIDERS_COLLECTION)
                .document(uid)
                .update(
                    mapOf(
                        "rating" to newRating,
                        "totalJobs" to totalJobs,
                        "completedJobs" to completedJobs,
                        "updatedAt" to Date()
                    )
                )
                .await()
            
            Log.d(TAG, "Rating atualizado: $uid")
            ProviderResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar rating: ${e.message}")
            ProviderResult.Error("Erro ao atualizar rating: ${e.message}")
        }
    }
    
    /**
     * Ativa/desativa prestador
     */
    suspend fun setActiveStatus(
        uid: String,
        isActive: Boolean
    ): ProviderResult {
        return try {
            Log.d(TAG, "Atualizando status ativo: $uid -> $isActive")
            
            db.collection(PROVIDERS_COLLECTION)
                .document(uid)
                .update(
                    mapOf(
                        "isActive" to isActive,
                        "updatedAt" to Date()
                    )
                )
                .await()
            
            Log.d(TAG, "Status ativo atualizado: $uid")
            ProviderResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar status ativo: ${e.message}")
            ProviderResult.Error("Erro ao atualizar status ativo: ${e.message}")
        }
    }
    
    /**
     * Obtém todos os prestadores verificados
     */
    suspend fun getAllVerifiedProviders(limit: Int = 50): List<ProviderProfile> {
        return try {
            Log.d(TAG, "Buscando todos os prestadores verificados")
            
            val snapshot = db.collection(PROVIDERS_COLLECTION)
                .whereEqualTo("verificationStatus", "verified")
                .whereEqualTo("isActive", true)
                .orderBy("rating", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            
            val providers = mutableListOf<ProviderProfile>()
            
            snapshot.documents.forEach { doc ->
                try {
                    val data = doc.data ?: return@forEach
                    val addressData = data["address"] as? Map<String, Any> ?: return@forEach
                    val bankData = data["bank"] as? Map<String, Any> ?: return@forEach
                    
                    val profile = ProviderProfile(
                        uid = doc.id,
                        email = data["email"] as? String ?: "",
                        fullName = data["fullName"] as? String ?: "",
                        phone = data["phone"] as? String ?: "",
                        cpf = data["cpf"] as? String,
                        address = Address(
                            cep = addressData["cep"] as? String ?: "",
                            street = addressData["street"] as? String ?: "",
                            number = addressData["number"] as? String ?: "",
                            complement = addressData["complement"] as? String,
                            city = addressData["city"] as? String ?: "",
                            state = addressData["state"] as? String ?: "",
                            coordinates = addressData["coordinates"] as? Map<String, Double>
                        ),
                        bank = BankInfo(
                            bankName = bankData["bankName"] as? String ?: "",
                            agency = bankData["agency"] as? String ?: "",
                            account = bankData["account"] as? String ?: "",
                            accountType = bankData["accountType"] as? String ?: "CONTA_CORRENTE"
                        ),
                        services = (data["services"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        verificationStatus = data["verificationStatus"] as? String ?: "pending",
                        rating = (data["rating"] as? Double) ?: 0.0,
                        totalJobs = (data["totalJobs"] as? Long)?.toInt() ?: 0,
                        completedJobs = (data["completedJobs"] as? Long)?.toInt() ?: 0,
                        isActive = data["isActive"] as? Boolean ?: true,
                        bio = data["bio"] as? String ?: "",
                        profileImageUrl = data["profileImageUrl"] as? String,
                        createdAt = data["createdAt"] as? Date ?: Date(),
                        updatedAt = data["updatedAt"] as? Date ?: Date()
                    )
                    providers.add(profile)
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao processar prestador: ${e.message}")
                }
            }
            
            Log.d(TAG, "Encontrados ${providers.size} prestadores verificados")
            providers
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar prestadores verificados: ${e.message}")
            emptyList()
        }
    }
}