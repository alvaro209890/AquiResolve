package com.aquiresolve.app.models

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

class OsChecklistDataTest {

    @Test
    fun `toMap and fromMap roundtrip preserves all fields`() {
        val original = OsChecklistData(
            orderId = "test_order_123",
            status = OsChecklistData.STATUS_CHECKLIST_PENDING,
            startLatitude = -23.5505,
            startLongitude = -46.6333,
            startedAt = Timestamp.now(),
            clientPresent = true,
            serviceMatches = true,
            visibleDamage = false,
            materialAvailable = true,
            clientObservations = false,
            executedAsRequested = true,
            additionalService = true,
            partsReplaced = false,
            valueChanged = false,
            serviceCompleted = true,
            cleanAfterService = true,
            serviceDescription = listOf("Elétrico", "Encanador"),
            problemResolution = "resolved",
            declarationAccepted = true,
            executionDescription = "Troca do sifão da pia e substituição de conexões com vazamento.",
            observations = "Cliente acompanhou a conclusão.",
            photosBefore = listOf("url_before_1", "url_before_2"),
            photosDuring = listOf("url_during_1"),
            photosAfter = listOf("url_after_1", "url_after_2", "url_after_3"),
            providerSignatureUrl = "url_provider_sig",
            providerSignatureName = "João Prestador",
            providerSignedAt = Timestamp.now(),
            clientSignatureUrl = "url_client_sig",
            clientSignatureName = "Maria Cliente",
            clientSignatureDocument = "123.456.789-00",
            clientSignedAt = Timestamp.now(),
            completedAt = Timestamp.now(),
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        val map = original.toMap()
        val restored = OsChecklistData.fromMap(map, "test_order_123")

        assertEquals(original.orderId, restored.orderId)
        assertEquals(original.status, restored.status)
        assertEquals(original.startLatitude, restored.startLatitude)
        assertEquals(original.startLongitude, restored.startLongitude)
        assertEquals(original.clientPresent, restored.clientPresent)
        assertEquals(original.serviceMatches, restored.serviceMatches)
        assertEquals(original.visibleDamage, restored.visibleDamage)
        assertEquals(original.materialAvailable, restored.materialAvailable)
        assertEquals(original.clientObservations, restored.clientObservations)
        assertEquals(original.executedAsRequested, restored.executedAsRequested)
        assertEquals(original.additionalService, restored.additionalService)
        assertEquals(original.partsReplaced, restored.partsReplaced)
        assertEquals(original.valueChanged, restored.valueChanged)
        assertEquals(original.serviceCompleted, restored.serviceCompleted)
        assertEquals(original.cleanAfterService, restored.cleanAfterService)
        assertEquals(original.serviceDescription, restored.serviceDescription)
        assertEquals(original.problemResolution, restored.problemResolution)
        assertEquals(original.declarationAccepted, restored.declarationAccepted)
        assertEquals(original.executionDescription, restored.executionDescription)
        assertEquals(original.observations, restored.observations)
        assertEquals(original.photosBefore, restored.photosBefore)
        assertEquals(original.photosDuring, restored.photosDuring)
        assertEquals(original.photosAfter, restored.photosAfter)
        assertEquals(original.providerSignatureUrl, restored.providerSignatureUrl)
        assertEquals(original.providerSignatureName, restored.providerSignatureName)
        assertEquals(original.clientSignatureUrl, restored.clientSignatureUrl)
        assertEquals(original.clientSignatureName, restored.clientSignatureName)
        assertEquals(original.clientSignatureDocument, restored.clientSignatureDocument)
    }

    @Test
    fun `checklistComplete returns false when description is empty`() {
        val checklist = OsChecklistData(
            orderId = "test",
            status = OsChecklistData.STATUS_COMPLETED,
            clientPresent = true,
            serviceMatches = true,
            visibleDamage = false,
            materialAvailable = true,
            clientObservations = false,
            executedAsRequested = true,
            additionalService = false,
            partsReplaced = false,
            valueChanged = false,
            serviceCompleted = true,
            cleanAfterService = true,
            serviceDescription = listOf("Elétrico"),
            problemResolution = "resolved",
            declarationAccepted = true,
            executionDescription = ""
        )
        assertFalse("Checklist sem descrição deve ser incompleto", checklist.checklistComplete)
    }

    @Test
    fun `checklistComplete returns true when all fields are filled`() {
        val checklist = OsChecklistData(
            orderId = "test",
            status = OsChecklistData.STATUS_COMPLETED,
            clientPresent = true,
            serviceMatches = true,
            visibleDamage = false,
            materialAvailable = true,
            clientObservations = false,
            executedAsRequested = true,
            additionalService = false,
            partsReplaced = false,
            valueChanged = false,
            serviceCompleted = true,
            cleanAfterService = true,
            serviceDescription = listOf("Elétrico"),
            problemResolution = "resolved",
            declarationAccepted = true,
            executionDescription = "Serviço realizado conforme solicitado."
        )
        assertTrue("Checklist completo deve retornar true", checklist.checklistComplete)
    }

    @Test
    fun `checklistComplete returns false when service description is empty`() {
        val checklist = OsChecklistData(
            orderId = "test",
            clientPresent = true,
            serviceMatches = true,
            visibleDamage = false,
            materialAvailable = true,
            clientObservations = false,
            executedAsRequested = true,
            additionalService = false,
            partsReplaced = false,
            valueChanged = false,
            serviceCompleted = true,
            cleanAfterService = true,
            problemResolution = "resolved",
            declarationAccepted = true,
            executionDescription = "Serviço realizado conforme solicitado."
        )
        assertFalse("Checklist sem descrição de serviço deve ser incompleto", checklist.checklistComplete)
    }

    @Test
    fun `photosComplete returns false when categories are empty`() {
        val checklist = OsChecklistData(orderId = "test")
        assertFalse(checklist.photosComplete)
    }

    @Test
    fun `photosComplete returns true when all categories have photos`() {
        val checklist = OsChecklistData(
            orderId = "test",
            photosBefore = listOf("url1"),
            photosDuring = listOf("url2"),
            photosAfter = listOf("url3")
        )
        assertTrue(checklist.photosComplete)
    }

    @Test
    fun `signatures are incomplete when null`() {
        val checklist = OsChecklistData(orderId = "test")
        assertFalse(checklist.providerSignatureComplete)
        assertFalse(checklist.clientSignatureComplete)
    }

    @Test
    fun `signatures are complete when all fields present`() {
        val checklist = OsChecklistData(
            orderId = "test",
            providerSignatureUrl = "url",
            providerSignatureName = "Provider",
            clientSignatureUrl = "url",
            clientSignatureName = "Client",
            clientSignatureDocument = "123"
        )
        assertTrue(checklist.providerSignatureComplete)
        assertTrue(checklist.clientSignatureComplete)
    }

    @Test
    fun `isComplete returns true only when everything is filled`() {
        val checklist = OsChecklistData(
            orderId = "test",
            status = OsChecklistData.STATUS_COMPLETED,
            clientPresent = true,
            serviceMatches = true,
            visibleDamage = false,
            materialAvailable = true,
            clientObservations = false,
            executedAsRequested = true,
            additionalService = false,
            partsReplaced = false,
            valueChanged = false,
            serviceCompleted = true,
            cleanAfterService = true,
            serviceDescription = listOf("Elétrico"),
            problemResolution = "resolved",
            declarationAccepted = true,
            executionDescription = "Done.",
            observations = "Sem observações adicionais.",
            photosBefore = listOf("a"),
            photosAfter = listOf("c")
        )
        assertTrue(checklist.isComplete)
    }

    @Test
    fun `isComplete returns false when photos are missing`() {
        val checklist = OsChecklistData(
            orderId = "test",
            status = OsChecklistData.STATUS_COMPLETED,
            clientPresent = true,
            serviceMatches = true,
            visibleDamage = false,
            materialAvailable = true,
            clientObservations = false,
            executedAsRequested = true,
            additionalService = false,
            partsReplaced = false,
            valueChanged = false,
            serviceCompleted = true,
            cleanAfterService = true,
            serviceDescription = listOf("Elétrico"),
            problemResolution = "resolved",
            declarationAccepted = true,
            executionDescription = "Done."
        )
        assertFalse(checklist.isComplete)
    }

    @Test
    fun `empty map fromMap creates default checklist`() {
        val checklist = OsChecklistData.fromMap(emptyMap(), "order_empty")
        assertEquals("order_empty", checklist.orderId)
        assertEquals(OsChecklistData.STATUS_CHECKLIST_PENDING, checklist.status)
        assertEquals("", checklist.executionDescription)
        assertEquals("", checklist.observations)
        assertEquals(emptyList<String>(), checklist.serviceDescription)
        assertNull(checklist.clientPresent)
    }

    @Test
    fun `toMap contains all expected keys`() {
        val checklist = OsChecklistData(orderId = "map_keys_test")
        val map = checklist.toMap()
        assertTrue(map.containsKey("orderId"))
        assertTrue(map.containsKey("status"))
        assertTrue(map.containsKey("cleanAfterService"))
        assertTrue(map.containsKey("materialsUsed"))
        assertTrue(map.containsKey("materialsDescription"))
        assertTrue(map.containsKey("serviceDescription"))
        assertTrue(map.containsKey("problemResolution"))
        assertTrue(map.containsKey("declarationAccepted"))
        assertTrue(map.containsKey("executionDescription"))
        assertTrue(map.containsKey("observations"))
        assertTrue(map.containsKey("photosBefore"))
        assertTrue(map.containsKey("photosDuring"))
        assertTrue(map.containsKey("photosAfter"))
        assertTrue(map.containsKey("createdAt"))
        assertTrue(map.containsKey("updatedAt"))
    }

    @Test
    fun `status constants are correct`() {
        assertEquals("checklist_pending", OsChecklistData.STATUS_CHECKLIST_PENDING)
        assertEquals("photos_pending", OsChecklistData.STATUS_PHOTOS_PENDING)
        assertEquals("ready_for_completion_code", OsChecklistData.STATUS_READY_FOR_COMPLETION_CODE)
        assertEquals("signatures_pending", OsChecklistData.STATUS_SIGNATURES_PENDING)
        assertEquals("completed", OsChecklistData.STATUS_COMPLETED)
    }
}
