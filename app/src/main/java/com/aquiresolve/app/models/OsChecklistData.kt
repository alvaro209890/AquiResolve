package com.aquiresolve.app.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class OsChecklistData(
    @PropertyName("orderId")
    val orderId: String = "",
    @PropertyName("providerId")
    val providerId: String? = null,
    @PropertyName("status")
    val status: String = STATUS_CHECKLIST_PENDING,

    // GPS no início
    @PropertyName("startLatitude")
    val startLatitude: Double? = null,
    @PropertyName("startLongitude")
    val startLongitude: Double? = null,
    @PropertyName("startedAt")
    val startedAt: Timestamp? = null,

    // Checklist Respostas
    @PropertyName("clientPresent")
    val clientPresent: Boolean? = null,
    @PropertyName("serviceMatches")
    val serviceMatches: Boolean? = null,
    @PropertyName("visibleDamage")
    val visibleDamage: Boolean? = null,
    @PropertyName("materialAvailable")
    val materialAvailable: Boolean? = null,
    @PropertyName("materialsUsed")
    val materialsUsed: Boolean? = null,
    @PropertyName("materialsDescription")
    val materialsDescription: String = "",
    @PropertyName("clientObservations")
    val clientObservations: Boolean? = null,
    @PropertyName("executedAsRequested")
    val executedAsRequested: Boolean? = null,
    @PropertyName("additionalService")
    val additionalService: Boolean? = null,
    @PropertyName("partsReplaced")
    val partsReplaced: Boolean? = null,
    @PropertyName("valueChanged")
    val valueChanged: Boolean? = null,
    @PropertyName("serviceCompleted")
    val serviceCompleted: Boolean? = null,
    @PropertyName("cleanAfterService")
    val cleanAfterService: Boolean? = null,

    // Tipos/categorias do serviço executado
    @PropertyName("serviceDescription")
    val serviceDescription: List<String> = emptyList(),

    // Avarias pré-existentes (campo texto do modelo)
    @PropertyName("preExistingDamages")
    val preExistingDamages: String = "",

    // Resolução do problema: "resolved" | "return_needed" | "not_resolved"
    @PropertyName("problemResolution")
    val problemResolution: String = "",

    // Declaração de concordância assinada pelo prestador
    @PropertyName("declarationAccepted")
    val declarationAccepted: Boolean? = null,

    // Descrição detalhada
    @PropertyName("executionDescription")
    val executionDescription: String = "",

    // Observações gerais do atendimento
    @PropertyName("observations")
    val observations: String = "",

    // Fotos
    @PropertyName("photosBefore")
    val photosBefore: List<String> = emptyList(),
    @PropertyName("photosDuring")
    val photosDuring: List<String> = emptyList(),
    @PropertyName("photosAfter")
    val photosAfter: List<String> = emptyList(),
    @PropertyName("photoTimestampsBefore")
    val photoTimestampsBefore: List<Timestamp> = emptyList(),
    @PropertyName("photoTimestampsDuring")
    val photoTimestampsDuring: List<Timestamp> = emptyList(),
    @PropertyName("photoTimestampsAfter")
    val photoTimestampsAfter: List<Timestamp> = emptyList(),

    // Campos legados de assinatura. Mantidos para leitura de OS antigas; o fluxo novo usa código do cliente.
    @PropertyName("providerSignatureUrl")
    val providerSignatureUrl: String? = null,
    @PropertyName("providerSignatureName")
    val providerSignatureName: String? = null,
    @PropertyName("providerSignedAt")
    val providerSignedAt: Timestamp? = null,

    // Assinatura do cliente
    @PropertyName("clientSignatureUrl")
    val clientSignatureUrl: String? = null,
    @PropertyName("clientSignatureName")
    val clientSignatureName: String? = null,
    @PropertyName("clientSignatureDocument")
    val clientSignatureDocument: String? = null,
    @PropertyName("clientSignedAt")
    val clientSignedAt: Timestamp? = null,

    // Metadados
    @PropertyName("completedAt")
    val completedAt: Timestamp? = null,
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),
    @PropertyName("updatedAt")
    val updatedAt: Timestamp = Timestamp.now()
) {
    companion object {
        const val STATUS_CHECKLIST_PENDING = "checklist_pending"
        const val STATUS_PHOTOS_PENDING = "photos_pending"
        const val STATUS_READY_FOR_COMPLETION_CODE = "ready_for_completion_code"
        const val STATUS_SIGNATURES_PENDING = "signatures_pending"
        const val STATUS_COMPLETED = "completed"

        fun fromMap(data: Map<String, Any?>, orderId: String): OsChecklistData {
            return OsChecklistData(
                orderId = orderId,
                providerId = data["providerId"] as? String,
                status = data["status"] as? String ?: STATUS_CHECKLIST_PENDING,
                startLatitude = data["startLatitude"] as? Double,
                startLongitude = data["startLongitude"] as? Double,
                startedAt = data["startedAt"] as? Timestamp,
                clientPresent = data["clientPresent"] as? Boolean,
                serviceMatches = data["serviceMatches"] as? Boolean,
                visibleDamage = data["visibleDamage"] as? Boolean,
                materialAvailable = data["materialAvailable"] as? Boolean,
                materialsUsed = data["materialsUsed"] as? Boolean,
                materialsDescription = data["materialsDescription"] as? String ?: "",
                clientObservations = data["clientObservations"] as? Boolean,
                executedAsRequested = data["executedAsRequested"] as? Boolean,
                additionalService = data["additionalService"] as? Boolean,
                partsReplaced = data["partsReplaced"] as? Boolean,
                valueChanged = data["valueChanged"] as? Boolean,
                serviceCompleted = data["serviceCompleted"] as? Boolean,
                cleanAfterService = data["cleanAfterService"] as? Boolean,
                serviceDescription = (data["serviceDescription"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                preExistingDamages = data["preExistingDamages"] as? String ?: "",
                problemResolution = data["problemResolution"] as? String ?: "",
                declarationAccepted = data["declarationAccepted"] as? Boolean,
                executionDescription = data["executionDescription"] as? String ?: "",
                observations = data["observations"] as? String ?: "",
                photosBefore = (data["photosBefore"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                photosDuring = (data["photosDuring"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                photosAfter = (data["photosAfter"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                photoTimestampsBefore = (data["photoTimestampsBefore"] as? List<*>)?.filterIsInstance<Timestamp>() ?: emptyList(),
                photoTimestampsDuring = (data["photoTimestampsDuring"] as? List<*>)?.filterIsInstance<Timestamp>() ?: emptyList(),
                photoTimestampsAfter = (data["photoTimestampsAfter"] as? List<*>)?.filterIsInstance<Timestamp>() ?: emptyList(),
                providerSignatureUrl = data["providerSignatureUrl"] as? String,
                providerSignatureName = data["providerSignatureName"] as? String,
                providerSignedAt = data["providerSignedAt"] as? Timestamp,
                clientSignatureUrl = data["clientSignatureUrl"] as? String,
                clientSignatureName = data["clientSignatureName"] as? String,
                clientSignatureDocument = data["clientSignatureDocument"] as? String,
                clientSignedAt = data["clientSignedAt"] as? Timestamp,
                completedAt = data["completedAt"] as? Timestamp,
                createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now(),
                updatedAt = data["updatedAt"] as? Timestamp ?: Timestamp.now()
            )
        }
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "orderId" to orderId,
            "providerId" to providerId,
            "status" to status,
            "startLatitude" to startLatitude,
            "startLongitude" to startLongitude,
            "startedAt" to startedAt,
            "clientPresent" to clientPresent,
            "serviceMatches" to serviceMatches,
            "visibleDamage" to visibleDamage,
            "materialAvailable" to materialAvailable,
            "materialsUsed" to materialsUsed,
            "materialsDescription" to materialsDescription,
            "clientObservations" to clientObservations,
            "executedAsRequested" to executedAsRequested,
            "additionalService" to additionalService,
            "partsReplaced" to partsReplaced,
            "valueChanged" to valueChanged,
            "serviceCompleted" to serviceCompleted,
            "cleanAfterService" to cleanAfterService,
            "serviceDescription" to serviceDescription,
            "preExistingDamages" to preExistingDamages,
            "problemResolution" to problemResolution,
            "declarationAccepted" to declarationAccepted,
            "executionDescription" to executionDescription,
            "observations" to observations,
            "photosBefore" to photosBefore,
            "photosDuring" to photosDuring,
            "photosAfter" to photosAfter,
            "photoTimestampsBefore" to photoTimestampsBefore,
            "photoTimestampsDuring" to photoTimestampsDuring,
            "photoTimestampsAfter" to photoTimestampsAfter,
            "providerSignatureUrl" to providerSignatureUrl,
            "providerSignatureName" to providerSignatureName,
            "providerSignedAt" to providerSignedAt,
            "clientSignatureUrl" to clientSignatureUrl,
            "clientSignatureName" to clientSignatureName,
            "clientSignatureDocument" to clientSignatureDocument,
            "clientSignedAt" to clientSignedAt,
            "completedAt" to completedAt,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }

    val checklistStep1Complete: Boolean
        get() = clientPresent != null && serviceMatches != null && visibleDamage != null &&
                materialAvailable != null && clientObservations != null

    val checklistStep2Complete: Boolean
        get() = executedAsRequested != null && additionalService != null &&
                partsReplaced != null && valueChanged != null && serviceCompleted != null &&
                cleanAfterService != null && serviceDescription.isNotEmpty() &&
                problemResolution.isNotEmpty() &&
                declarationAccepted == true

    val checklistComplete: Boolean
        get() = checklistStep1Complete && checklistStep2Complete && executionDescription.isNotBlank()

    val photosComplete: Boolean
        get() = photosBefore.isNotEmpty() && photosAfter.isNotEmpty()

    val providerSignatureComplete: Boolean
        get() = providerSignatureUrl != null && providerSignatureName != null

    val clientSignatureComplete: Boolean
        get() = clientSignatureUrl != null && clientSignatureName != null

    val isComplete: Boolean
        get() = checklistComplete && photosComplete && status == STATUS_COMPLETED

    val checklistCompletable: Boolean
        get() = clientPresent != null && serviceMatches != null
}
