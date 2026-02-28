package com.example.loginapp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Gerenciador de imagens integrado com Firebase Storage
 * 
 * Funcionalidades:
 * - Upload de imagens para Firebase Storage
 * - Download de imagens do Firebase Storage
 * - Exclusão de imagens
 * - Gerenciamento de URLs
 * - Organização por pastas
 */
class FirebaseImageManager {
    
    private val storage: FirebaseStorage = FirebaseConfig.getStorage()
    private val storageRef: StorageReference = storage.reference
    
    companion object {
        private const val TAG = "FirebaseImageManager"
        
        // Pastas organizadas
        const val FOLDER_PROFILE_IMAGES = "profile_images"
        const val FOLDER_ORDER_IMAGES = "order_images"
        const val FOLDER_DOCUMENTS = "documents"
        const val FOLDER_DOCUMENTOS = "Documentos"
        const val FOLDER_CHAT_IMAGES = "chats"
        const val FOLDER_SERVICE_IMAGES = "service_images"
        const val FOLDER_PEDIDOS = "Pedidos"
        const val FOLDER_SELFIE = "Selfie"
        const val FOLDER_PROVIDER_DOCUMENTS = "provider_documents"
        const val FOLDER_SELFIES = "selfies"
    }
    
    /**
     * Dados de upload de imagem
     */
    data class ImageUploadData(
        val uri: Uri,
        val fileName: String,
        val folder: String,
        val userId: String? = null,
        val orderId: String? = null,
        val metadata: Map<String, String> = emptyMap()
    )
    
    /**
     * Resultado do upload
     */
    sealed class UploadResult {
        data class Success(val downloadUrl: String, val fileName: String) : UploadResult()
        data class Error(val message: String) : UploadResult()
        data class Progress(val progress: Int) : UploadResult()
    }
    
    /**
     * Resultado do download
     */
    sealed class DownloadResult {
        data class Success(val localUri: Uri) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }
    
