package com.example.loginapp.models.payment

import com.google.gson.annotations.SerializedName

/**
 * Modelo para requisição de pagamento PIX (API v5 Pagar.me)
 * Estrutura correta: Order contém payments[]
 */
data class PixPaymentRequest(
    @SerializedName("items")
    val items: List<PaymentItem>,
    
    @SerializedName("customer")
    val customer: CustomerInfo,
    
    @SerializedName("payments")
    val payments: List<PixPaymentMethod>,
    
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null,
    
    @SerializedName("closed")
    val closed: Boolean = true
)

/**
 * Método de pagamento PIX
 */
data class PixPaymentMethod(
    @SerializedName("payment_method")
    val paymentMethod: String = "pix",
    
    @SerializedName("pix")
    val pix: PixData
)

/**
 * Dados do PIX
 */
data class PixData(
    @SerializedName("expires_in")
    val expiresIn: Int = 3600, // Expira em 1 hora (3600 segundos)
    
    @SerializedName("additional_information")
    val additionalInformation: List<PixAdditionalInfo>? = null
)

/**
 * Informações adicionais do PIX
 */
data class PixAdditionalInfo(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("value")
    val value: String
)

/**
 * Resposta do pagamento PIX
 */
data class PixPaymentResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("status")
    val status: String, // pending, paid, canceled
    
    @SerializedName("amount")
    val amount: Long,
    
    @SerializedName("payment_method")
    val paymentMethod: String,
    
    @SerializedName("created_at")
    val createdAt: String,
    
    @SerializedName("expires_at")
    val expiresAt: String?,
    
    @SerializedName("last_transaction")
    val lastTransaction: PixTransactionInfo?,
    
    @SerializedName("charges")
    val charges: List<PixChargeInfo>?
)

/**
 * Informações da transação PIX
 */
data class PixTransactionInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("transaction_type")
    val transactionType: String,
    
    @SerializedName("qr_code")
    val qrCode: String?,
    
    @SerializedName("qr_code_url")
    val qrCodeUrl: String?,
    
    @SerializedName("expires_at")
    val expiresAt: String?
)

/**
 * Informações da cobrança PIX
 */
data class PixChargeInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("amount")
    val amount: Long,
    
    @SerializedName("last_transaction")
    val lastTransaction: PixTransactionInfo?
)



