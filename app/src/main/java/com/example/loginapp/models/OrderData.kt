package com.example.loginapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class OrderData(
    @PropertyName("id")
    val id: String = "",
    @PropertyName("protocol")
    val protocol: String = "",
    @PropertyName("clientId")
    val clientId: String = "",
    @PropertyName("clientName")
    val clientName: String = "",
    @PropertyName("clientEmail")
    val clientEmail: String = "",
    @PropertyName("clientPhone")
    val clientPhone: String = "",
    
    // Detalhes do Serviço
    @PropertyName("serviceType")
    val serviceType: String = "",
    @PropertyName("serviceName")
    val serviceName: String = "",
    @PropertyName("description")
    val description: String = "",
    @PropertyName("priority")
    val priority: String = "normal",
    
    // Localização
    @PropertyName("address")
    val address: String = "",
    @PropertyName("city")
    val city: String = "",
    @PropertyName("state")
    val state: String = "",
    @PropertyName("zipCode")
    val zipCode: String = "",
    @PropertyName("coordinates")
    val coordinates: GeoPoint? = null,
    
    // Agendamento
    @PropertyName("scheduledDate")
    val scheduledDate: Timestamp? = null,
    @PropertyName("preferredTimeSlot")
    val preferredTimeSlot: String = "afternoon",
    
    // Status e Atribuição
    @PropertyName("status")
    val status: String = "distributing", // Novo status inicial
    @PropertyName("assignedProvider")
    val assignedProvider: String? = null,
    @PropertyName("assignedProviderName")
    val assignedProviderName: String? = null, // Nome do prestador atribuído
    @PropertyName("assignedAt")
    val assignedAt: Timestamp? = null,
    // Execução
    @PropertyName("startedAt")
    val startedAt: Timestamp? = null,
    @PropertyName("clientCompletionConfirmed")
    val clientCompletionConfirmed: Boolean = false,
    @PropertyName("providerCompletionConfirmed")
    val providerCompletionConfirmed: Boolean = false,
    @PropertyName("completedAt")
    val completedAt: Timestamp? = null,
    @PropertyName("distributionStartedAt")
    val distributionStartedAt: Timestamp = Timestamp.now(), // Quando começou a distribuição
    
    // Códigos de Verificação para Finalização
    @PropertyName("clientVerificationCode")
    val clientVerificationCode: String? = null, // Código do cliente (6 dígitos)
    @PropertyName("providerVerificationCode")
    val providerVerificationCode: String? = null, // Código do prestador (6 dígitos)
    @PropertyName("verificationCodesGeneratedAt")
    val verificationCodesGeneratedAt: Timestamp? = null, // Quando os códigos foram gerados
    
    // Cancelamento
    @PropertyName("cancelledAt")
    val cancelledAt: Timestamp? = null,
    @PropertyName("cancelledBy")
    val cancelledBy: String? = null, // "client" ou "provider"
    @PropertyName("cancellationReason")
    val cancellationReason: String? = null,
    @PropertyName("refundStatus")
    val refundStatus: String? = null, // "pending", "processing", "completed", "failed"
    @PropertyName("refundRequestedAt")
    val refundRequestedAt: Timestamp? = null,
    
    // Preços
    @PropertyName("estimatedPrice")
    val estimatedPrice: Double = 0.0,
    @PropertyName("finalPrice")
    val finalPrice: Double? = null,
    @PropertyName("providerCommission")
    val providerCommission: Double = 0.0, // 50% do valor do pedido para o prestador

    // Pagamento
    @PropertyName("paymentStatus")
    val paymentStatus: String? = null,
    @PropertyName("transactionId")
    val transactionId: String? = null,
    
    // Metadados
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),
    @PropertyName("updatedAt")
    val updatedAt: Timestamp = Timestamp.now(),
    @PropertyName("confirmedAt")
    val confirmedAt: Timestamp? = null, // Timestamp de quando o pedido foi confirmado após pagamento
    @PropertyName("adminNotes")
    val adminNotes: String = "",
    
    // Avaliação
    @PropertyName("rating")
    val rating: Int? = null,
    @PropertyName("review")
    val review: String? = null,
    @PropertyName("reviewedAt")
    val reviewedAt: Timestamp? = null,
    
    // Campos adicionais para compatibilidade
    @PropertyName("complement")
    val complement: String? = null,
    @PropertyName("images")
    val images: List<String> = emptyList()
) {
    companion object {
        const val STATUS_DISTRIBUTING = "distributing" // Em distribuição (status inicial)
        const val STATUS_PENDING = "pending" // Aguardando resposta do prestador
        const val STATUS_ASSIGNED = "assigned" // Atribuído a um prestador
        const val STATUS_IN_PROGRESS = "in_progress" // Em andamento
        const val STATUS_COMPLETED = "completed" // Concluído
        const val STATUS_CANCELLED = "cancelled" // Cancelado
        const val STATUS_EXPIRED = "expired" // Expirado
        
        const val PRIORITY_LOW = "low"
        const val PRIORITY_NORMAL = "normal"
        const val PRIORITY_HIGH = "high"
        const val PRIORITY_URGENT = "urgent"
        
        const val TIME_SLOT_MORNING = "morning"
        const val TIME_SLOT_AFTERNOON = "afternoon"
        const val TIME_SLOT_EVENING = "evening"
    }
}

/**
 * Status de um pedido (mantido para compatibilidade)
 */
enum class OrderStatus {
    DISTRIBUTING,   // Em distribuição (status inicial)
    PENDING,        // Aguardando resposta do prestador
    QUOTES_RECEIVED, // Cotações recebidas (serviços complexos)
    ASSIGNED,       // Atribuído a um prestador
    IN_PROGRESS,    // Em andamento
    COMPLETED,      // Concluído
    CANCELLED,      // Cancelado
    EXPIRED         // Expirado
}

/**
 * Dados de uma cotação de prestador
 */
data class QuoteData(
    val id: String,
    val orderId: String,
    val providerId: String,
    val providerName: String,
    val providerRating: Double,
    val providerReviews: Int = 0,
    val price: Double,
    val description: String,
    val estimatedTime: String, // Ex: "2-3 horas", "1 dia"
    val availableDate: String = "",
    val distance: String = "",
    val includesMaterial: Boolean = false,
    val warranty: String = "",
    val createdAt: Date = Date(),
    val status: QuoteStatus = QuoteStatus.PENDING
)

/**
 * Status de uma cotação
 */
enum class QuoteStatus {
    PENDING,    // Aguardando resposta do cliente
    ACCEPTED,   // Aceita pelo cliente
    REJECTED,   // Rejeitada pelo cliente
    EXPIRED     // Expirada
}

/**
 * Dados para criação de um novo pedido
 */
data class CreateOrderRequest(
    val serviceType: String,
    val serviceNiche: String,
    val description: String,
    val images: List<String> = emptyList(),
    val cep: String,
    val address: String,
    val complement: String? = null,
    val preferredDate: Date? = null,
    val preferredTime: String? = null
)

/**
 * Resultado de criação de pedido
 */
sealed class OrderResult {
    object Success : OrderResult()
    data class Error(val message: String) : OrderResult()
} 