package com.example.loginapp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Gerenciador de verificação de prestadores
 * 
 * Funcionalidades:
 * - Upload de documentos obrigatórios (RG, foto do rosto)
 * - Validação de documentos
 * - Acompanhamento do status de verificação
 * - Notificações de aprovação/rejeição
 */
class ProviderVerificationManager {
    
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    
    companion object {
        private const val TAG = "ProviderVerificationManager"
        private const val PROVIDERS_COLLECTION = "providers"
        private const val DOCUMENTS_COLLECTION = "provider_documents"
    }
    
    /**
     * Tipos de documentos para verificação
     */
    enum class DocumentType(
        val displayName: String,
        val required: Boolean,
        val description: String,
        val maxSizeMB: Int = 10
    ) {
        RG_FRONT("RG (Frente)", false, "Documento de identidade - lado da foto", 5),
        RG_BACK("RG (Verso)", false, "Documento de identidade - lado dos dados", 5),
        CNH_FRONT("CNH (Frente)", false, "Carteira de motorista - frente", 5),
        CNH_BACK("CNH (Verso)", false, "Carteira de motorista - verso", 5),
        SELFIE("Foto do Rosto", true, "Selfie com documento em mãos (reconhecimento facial)", 3),
        PROOF_OF_ADDRESS("Comprovante de Residência", false, "Conta de luz, água ou telefone", 5),
        BANK_STATEMENT("Extrato Bancário", false, "Comprovante de conta bancária", 5),
        WORK_CERTIFICATE("Certidão de Trabalho", false, "Comprovante de experiência", 5)
    }
    
    /**
     * Status de verificação
     */
    enum class VerificationStatus {
        PENDING,        // Aguardando documentos
        UNDER_REVIEW,   // Em análise
        APPROVED,       // Aprovado
        REJECTED,       // Rejeitado
        EXPIRED         // Expirado
    }
    
    /**
     * Status de documento
     */
    enum class DocumentStatus {
        PENDING,        // Aguardando upload
        UPLOADED,       // Enviado
        VALIDATED,      // Validado
        REJECTED,       // Rejeitado
        EXPIRED         // Expirado
    }
    
