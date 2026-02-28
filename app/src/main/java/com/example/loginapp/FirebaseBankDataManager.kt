package com.example.loginapp

import android.util.Log
import com.example.loginapp.utils.awaitCurrentUser
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Gerenciador de dados bancários dos prestadores no Firebase
 */
class FirebaseBankDataManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "FirebaseBankDataManager"
        private const val BANK_DATA_COLLECTION = "provider_bank_data"
    }
    
    /**
     * Dados bancários do prestador
     */
    data class BankData(
        val id: String = "",
        val userId: String = "",
        val bankName: String = "",
        val bankCode: String = "",
        val agency: String = "",
        val account: String = "",
        val accountType: String = "CONTA_CORRENTE", // CONTA_CORRENTE, CONTA_POUPANCA
        val accountHolderName: String = "",
        val accountHolderDocument: String = "",
        val pixKey: String = "",
        val pixKeyType: String = "", // CPF, EMAIL, TELEFONE, ALEATORIA
        val isVerified: Boolean = false,
        val verificationStatus: String = "PENDING", // PENDING, VERIFIED, REJECTED
        val createdAt: Timestamp = Timestamp.now(),
        val updatedAt: Timestamp = Timestamp.now()
    )
    
    /**
     * Salva dados bancários do prestador
     */
    suspend fun saveBankData(bankData: BankData): Result<String> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            // Verificar se já existe dados bancários
            val existingData = loadBankData()
            val bankDataId = if (existingData.isSuccess && existingData.getOrNull() != null) {
                existingData.getOrNull()!!.id
            } else {
                db.collection(BANK_DATA_COLLECTION).document().id
            }
            
            // Preparar dados bancários
            val bankDataToSave = bankData.copy(
                id = bankDataId,
                userId = user.uid,
                updatedAt = Timestamp.now()
            )
            
            // Salvar no Firestore
            db.collection(BANK_DATA_COLLECTION)
                .document(bankDataId)
                .set(bankDataToSave)
                .await()
            
            Log.d(TAG, "Dados bancários salvos com sucesso: $bankDataId")
            Result.success(bankDataId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar dados bancários", e)
            Result.failure(e)
        }
    }
    
    /**
     * Carrega dados bancários do prestador
     */
    suspend fun loadBankData(): Result<BankData?> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            val snapshot = db.collection(BANK_DATA_COLLECTION)
                .whereEqualTo("userId", user.uid)
                .limit(1)
                .get()
                .await()
            
            if (snapshot.isEmpty) {
                Log.d(TAG, "Nenhum dado bancário encontrado")
                return Result.success(null)
            }
            
            val bankData = snapshot.documents.first().toObject(BankData::class.java)
            Log.d(TAG, "Dados bancários carregados: ${bankData?.id}")
            Result.success(bankData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar dados bancários", e)
            Result.failure(e)
        }
    }
    
    /**
     * Atualiza dados bancários
     */
    suspend fun updateBankData(bankData: BankData): Result<Unit> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            val updateData = hashMapOf<String, Any>(
                "bankName" to bankData.bankName,
                "bankCode" to bankData.bankCode,
                "agency" to bankData.agency,
                "account" to bankData.account,
                "accountType" to bankData.accountType,
                "accountHolderName" to bankData.accountHolderName,
                "accountHolderDocument" to bankData.accountHolderDocument,
                "pixKey" to bankData.pixKey,
                "pixKeyType" to bankData.pixKeyType,
                "updatedAt" to Timestamp.now()
            )
            
            db.collection(BANK_DATA_COLLECTION)
                .document(bankData.id)
                .update(updateData)
                .await()
            
            Log.d(TAG, "Dados bancários atualizados: ${bankData.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar dados bancários", e)
            Result.failure(e)
        }
    }
    
    /**
     * Verifica se os dados bancários estão completos
     */
    suspend fun isBankDataComplete(): Result<Boolean> {
        return try {
            val bankDataResult = loadBankData()
            if (bankDataResult.isFailure) {
                return Result.failure(bankDataResult.exceptionOrNull()!!)
            }
            
            val bankData = bankDataResult.getOrNull()
            if (bankData == null) {
                return Result.success(false)
            }
            
            val isComplete = bankData.bankName.isNotEmpty() &&
                    bankData.agency.isNotEmpty() &&
                    bankData.account.isNotEmpty() &&
                    bankData.accountHolderName.isNotEmpty() &&
                    bankData.accountHolderDocument.isNotEmpty()
            
            Log.d(TAG, "Dados bancários completos: $isComplete")
            Result.success(isComplete)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar dados bancários", e)
            Result.failure(e)
        }
    }
    
    /**
     * Valida dados bancários
     */
    fun validateBankData(bankData: BankData): List<String> {
        val errors = mutableListOf<String>()
        
        if (bankData.bankName.isEmpty()) {
            errors.add("Nome do banco é obrigatório")
        }
        
        if (bankData.agency.isEmpty()) {
            errors.add("Agência é obrigatória")
        }
        
        if (bankData.account.isEmpty()) {
            errors.add("Número da conta é obrigatório")
        }
        
        if (bankData.accountHolderName.isEmpty()) {
            errors.add("Nome do titular é obrigatório")
        }
        
        if (bankData.accountHolderDocument.isEmpty()) {
            errors.add("CPF/CNPJ do titular é obrigatório")
        }
        
        // Validar PIX se fornecido
        if (bankData.pixKey.isNotEmpty() && bankData.pixKeyType.isEmpty()) {
            errors.add("Tipo da chave PIX é obrigatório")
        }
        
        return errors
    }
}

