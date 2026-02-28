package com.example.loginapp

import android.util.Log
import com.example.loginapp.utils.awaitCurrentUser
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Gerenciador de documentos de prestadores no Firebase
 */
class FirebaseProviderDocumentManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "FirebaseProviderDocumentManager"
        private const val DOCUMENTS_COLLECTION = "provider_documents"
    }
    
    /**
     * Dados de documento do prestador
     */
    data class ProviderDocument(
        val id: String = "",
        val userId: String = "",
        val documentType: String = "", // RG, CPF, CNH, COMPROVANTE_RESIDENCIA
        val documentUrl: String = "",
        val fileName: String = "",
        val fileSize: Long = 0L,
        val mimeType: String = "",
        val verificationStatus: String = "PENDING", // PENDING, APPROVED, REJECTED
        val rejectionReason: String = "",
        val uploadedAt: Timestamp = Timestamp.now(),
        val verifiedAt: Timestamp? = null,
        val verifiedBy: String = ""
    )
    
    /**
     * Salva um documento do prestador
     */
    suspend fun saveDocument(document: ProviderDocument): Result<String> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            // Gerar ID único para o documento
            val documentId = db.collection(DOCUMENTS_COLLECTION).document().id
            
            // Preparar dados do documento
            val documentData = document.copy(
                id = documentId,
                userId = user.uid,
                uploadedAt = Timestamp.now()
            )
            
            // Salvar no Firestore
            db.collection(DOCUMENTS_COLLECTION)
                .document(documentId)
                .set(documentData)
                .await()
            
            Log.d(TAG, "Documento salvo com sucesso: $documentId")
            Result.success(documentId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar documento", e)
            Result.failure(e)
        }
    }
    
    /**
     * Carrega documentos de um prestador
     */
    suspend fun loadUserDocuments(): Result<List<ProviderDocument>> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            val snapshot = db.collection(DOCUMENTS_COLLECTION)
                .whereEqualTo("userId", user.uid)
                .orderBy("uploadedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            val documents = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(ProviderDocument::class.java)
                } catch (e: Exception) {
                    Log.w(TAG, "Erro ao converter documento: ${doc.id}", e)
                    null
                }
            }
            
            Log.d(TAG, "Documentos carregados: ${documents.size}")
            Result.success(documents)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar documentos", e)
            Result.failure(e)
        }
    }
    
    /**
     * Atualiza status de verificação de um documento
     */
    suspend fun updateVerificationStatus(
        documentId: String,
        status: String,
        rejectionReason: String = "",
        verifiedBy: String = ""
    ): Result<Unit> {
        return try {
            val updateData = hashMapOf<String, Any>(
                "verificationStatus" to status,
                "verifiedAt" to Timestamp.now(),
                "verifiedBy" to verifiedBy
            )
            
            if (rejectionReason.isNotEmpty()) {
                updateData["rejectionReason"] = rejectionReason
            }
            
            db.collection(DOCUMENTS_COLLECTION)
                .document(documentId)
                .update(updateData)
                .await()
            
            Log.d(TAG, "Status de verificação atualizado: $documentId -> $status")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar status de verificação", e)
            Result.failure(e)
        }
    }
    
    /**
     * Remove um documento
     */
    suspend fun deleteDocument(documentId: String): Result<Unit> {
        return try {
            db.collection(DOCUMENTS_COLLECTION)
                .document(documentId)
                .delete()
                .await()
            
            Log.d(TAG, "Documento removido: $documentId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover documento", e)
            Result.failure(e)
        }
    }
    
    /**
     * Verifica se o prestador tem todos os documentos necessários
     */
    suspend fun checkRequiredDocuments(): Result<Map<String, Boolean>> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            val snapshot = db.collection(DOCUMENTS_COLLECTION)
                .whereEqualTo("userId", user.uid)
                .whereEqualTo("verificationStatus", "APPROVED")
                .get()
                .await()
            
            val approvedDocuments = snapshot.documents.mapNotNull { doc ->
                doc.getString("documentType")
            }.toSet()
            
            val requiredDocuments = setOf("RG", "CPF", "COMPROVANTE_RESIDENCIA")
            val hasAllDocuments = requiredDocuments.all { it in approvedDocuments }
            
            val documentStatus = requiredDocuments.associateWith { docType ->
                docType in approvedDocuments
            }
            
            Log.d(TAG, "Status dos documentos: $documentStatus")
            Result.success(documentStatus)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar documentos necessários", e)
            Result.failure(e)
        }
    }
}