    /**
     * Dados de verificação
     */
    data class VerificationData(
        val id: String = "",
        val providerId: String = "",
        val status: VerificationStatus = VerificationStatus.PENDING,
        val submittedAt: Date? = null,
        val reviewedAt: Date? = null,
        val reviewedBy: String? = null,
        val rejectionReason: String? = null,
        val notes: String = "",
        val createdAt: Date = Date(),
        val expiresAt: Date = Date(System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L)) // 30 dias
    )
    
    /**
     * Dados de documento
     */
    data class DocumentData(
        val id: String = "",
        val verificationId: String = "",
        val providerId: String = "",
        val type: DocumentType,
        val fileName: String = "",
        val fileSize: Long = 0,
        val fileUri: String = "",
        val status: DocumentStatus = DocumentStatus.PENDING,
        val uploadedAt: Date? = null,
        val validatedAt: Date? = null,
        val validationNotes: String = "",
        val rejectionReason: String = ""
    )
    
    /**
     * Resultado de operações
     */
    sealed class VerificationResult {
        object Success : VerificationResult()
        data class VerificationCreated(val verificationId: String) : VerificationResult()
        data class Error(val message: String) : VerificationResult()
    }
    
    /**
     * Inicia processo de verificação.
     * O status é gerenciado na coleção providers - não cria documentos em provider_verifications.
     * Retorna VerificationCreated(providerId) para compatibilidade com fluxo de upload de documentos.
     */
    suspend fun startVerification(providerId: String): VerificationResult {
        return try {
            Log.d(TAG, "Iniciando verificação para prestador: $providerId (status em providers)")
            // O prestador já existe na coleção providers com verificationStatus do cadastro
            VerificationResult.VerificationCreated(providerId)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar verificação: ${e.message}")
            VerificationResult.Error("Erro ao iniciar verificação: ${e.message}")
        }
    }
    
    /**
     * Obtém status da verificação da coleção providers.
     * Usa o campo verificationStatus do documento do prestador.
     * "verified" ou "verificado" = aprovado.
     */
    suspend fun getVerificationStatus(providerId: String): VerificationData? {
        return try {
            Log.d(TAG, "🔍 Consultando verificação para prestador: $providerId")
            Log.d(TAG, "📂 Coleção: $PROVIDERS_COLLECTION")
            
            val doc = db.collection(PROVIDERS_COLLECTION)
                .document(providerId)
                .get()
                .await()

            if (!doc.exists()) {
                Log.w(TAG, "❌ Prestador não encontrado na coleção providers")
                return null
            }

            val statusString = doc.getString("verificationStatus") ?: "pending"
            Log.d(TAG, "📋 Status encontrado: $statusString")

            VerificationData(
                id = providerId,
                providerId = providerId,
                status = when (statusString.lowercase()) {
                    "verified", "verificado" -> VerificationStatus.APPROVED
                    "rejected", "rejeitado" -> VerificationStatus.REJECTED
                    "under_review", "em_analise" -> VerificationStatus.UNDER_REVIEW
                    "expired", "expirado" -> VerificationStatus.EXPIRED
                    else -> VerificationStatus.PENDING
                },
                submittedAt = doc.getDate("updatedAt"),
                reviewedAt = doc.getDate("updatedAt"),
                reviewedBy = doc.getString("reviewedBy"),
                rejectionReason = doc.getString("rejectionReason"),
                notes = doc.getString("notes") ?: "",
                createdAt = doc.getDate("createdAt") ?: Date(),
                expiresAt = doc.getDate("expiresAt") ?: Date()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao obter status da verificação: ${e.message}", e)
            null
        }
    }
    
    /**
     * Limpa dados inconsistentes de verificação.
     * O status é gerenciado na coleção providers - corrige se under_review sem documentos.
     */
    suspend fun cleanInconsistentVerificationData(providerId: String): VerificationResult {
        return try {
            Log.d(TAG, "🧹 Limpando dados inconsistentes para: $providerId")
            
            val verification = getVerificationStatus(providerId)
            if (verification == null) {
                Log.d(TAG, "Nenhum prestador encontrado")
                return VerificationResult.Success
            }
            
            val documents = getProviderDocuments(providerId)
            Log.d(TAG, "Status atual: ${verification.status}, Documentos encontrados: ${documents.size}")
            
            // Se está marcado como UNDER_REVIEW mas não tem documentos válidos, corrigir em providers
            if (verification.status == VerificationStatus.UNDER_REVIEW && documents.isEmpty()) {
                Log.w(TAG, "⚠️ Status under_review sem documentos válidos. Corrigindo em providers...")
                
                db.collection(PROVIDERS_COLLECTION)
                    .document(providerId)
                    .update(
                        mapOf(
                            "verificationStatus" to "pending",
                            "updatedAt" to Date()
                        )
                    )
                    .await()
                
                Log.d(TAG, "✅ Status corrigido para pending")
            }
            
            VerificationResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao limpar dados inconsistentes: ${e.message}")
            VerificationResult.Error("Erro ao limpar dados: ${e.message}")
        }
    }
    
    /**
     * Upload de documento usando FirebaseImageManager (mesma abordagem dos pedidos)
     */
    suspend fun uploadDocument(
        context: android.content.Context,
        providerId: String,
        documentType: DocumentType,
        fileName: String,
        fileSize: Long,
        fileUri: Uri
    ): VerificationResult {
        return try {
            Log.d(TAG, "Iniciando upload de documento: $documentType para prestador: $providerId")
            
            // Garantir que exista uma verificação ativa; caso não exista, cria
            var verification = getVerificationStatus(providerId)
            if (verification == null) {
                when (val createResult = startVerification(providerId)) {
                    is VerificationResult.VerificationCreated -> {
                        verification = getVerificationStatus(providerId)
                    }
                    else -> {
                        // segue sem verificação (permitir upload mesmo assim)
                        verification = null
                    }
                }
            }
            
            // Validar arquivo
            val validationError = validateDocument(fileName, fileSize, documentType)
            if (validationError != null) {
                return VerificationResult.Error(validationError)
            }
            
            // Upload usando FirebaseImageManager (mesma abordagem dos pedidos)
            // Salvar sempre em Documentos/{userId}/...
            val folderName = FirebaseImageManager.FOLDER_DOCUMENTOS
            
            val imageManager = FirebaseImageManager()
            val uploadData = FirebaseImageManager.ImageUploadData(
                uri = fileUri,
                fileName = fileName,
                folder = folderName,
                userId = providerId,
                metadata = mapOf(
                    "documentType" to documentType.name,
                    "providerId" to providerId,
                    "verificationId" to (verification?.id ?: "")
                )
            )
            
            val uploadResult = imageManager.uploadImage(context, uploadData)
            val downloadUrl = when (uploadResult) {
                is FirebaseImageManager.UploadResult.Success -> uploadResult.downloadUrl
                is FirebaseImageManager.UploadResult.Error -> return VerificationResult.Error(uploadResult.message)
                else -> return VerificationResult.Error("Erro no upload")
            }
            
            // Salvar dados do documento no Firestore
            val documentId = db.collection(DOCUMENTS_COLLECTION).document().id
            val documentData = DocumentData(
                id = documentId,
                verificationId = verification?.id ?: "",
                providerId = providerId,
                type = documentType,
                fileName = fileName,
                fileSize = fileSize,
                fileUri = downloadUrl.toString(),
                status = DocumentStatus.UPLOADED,
                uploadedAt = Date()
            )
            
            db.collection(DOCUMENTS_COLLECTION)
                .document(documentId)
                .set(documentData)
                .await()
            
            Log.d(TAG, "Documento enviado com sucesso: $documentId")
            VerificationResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fazer upload do documento: ${e.message}")
            VerificationResult.Error("Erro ao enviar documento: ${e.message}")
        }
    }
    
    /**
     * Verifica se um arquivo realmente existe no Firebase Storage
     */
    suspend fun verifyDocumentExists(fileUrl: String): Boolean {
        return try {
            if (fileUrl.isEmpty()) return false
            
            val storageRef = storage.getReferenceFromUrl(fileUrl)
            storageRef.metadata.await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Documento não encontrado no Storage: ${e.message}")
            false
        }
    }
    
    /**
     * Obtém documentos do prestador e verifica se realmente existem
     */
    suspend fun getProviderDocuments(providerId: String): List<DocumentData> {
        return try {
            val snapshot = db.collection(DOCUMENTS_COLLECTION)
                .whereEqualTo("providerId", providerId)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    val fileUri = doc.getString("fileUri") ?: ""
                    
                    // Verificar se o arquivo realmente existe no Storage
                    val documentExists = if (fileUri.isNotEmpty()) {
                        verifyDocumentExists(fileUri)
                    } else {
                        false
                    }
                    
                    // Se o documento não existe, marcar como PENDING ou deletar registro
                    if (!documentExists && fileUri.isNotEmpty()) {
                        Log.w(TAG, "Documento ${doc.id} marcado como UPLOADED mas arquivo não existe no Storage. Corrigindo...")
                        
                        // Deletar registro inválido do Firestore
                        db.collection(DOCUMENTS_COLLECTION)
                            .document(doc.id)
                            .delete()
                            .await()
                        
                        return@mapNotNull null
                    }
                    
                    DocumentData(
                        id = doc.id,
                        verificationId = doc.getString("verificationId") ?: "",
                        providerId = doc.getString("providerId") ?: "",
                        type = DocumentType.valueOf(doc.getString("type") ?: ""),
                        fileName = doc.getString("fileName") ?: "",
                        fileSize = doc.getLong("fileSize") ?: 0,
                        fileUri = fileUri,
                        status = when (doc.getString("status")) {
                            "PENDING" -> DocumentStatus.PENDING
                            "UPLOADED" -> DocumentStatus.UPLOADED
                            "VALIDATED" -> DocumentStatus.VALIDATED
                            "REJECTED" -> DocumentStatus.REJECTED
                            "EXPIRED" -> DocumentStatus.EXPIRED
                            else -> DocumentStatus.PENDING
                        },
                        uploadedAt = doc.getDate("uploadedAt"),
                        validatedAt = doc.getDate("validatedAt"),
                        validationNotes = doc.getString("validationNotes") ?: "",
                        rejectionReason = doc.getString("rejectionReason") ?: ""
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao processar documento: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter documentos: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Submete verificação para análise
     */
    suspend fun submitForReview(providerId: String): VerificationResult {
        return try {
            Log.d(TAG, "Submetendo verificação para análise: $providerId")
            
            val verification = getVerificationStatus(providerId)
            if (verification == null) {
                return VerificationResult.Error("Verificação não encontrada")
            }
            
            // Verificar se todos os documentos obrigatórios foram enviados
            // IMPORTANTE: Esta função já valida se os arquivos realmente existem no Storage
            val documents = getProviderDocuments(providerId)
            
            if (documents.isEmpty()) {
                return VerificationResult.Error("Nenhum documento encontrado. Faça upload dos documentos primeiro.")
            }
            
            Log.d(TAG, "Documentos encontrados e validados: ${documents.size}")
            
            // Regras: SELFIE obrigatória + (RG frente/verso) OU (CNH frente/verso)
            val hasSelfie = documents.any { it.type == DocumentType.SELFIE && it.status == DocumentStatus.UPLOADED }
            val hasRgPair = documents.any { it.type == DocumentType.RG_FRONT && it.status == DocumentStatus.UPLOADED } &&
                    documents.any { it.type == DocumentType.RG_BACK && it.status == DocumentStatus.UPLOADED }
            val hasCnhPair = documents.any { it.type == DocumentType.CNH_FRONT && it.status == DocumentStatus.UPLOADED } &&
                    documents.any { it.type == DocumentType.CNH_BACK && it.status == DocumentStatus.UPLOADED }

            Log.d(TAG, "Validação de documentos: SELFIE=$hasSelfie, RG=$hasRgPair, CNH=$hasCnhPair")

            if (!hasSelfie) {
                return VerificationResult.Error("❌ Selfie obrigatória não encontrada. Faça upload novamente.")
            }
            
            if (!hasRgPair && !hasCnhPair) {
                return VerificationResult.Error("❌ Envie RG (frente/verso) OU CNH (frente/verso). Documentos não encontrados no sistema.")
            }
            
            // VERIFICAÇÃO ADICIONAL: Garantir que os arquivos realmente existem no Storage
            Log.d(TAG, "Verificando existência dos arquivos no Firebase Storage...")
            for (doc in documents) {
                if (!verifyDocumentExists(doc.fileUri)) {
                    Log.e(TAG, "Arquivo não encontrado: ${doc.type} - ${doc.fileUri}")
                    return VerificationResult.Error("❌ Arquivo ${doc.type.displayName} não encontrado no servidor. Faça upload novamente.")
                }
            }
            
            Log.d(TAG, "✅ Todos os arquivos foram verificados e existem no Storage")
            
            // Atualizar status na coleção providers
            db.collection(PROVIDERS_COLLECTION)
                .document(providerId)
                .update(
                    mapOf(
                        "verificationStatus" to "under_review",
                        "updatedAt" to Date()
                    )
                )
                .await()
            
            Log.d(TAG, "Verificação submetida para análise: $providerId")
            VerificationResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao submeter verificação: ${e.message}")
            VerificationResult.Error("Erro ao submeter verificação: ${e.message}")
        }
    }
    
    /**
     * Remove documento
     */
    suspend fun removeDocument(documentId: String): VerificationResult {
        return try {
            Log.d(TAG, "Removendo documento: $documentId")
            
            // Obter dados do documento
            val doc = db.collection(DOCUMENTS_COLLECTION)
                .document(documentId)
                .get()
                .await()
            
            if (!doc.exists()) {
                return VerificationResult.Error("Documento não encontrado")
            }
            
            // Remover do Firestore
            db.collection(DOCUMENTS_COLLECTION)
                .document(documentId)
                .delete()
                .await()
            
            // Remover do Storage (opcional - pode manter para auditoria)
            val fileUri = doc.getString("fileUri")
            if (!fileUri.isNullOrEmpty()) {
                try {
                    val storageRef = storage.getReferenceFromUrl(fileUri)
                    storageRef.delete().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Erro ao remover arquivo do storage: ${e.message}")
                }
            }
            
            Log.d(TAG, "Documento removido: $documentId")
            VerificationResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover documento: ${e.message}")
            VerificationResult.Error("Erro ao remover documento: ${e.message}")
        }
    }
    
    /**
     * Valida documento
     */
    private fun validateDocument(
        fileName: String,
        fileSize: Long,
        documentType: DocumentType
    ): String? {
        // Verificar tamanho
        val maxSizeBytes = documentType.maxSizeMB * 1024 * 1024
        if (fileSize > maxSizeBytes) {
            return "Arquivo muito grande. Máximo ${documentType.maxSizeMB}MB para ${documentType.displayName}"
        }
        
        // Verificar extensão
        val allowedExtensions = listOf("jpg", "jpeg", "png", "pdf")
        val fileExtension = fileName.substringAfterLast(".", "").lowercase()
        if (fileExtension !in allowedExtensions) {
            return "Formato não suportado. Use JPG, PNG ou PDF"
        }
        
        // Validações específicas por tipo
        when (documentType) {
            DocumentType.SELFIE -> {
                if (fileExtension == "pdf") {
                    return "Foto do rosto deve ser uma imagem (JPG ou PNG)"
                }
            }
            DocumentType.RG_FRONT, DocumentType.RG_BACK -> {
                if (fileExtension == "pdf") {
                    return "RG deve ser uma imagem (JPG ou PNG)"
                }
            }
            else -> {
                // PDF é aceito para outros documentos
            }
        }
        
        return null
    }
    
    /**
     * Aprova verificação (admin).
     * Atualiza verificationStatus na coleção providers e isVerified na coleção users.
     * @param providerId ID do prestador (documento em providers)
     */
    suspend fun approveVerification(
        providerId: String,
        adminId: String,
        notes: String = ""
    ): VerificationResult {
        return try {
            Log.d(TAG, "Aprovando verificação do prestador: $providerId")
            
            // Atualizar status na coleção providers
            db.collection(PROVIDERS_COLLECTION)
                .document(providerId)
                .update(
                    mapOf(
                        "verificationStatus" to "verified",
                        "updatedAt" to Date(),
                        "reviewedBy" to adminId,
                        "notes" to notes
                    )
                )
                .await()
            
            // Atualizar isVerified na coleção users (consistência com login e rastreamento)
            try {
                db.collection("users")
                    .document(providerId)
                    .update("isVerified", true)
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Aviso: não foi possível atualizar users.isVerified: ${e.message}")
            }
            
            // Atualizar status dos documentos do prestador
            val documents = db.collection(DOCUMENTS_COLLECTION)
                .whereEqualTo("providerId", providerId)
                .get()
                .await()
            
            documents.documents.forEach { doc ->
                doc.reference.update(
                    mapOf(
                        "status" to "VALIDATED",
                        "validatedAt" to Date()
                    )
                ).await()
            }
            
            Log.d(TAG, "Verificação aprovada: $providerId")
            VerificationResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao aprovar verificação: ${e.message}")
            VerificationResult.Error("Erro ao aprovar verificação: ${e.message}")
        }
    }
    
    /**
     * Rejeita verificação (admin).
     * Atualiza verificationStatus na coleção providers.
     * @param providerId ID do prestador (documento em providers)
     */
    suspend fun rejectVerification(
        providerId: String,
        adminId: String,
        reason: String,
        notes: String = ""
    ): VerificationResult {
        return try {
            Log.d(TAG, "Rejeitando verificação do prestador: $providerId")
            
            db.collection(PROVIDERS_COLLECTION)
                .document(providerId)
                .update(
                    mapOf(
                        "verificationStatus" to "rejected",
                        "updatedAt" to Date(),
                        "reviewedBy" to adminId,
                        "rejectionReason" to reason,
                        "notes" to notes
                    )
                )
                .await()
            
            // Atualizar isVerified na coleção users
            try {
                db.collection("users")
                    .document(providerId)
                    .update("isVerified", false)
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Aviso: não foi possível atualizar users.isVerified: ${e.message}")
            }
            
            Log.d(TAG, "Verificação rejeitada: $providerId")
            VerificationResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao rejeitar verificação: ${e.message}")
            VerificationResult.Error("Erro ao rejeitar verificação: ${e.message}")
        }
    }
}