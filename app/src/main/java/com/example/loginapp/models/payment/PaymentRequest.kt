package com.example.loginapp.models.payment

import com.google.gson.annotations.SerializedName

/**
 * Modelo para requisição de pagamento à Pagar.me (API v5)
 * Estrutura correta: Order contém payments[]
 */
data class PaymentRequest(
    @SerializedName("items")
    val items: List<PaymentItem>,
    
    @SerializedName("customer")
    val customer: CustomerInfo,
    
    @SerializedName("payments")
    val payments: List<CreditCardPaymentMethod>,
    
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null,
    
    @SerializedName("closed")
    val closed: Boolean = true
)

/**
 * Método de pagamento com cartão
 */
data class CreditCardPaymentMethod(
    @SerializedName("payment_method")
    val paymentMethod: String = "credit_card",
    
    @SerializedName("credit_card")
    val creditCard: CreditCardData
)

/**
 * Dados do cartão de crédito para pagamento
 */
data class CreditCardData(
    @SerializedName("card")
    val card: CardData,
    
    @SerializedName("billing_address")
    val billingAddress: BillingAddress
)

/**
 * Item do pedido
 */
data class PaymentItem(
    @SerializedName("amount")
    val amount: Long,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("quantity")
    val quantity: Int,
    
    @SerializedName("code")
    val code: String? = null
)

/**
 * Resposta de pagamento da Pagar.me
 */
data class PaymentResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("status")
    val status: String, // paid, pending, failed, refunded
    
    @SerializedName("amount")
    val amount: Long,
    
    @SerializedName("paid_amount")
    val paidAmount: Long? = null,
    
    @SerializedName("payment_method")
    val paymentMethod: String,
    
    @SerializedName("created_at")
    val createdAt: String,
    
    @SerializedName("updated_at")
    val updatedAt: String,
    
    @SerializedName("last_transaction")
    val lastTransaction: TransactionInfo? = null,
    
    @SerializedName("gateway_response")
    val gatewayResponse: GatewayResponse? = null,
    
    @SerializedName("customer")
    val customer: CustomerResponse? = null
)

/**
 * Informações da transação
 */
data class TransactionInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("gateway_id")
    val gatewayId: String? = null,
    
    @SerializedName("gateway_response_code")
    val gatewayResponseCode: String? = null,
    
    @SerializedName("gateway_response_message")
    val gatewayResponseMessage: String? = null
)

/**
 * Resposta do gateway de pagamento
 */
data class GatewayResponse(
    @SerializedName("code")
    val code: String,
    
    @SerializedName("errors")
    val errors: List<PaymentError>? = null
)

/**
 * Erro de pagamento
 */
data class PaymentError(
    @SerializedName("message")
    val message: String,
    
    @SerializedName("parameter_name")
    val parameterName: String? = null
)

/**
 * Resposta de informações do cliente
 */
data class CustomerResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("email")
    val email: String
)





