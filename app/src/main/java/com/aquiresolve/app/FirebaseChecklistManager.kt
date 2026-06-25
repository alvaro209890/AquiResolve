package com.aquiresolve.app

import android.util.Log
import com.aquiresolve.app.models.OsChecklistData
import com.aquiresolve.app.models.OrderData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirebaseChecklistManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FirebaseChecklistManager"
        private const val CHECKLISTS_COLLECTION = "checklists"
    }

    private fun currentProviderId(): String? = auth.currentUser?.uid

    private fun addProviderMetadata(data: MutableMap<String, Any?>) {
        currentProviderId()?.let { data["providerId"] = it }
    }

    suspend fun getChecklist(orderId: String): Result<OsChecklistData?> {
        return try {
            val doc = db.collection(CHECKLISTS_COLLECTION).document(orderId).get().await()
            if (doc.exists()) {
                val data = doc.data
                if (data != null) {
                    Result.success(OsChecklistData.fromMap(data, orderId))
                } else {
                    Result.success(null)
                }
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar checklist: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun createOrUpdateChecklist(data: OsChecklistData): Result<Unit> {
        return try {
            val updates = data.toMap().toMutableMap()
            updates["updatedAt"] = Timestamp.now()
            addProviderMetadata(updates)

            db.collection(CHECKLISTS_COLLECTION)
                .document(data.orderId)
                .set(updates, SetOptions.merge())
                .await()

            Log.d(TAG, "Checklist salvo para pedido: ${data.orderId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar checklist: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun startService(
        orderId: String,
        latitude: Double?,
        longitude: Double?
    ): Result<OsChecklistData> {
        return try {
            val now = Timestamp.now()
            val checklist = OsChecklistData(
                orderId = orderId,
                providerId = currentProviderId(),
                status = OsChecklistData.STATUS_CHECKLIST_PENDING,
                startLatitude = latitude,
                startLongitude = longitude,
                startedAt = now,
                createdAt = now,
                updatedAt = now
            )

            val payload = checklist.toMap().toMutableMap()
            payload["materialsUsed"] = false
            payload["materialsDescription"] = ""

            db.collection(CHECKLISTS_COLLECTION)
                .document(orderId)
                .set(payload, SetOptions.merge())
                .await()

            Log.d(TAG, "Serviço iniciado com checklist para pedido: $orderId")
            Result.success(checklist)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar serviço com checklist: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveChecklistAnswers(
        orderId: String,
        answers: Map<String, Boolean?>,
        executionDescription: String,
        serviceDescription: List<String> = emptyList(),
        preExistingDamages: String = "",
        observations: String = "",
        problemResolution: String = "",
        materialsUsed: Boolean = false,
        materialsDescription: String = ""
    ): Result<Unit> {
        return try {
            val data = mutableMapOf<String, Any?>(
                "orderId" to orderId,
                "updatedAt" to Timestamp.now()
            )
            addProviderMetadata(data)
            answers.forEach { (key, value) ->
                if (value != null) {
                    data[key] = value
                }
            }
            data["serviceDescription"] = serviceDescription
            data["executionDescription"] = executionDescription
            data["preExistingDamages"] = preExistingDamages
            data["observations"] = observations
            data["materialsUsed"] = materialsUsed
            data["materialsDescription"] = if (materialsUsed) materialsDescription else ""
            if (problemResolution.isNotEmpty()) data["problemResolution"] = problemResolution
            data["status"] = OsChecklistData.STATUS_PHOTOS_PENDING

            db.collection(CHECKLISTS_COLLECTION)
                .document(orderId)
                .set(data, SetOptions.merge())
                .await()

            Log.d(TAG, "Respostas do checklist salvas: $orderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar respostas do checklist: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun savePhotos(
        orderId: String,
        category: String,
        photoUrls: List<String>,
        timestamps: List<Timestamp>
    ): Result<Unit> {
        return try {
            val field = when (category) {
                "before" -> "photosBefore"
                "during" -> "photosDuring"
                "after" -> "photosAfter"
                else -> return Result.failure(IllegalArgumentException("Categoria inválida: $category"))
            }
            val timeField = "photoTimestamps${category.replaceFirstChar { it.uppercase() }}"

            val data = mapOf<String, Any?>(
                "orderId" to orderId,
                field to photoUrls,
                timeField to timestamps,
                "status" to if (category == "after") {
                    OsChecklistData.STATUS_READY_FOR_COMPLETION_CODE
                } else {
                    OsChecklistData.STATUS_PHOTOS_PENDING
                },
                "updatedAt" to Timestamp.now()
            ).toMutableMap()
            addProviderMetadata(data)

            db.collection(CHECKLISTS_COLLECTION)
                .document(orderId)
                .set(data, SetOptions.merge())
                .await()

            Log.d(TAG, "Fotos salvas ($category) para pedido: $orderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar fotos: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveProviderSignature(
        orderId: String,
        signatureUrl: String,
        providerName: String
    ): Result<Unit> {
        return try {
            val data = mapOf<String, Any?>(
                "orderId" to orderId,
                "providerSignatureUrl" to signatureUrl,
                "providerSignatureName" to providerName,
                "providerSignedAt" to Timestamp.now(),
                "status" to OsChecklistData.STATUS_SIGNATURES_PENDING,
                "updatedAt" to Timestamp.now()
            ).toMutableMap()
            addProviderMetadata(data)

            db.collection(CHECKLISTS_COLLECTION)
                .document(orderId)
                .set(data, SetOptions.merge())
                .await()

            Log.d(TAG, "Assinatura do prestador salva: $orderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar assinatura do prestador: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveClientSignature(
        orderId: String,
        signatureUrl: String,
        clientName: String,
        clientDocument: String
    ): Result<Unit> {
        return try {
            val data = mapOf<String, Any?>(
                "orderId" to orderId,
                "clientSignatureUrl" to signatureUrl,
                "clientSignatureName" to clientName,
                "clientSignatureDocument" to clientDocument,
                "clientSignedAt" to Timestamp.now(),
                "status" to OsChecklistData.STATUS_COMPLETED,
                "completedAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            ).toMutableMap()
            addProviderMetadata(data)

            db.collection(CHECKLISTS_COLLECTION)
                .document(orderId)
                .set(data, SetOptions.merge())
                .await()

            Log.d(TAG, "Assinatura do cliente salva: $orderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar assinatura do cliente: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun markCompletedByClientCode(orderId: String): Result<Unit> {
        return try {
            val data = mapOf<String, Any?>(
                "orderId" to orderId,
                "status" to OsChecklistData.STATUS_COMPLETED,
                "completedAt" to Timestamp.now(),
                "completionMethod" to "client_code",
                "updatedAt" to Timestamp.now()
            ).toMutableMap()
            addProviderMetadata(data)

            db.collection(CHECKLISTS_COLLECTION)
                .document(orderId)
                .set(data, SetOptions.merge())
                .await()

            Log.d(TAG, "Checklist concluído por código do cliente: $orderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao concluir checklist por código: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteChecklist(orderId: String): Result<Unit> {
        return try {
            db.collection(CHECKLISTS_COLLECTION)
                .document(orderId)
                .delete()
                .await()

            Log.d(TAG, "Checklist excluído: $orderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao excluir checklist: ${e.message}")
            Result.failure(e)
        }
    }

    fun listenToChecklist(orderId: String, onChanged: (OsChecklistData?) -> Unit) {
        db.collection(CHECKLISTS_COLLECTION)
            .document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erro no listener do checklist: ${error.message}")
                    return@addSnapshotListener
                }
                val data = snapshot?.data
                val checklist = if (data != null) OsChecklistData.fromMap(data, orderId) else null
                onChanged(checklist)
            }
    }
}