    /**
     * Faz upload de uma imagem para o Firebase Storage
     */
    suspend fun uploadImage(
        context: Context,
        uploadData: ImageUploadData,
        onProgress: ((Int) -> Unit)? = null
    ): UploadResult {
        var fileRef: StorageReference? = null
        return try {
            Log.d(TAG, "Iniciando upload de imagem: ${uploadData.fileName}")
            
            // Criar referência do arquivo no Storage
            fileRef = createStorageReference(uploadData)
            
            // Obter InputStream da URI
            val inputStream = getInputStreamFromUri(context, uploadData.uri)
                ?: return UploadResult.Error("Não foi possível ler o arquivo")
            
            // Criar metadados (usar mimeType quando disponível)
            val contentType = uploadData.metadata["mimeType"]?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(contentType)
                .apply {
                    uploadData.metadata.forEach { (key, value) ->
                        setCustomMetadata(key, value)
                    }
                }
                .build()
            
            // Fazer upload
            val uploadTask = fileRef.putStream(inputStream, metadata)
            
            // Monitorar progresso
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                onProgress?.invoke(progress)
            }
            
            // Aguardar conclusão
            val result = uploadTask.await()
            
            // Obter URL de download
            val downloadUrl = result.storage.downloadUrl.await()
            
            Log.d(TAG, "Upload concluído: $downloadUrl")
            UploadResult.Success(downloadUrl.toString(), uploadData.fileName)
            
        } catch (e: CancellationException) {
            // A corrotina foi cancelada (ex.: Activity/Fragment destruído),
            // mas o upload pode ter sido concluído no Storage. Tentar recuperar a URL.
            Log.w(TAG, "Upload cancelado pela corrotina, tentando recuperar URL de download...", e)
            return try {
                val ref = fileRef
                if (ref != null) {
                    val downloadUrl = ref.downloadUrl.await()
                    Log.d(TAG, "Upload confirmado após cancelamento: $downloadUrl")
                    UploadResult.Success(downloadUrl.toString(), uploadData.fileName)
                } else {
                    UploadResult.Error("Upload cancelado antes de criar referência")
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Falha ao recuperar URL após cancelamento", ex)
                UploadResult.Error("Upload cancelado: ${ex.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro no upload", e)
            UploadResult.Error("Erro no upload: ${e.message}")
        }
    }
    
    /**
     * Faz upload de múltiplas imagens
     */
    suspend fun uploadMultipleImages(
        context: Context,
        uploadDataList: List<ImageUploadData>,
        onProgress: ((Int, Int) -> Unit)? = null // (current, total)
    ): List<UploadResult> {
        val results = mutableListOf<UploadResult>()
        
        uploadDataList.forEachIndexed { index, uploadData ->
            val result = uploadImage(context, uploadData) { progress ->
                onProgress?.invoke(progress, uploadDataList.size)
            }
            results.add(result)
        }
        
        return results
    }
    
    /**
     * Faz download de uma imagem do Firebase Storage
     */
    suspend fun downloadImage(
        context: Context,
        downloadUrl: String,
        fileName: String
    ): DownloadResult {
        return try {
            Log.d(TAG, "Iniciando download: $fileName")
            
            // Criar arquivo local
            val localFile = File(context.cacheDir, fileName)
            
            // Fazer download
            val storageRef = storage.getReferenceFromUrl(downloadUrl)
            storageRef.getFile(localFile).await()
            
            Log.d(TAG, "Download concluído: ${localFile.absolutePath}")
            DownloadResult.Success(Uri.fromFile(localFile))
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro no download", e)
            DownloadResult.Error("Erro no download: ${e.message}")
        }
    }
    
    /**
     * Exclui uma imagem do Firebase Storage
     */
    suspend fun deleteImage(downloadUrl: String): Boolean {
        return try {
            Log.d(TAG, "Excluindo imagem: $downloadUrl")
            
            val storageRef = storage.getReferenceFromUrl(downloadUrl)
            storageRef.delete().await()
            
            Log.d(TAG, "Imagem excluída com sucesso")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao excluir imagem", e)
            false
        }
    }
    
    /**
     * Exclui múltiplas imagens
     */
    suspend fun deleteMultipleImages(downloadUrls: List<String>): List<Boolean> {
        return downloadUrls.map { url ->
            deleteImage(url)
        }
    }
    
    /**
     * Obtém URL de download de uma imagem
     */
    suspend fun getDownloadUrl(filePath: String): String? {
        return try {
            val storageRef = storageRef.child(filePath)
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter URL de download", e)
            null
        }
    }
    
    /**
     * Lista imagens de uma pasta
     */
    suspend fun listImages(folder: String): List<String> {
        return try {
            val folderRef = storageRef.child(folder)
            val result = folderRef.listAll().await()
            
            val downloadUrls = mutableListOf<String>()
            result.items.forEach { item ->
                val downloadUrl = item.downloadUrl.await().toString()
                downloadUrls.add(downloadUrl)
            }
            
            downloadUrls
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao listar imagens", e)
            emptyList()
        }
    }
    
    /**
     * Cria referência do arquivo no Storage
     */
    private fun createStorageReference(uploadData: ImageUploadData): StorageReference {
        val fileName = generateUniqueFileName(uploadData.fileName)
        
        return when (uploadData.folder) {
            FOLDER_PROFILE_IMAGES -> {
                storageRef.child(FOLDER_PROFILE_IMAGES)
                    .child(uploadData.userId ?: "unknown")
                    .child(fileName)
            }
            FOLDER_DOCUMENTOS -> {
                storageRef.child(FOLDER_DOCUMENTOS)
                    .child(uploadData.userId ?: "unknown")
                    .child(fileName)
            }
            FOLDER_ORDER_IMAGES -> {
                storageRef.child(FOLDER_ORDER_IMAGES)
                    .child(uploadData.orderId ?: "unknown")
                    .child(fileName)
            }
            FOLDER_PEDIDOS -> {
                storageRef.child(FOLDER_PEDIDOS)
                    .child(uploadData.orderId ?: "unknown")
                    .child(fileName)
            }
            FOLDER_CHAT_IMAGES -> {
                storageRef.child(FOLDER_CHAT_IMAGES)
                    .child(uploadData.orderId ?: "unknown")
                    .child(fileName)
            }
            FOLDER_SERVICE_IMAGES -> {
                storageRef.child(FOLDER_SERVICE_IMAGES)
                    .child(uploadData.userId ?: "unknown")
                    .child(fileName)
            }
            FOLDER_SELFIE -> {
                storageRef.child(FOLDER_SELFIE)
                    .child(uploadData.userId ?: "unknown")
                    .child(fileName)
            }
            FOLDER_SELFIES -> {
                storageRef.child(FOLDER_SELFIES)
                    .child(uploadData.userId ?: "unknown")
                    .child(fileName)
            }
            FOLDER_PROVIDER_DOCUMENTS -> {
                storageRef.child(FOLDER_PROVIDER_DOCUMENTS)
                    .child(uploadData.userId ?: "unknown")
                    .child(fileName)
            }
            else -> {
                storageRef.child(uploadData.folder).child(fileName)
            }
        }
    }
    
    /**
     * Gera nome único para o arquivo
     */
    private fun generateUniqueFileName(originalFileName: String): String {
        val timestamp = System.currentTimeMillis()
        val extension = originalFileName.substringAfterLast('.', "jpg")
        return "${timestamp}_${originalFileName.substringBeforeLast('.')}.$extension"
    }
    
    /**
     * Obtém InputStream de uma URI
     */
    private fun getInputStreamFromUri(context: Context, uri: Uri): InputStream? {
        return try {
            when (uri.scheme) {
                "content" -> context.contentResolver.openInputStream(uri)
                "file" -> FileInputStream(File(uri.path ?: ""))
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter InputStream", e)
            null
        }
    }
    
    /**
     * Obtém tamanho do arquivo em bytes
     */
    suspend fun getFileSize(downloadUrl: String): Long {
        return try {
            val storageRef = storage.getReferenceFromUrl(downloadUrl)
            val metadata = storageRef.metadata.await()
            metadata.sizeBytes
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter tamanho do arquivo", e)
            0L
        }
    }
    
    /**
     * Verifica se uma imagem existe no Storage
     */
    suspend fun imageExists(downloadUrl: String): Boolean {
        return try {
            val storageRef = storage.getReferenceFromUrl(downloadUrl)
            storageRef.metadata.await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Obtém metadados de uma imagem
     */
    suspend fun getImageMetadata(downloadUrl: String): Map<String, String>? {
        return try {
            val storageRef = storage.getReferenceFromUrl(downloadUrl)
            val metadata = storageRef.metadata.await()
            metadata.customMetadataKeys.associateWith { key ->
                metadata.getCustomMetadata(key) ?: ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter metadados", e)
            null
        }
    }
}
