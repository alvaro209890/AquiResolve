package com.example.loginapp

import android.util.Log
import com.example.loginapp.models.SavedAddress
import com.example.loginapp.utils.awaitCurrentUser
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.tasks.await

/**
 * Gerenciador de endereços salvos no Firebase
 */
class FirebaseAddressManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "FirebaseAddressManager"
    }

    /**
     * Salva um novo endereço para o cliente
     */
    suspend fun saveAddress(address: SavedAddress): Result<String> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }

            // Gerar ID único para o endereço
            val addressId = db.collection(SavedAddress.COLLECTION_NAME).document().id
            
            // Preparar dados do endereço
            val addressData = address.copy(
                id = addressId,
                clientId = user.uid,
                updatedAt = Timestamp.now()
            )
            
            // Se este endereço for marcado como padrão, remover o padrão dos outros
            if (addressData.isDefault) {
                removeDefaultFromOtherAddresses(user.uid)
            }
            
            // Salvar no Firestore
            db.collection(SavedAddress.COLLECTION_NAME)
                .document(addressId)
                .set(addressData)
                .await()
            
            Log.d(TAG, "Endereço salvo com sucesso: $addressId")
            Result.success(addressId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar endereço: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Busca todos os endereços salvos do usuário, filtrados por tipo
     */
    suspend fun getUserAddresses(userType: String = SavedAddress.USER_TYPE_CLIENT): Result<List<SavedAddress>> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                Log.e(TAG, "Usuário não autenticado")
                return Result.failure(Exception("Usuário não autenticado"))
            }

            Log.d(TAG, "Buscando endereços para usuário: ${user.uid} tipo: $userType")

            val snapshot = db.collection(SavedAddress.COLLECTION_NAME)
                .whereEqualTo("clientId", user.uid)
                .whereEqualTo("userType", userType)
                .get()
                .await()
            
            Log.d(TAG, "📄 Documentos encontrados: ${snapshot.documents.size}")
            
            val addresses = snapshot.documents.mapNotNull { doc ->
                try {
                    val address = doc.toObject(SavedAddress::class.java)
                    Log.d(TAG, "✅ Endereço convertido: ${address?.name}")
                    address
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao converter documento: ${e.message}")
                    null
                }
            }.sortedWith(compareByDescending<SavedAddress> { it.isDefault }
                .thenByDescending { it.createdAt })
            
            Log.d(TAG, "✅ Endereços carregados: ${addresses.size}")
            Result.success(addresses)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao buscar endereços: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Atualiza um endereço existente
     */
    suspend fun updateAddress(address: SavedAddress): Result<Unit> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            // Se este endereço for marcado como padrão, remover o padrão dos outros
            if (address.isDefault) {
                removeDefaultFromOtherAddresses(user.uid)
            }
            
            val updatedAddress = address.copy(updatedAt = Timestamp.now())
            
            db.collection(SavedAddress.COLLECTION_NAME)
                .document(address.id)
                .set(updatedAddress)
                .await()
            
            Log.d(TAG, "Endereço atualizado com sucesso: ${address.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar endereço: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Remove um endereço
     */
    suspend fun deleteAddress(addressId: String): Result<Unit> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            db.collection(SavedAddress.COLLECTION_NAME)
                .document(addressId)
                .delete()
                .await()
            
            Log.d(TAG, "Endereço removido com sucesso: $addressId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover endereço: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Define um endereço como padrão
     */
    suspend fun setDefaultAddress(addressId: String): Result<Unit> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            // Remover padrão dos outros endereços
            removeDefaultFromOtherAddresses(user.uid)
            
            // Definir este como padrão
            db.collection(SavedAddress.COLLECTION_NAME)
                .document(addressId)
                .update("isDefault", true, "updatedAt", Timestamp.now())
                .await()
            
            Log.d(TAG, "Endereço definido como padrão: $addressId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao definir endereço padrão: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Remove o status de padrão de todos os endereços do usuário
     */
    private suspend fun removeDefaultFromOtherAddresses(clientId: String) {
        try {
            val snapshot = db.collection(SavedAddress.COLLECTION_NAME)
                .whereEqualTo("clientId", clientId)
                .whereEqualTo("isDefault", true)
                .get()
                .await()
            
            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.update(doc.reference, "isDefault", false, "updatedAt", Timestamp.now())
            }
            batch.commit().await()
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover padrão dos endereços: ${e.message}")
        }
    }
    
    /**
     * Busca o endereço padrão do usuário
     */
    suspend fun getDefaultAddress(): Result<SavedAddress?> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            val snapshot = db.collection(SavedAddress.COLLECTION_NAME)
                .whereEqualTo("clientId", user.uid)
                .whereEqualTo("isDefault", true)
                .limit(1)
                .get()
                .await()
            
            val address = if (snapshot.documents.isNotEmpty()) {
                snapshot.documents[0].toObject(SavedAddress::class.java)
            } else null
            
            Result.success(address)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar endereço padrão: ${e.message}")
            Result.failure(e)
        }
    }
}

