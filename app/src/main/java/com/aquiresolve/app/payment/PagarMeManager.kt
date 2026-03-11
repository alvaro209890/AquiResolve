package com.aquiresolve.app.payment

import android.content.Context
import android.util.Log
import com.aquiresolve.app.BuildConfig
import com.aquiresolve.app.api.PagarMeApiService
import com.aquiresolve.app.models.payment.*
import com.aquiresolve.app.utils.awaitCurrentUser
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Manager para integração com o backend de pagamentos
 */
class PagarMeManager(private val context: Context) {

    companion object {
        private const val TAG = "PagarMeManager"
        private const val BASE_URL = BuildConfig.PAYMENTS_API_BASE_URL
    }

    private val apiService: PagarMeApiService
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    init {
        // Configurar cliente HTTP
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        // Configurar Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        apiService = retrofit.create(PagarMeApiService::class.java)
    }

    private fun extractApiErrorMessage(responseBody: okhttp3.ResponseBody?): String? {
        val rawBody = responseBody?.string()?.trim().orEmpty()
        if (rawBody.isBlank()) {
            return null
        }

        return try {
            val json = JSONObject(rawBody)
            json.optString("message")
                .trim()
                .ifBlank {
                    json.optJSONArray("errors")
                        ?.optJSONObject(0)
                        ?.optString("message")
                        ?.trim()
                        .orEmpty()
                }
                .ifBlank { null }
        } catch (_: Exception) {
            rawBody
        }
    }
    
    /**
     * Processar pagamento com cartão de crédito
     */
    suspend fun processPayment(
        cardData: CardData,
        customerInfo: CustomerInfo,
        billingAddress: BillingAddress,
        amount: Double,
        description: String,
        orderId: String
    ): PaymentResult {
        return try {
            Log.d(TAG, "Iniciando processamento de pagamento")
            
            // Converter valor para centavos
            val amountInCents = (amount * 100).toLong()
            
            // Criar item do pedido
            val item = PaymentItem(
                amount = amountInCents,
                description = description,
                quantity = 1,
                code = orderId
            )
            
            // Criar dados de cobrança
            val billing = com.aquiresolve.app.models.payment.BillingAddress(
                line1 = billingAddress.line1,
                line2 = billingAddress.line2,
                zipCode = billingAddress.zipCode,
                city = billingAddress.city,
                state = billingAddress.state,
                country = billingAddress.country
            )
            
            // Criar dados do cartão para pagamento (estrutura v5)
            val creditCardData = com.aquiresolve.app.models.payment.CreditCardData(
                card = cardData,
                billingAddress = billing
            )
            
            // Criar método de pagamento (estrutura v5)
            val creditCardPaymentMethod = com.aquiresolve.app.models.payment.CreditCardPaymentMethod(
                paymentMethod = "credit_card",
                creditCard = creditCardData
            )
            
            // Criar metadata com informações adicionais
            val metadata = mapOf(
                "order_id" to orderId,
                "platform" to "android"
            )
            
            // Criar requisição de pagamento (estrutura correta v5)
            val paymentRequest = com.aquiresolve.app.models.payment.PaymentRequest(
                items = listOf(item),
                customer = customerInfo,
                payments = listOf(creditCardPaymentMethod), // ✅ Campo obrigatório!
                metadata = metadata,
                closed = true
            )

            val authToken = getAuthorizationHeader()
            Log.d(TAG, "Enviando requisição para backend de pagamentos...")

            val response = apiService.createOrder(authToken, paymentRequest)

            if (response.isSuccessful) {
                val paymentResponse = response.body()

                if (paymentResponse != null) {
                    when (paymentResponse.status) {
                        "paid" -> {
                            Log.d(TAG, "Pagamento aprovado")
                            PaymentResult.Success(
                                transactionId = paymentResponse.id,
                                status = paymentResponse.status,
                                message = "Pagamento aprovado com sucesso!"
                            )
                        }
                        "pending" -> {
                            Log.d(TAG, "Pagamento pendente de processamento")
                            PaymentResult.Pending(
                                transactionId = paymentResponse.id,
                                message = "Pagamento em processamento. Aguarde confirmação."
                            )
                        }
                        else -> {
                            Log.w(TAG, "Pagamento recusado")
                            val errorMessage = paymentResponse.gatewayResponse?.errors?.firstOrNull()?.message
                                ?: "Pagamento não foi aprovado"
                            PaymentResult.Error(errorMessage)
                        }
                    }
                } else {
                    Log.e(TAG, "Resposta vazia da API")
                    PaymentResult.Error("Resposta inválida do servidor de pagamento")
                }
            } else {
                Log.e(TAG, "Erro na requisição de pagamento: ${response.code()}")
                
                val errorMessage = when (response.code()) {
                    400 -> "Dados do pagamento inválidos"
                    401 -> "Erro de autenticação com gateway de pagamento"
                    402 -> "Pagamento recusado"
                    422 -> "Dados do cartão inválidos"
                    500 -> "Erro no servidor de pagamento"
                    else -> "Erro ao processar pagamento (${response.code()})"
                }
                
                PaymentResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao processar pagamento", e)
            PaymentResult.Error("Erro de conexão: ${e.localizedMessage}")
        }
    }
    
    /**
     * Processar pagamento com PIX
     */
    suspend fun processPixPayment(
        customerInfo: com.aquiresolve.app.models.payment.CustomerInfo,
        amount: Double,
        description: String,
        orderId: String
    ): PixPaymentResult {
        return try {
            Log.d(TAG, "Iniciando processamento de pagamento PIX")
            
            // Converter valor para centavos
            val amountInCents = (amount * 100).toLong()
            if (amountInCents <= 0L) {
                return PixPaymentResult.Error("Valor inválido para gerar PIX")
            }
            
            // Criar item do pedido
            val item = com.aquiresolve.app.models.payment.PaymentItem(
                amount = amountInCents,
                description = description,
                quantity = 1,
                code = orderId
            )
            
            // Criar dados PIX
            val pixData = com.aquiresolve.app.models.payment.PixData(
                expiresIn = 3600, // 1 hora
                additionalInformation = listOf(
                    com.aquiresolve.app.models.payment.PixAdditionalInfo(
                        name = "Pedido",
                        value = orderId
                    )
                )
            )
            
            // Criar método de pagamento PIX (estrutura v5)
            val pixPaymentMethod = com.aquiresolve.app.models.payment.PixPaymentMethod(
                paymentMethod = "pix",
                pix = pixData
            )
            
            // Criar metadata
            val metadata = mapOf(
                "order_id" to orderId,
                "platform" to "android"
            )
            
            // Criar requisição PIX (estrutura correta v5)
            val pixRequest = com.aquiresolve.app.models.payment.PixPaymentRequest(
                items = listOf(item),
                customer = customerInfo,
                payments = listOf(pixPaymentMethod), // ✅ Campo obrigatório!
                metadata = metadata,
                closed = true
            )

            val authToken = getAuthorizationHeader()
            Log.d(TAG, "Enviando requisição PIX")

            val response = apiService.createPixOrder(authToken, pixRequest)

            if (response.isSuccessful) {
                val pixResponse = response.body()

                if (pixResponse != null) {
                    // Extrair QR Code da resposta
                    val qrCode = pixResponse.charges?.firstOrNull()?.lastTransaction?.qrCode
                        ?: pixResponse.lastTransaction?.qrCode
                    
                    val qrCodeUrl = pixResponse.charges?.firstOrNull()?.lastTransaction?.qrCodeUrl
                        ?: pixResponse.lastTransaction?.qrCodeUrl
                    
                    when {
                        pixResponse.status == "pending" && qrCode != null -> {
                            Log.d(TAG, "PIX gerado com sucesso")
                            PixPaymentResult.Success(
                                transactionId = pixResponse.id,
                                qrCode = qrCode,
                                qrCodeUrl = qrCodeUrl,
                                expiresAt = pixResponse.expiresAt
                            )
                        }
                        pixResponse.status == "paid" -> {
                            Log.d(TAG, "PIX já pago")
                            PixPaymentResult.Paid(
                                transactionId = pixResponse.id,
                                message = "PIX pago com sucesso!"
                            )
                        }
                        else -> {
                            Log.w(TAG, "Erro ao gerar PIX")
                            PixPaymentResult.Error("Não foi possível gerar o código PIX")
                        }
                    }
                } else {
                    Log.e(TAG, "Resposta vazia da API")
                    PixPaymentResult.Error("Resposta inválida do servidor de pagamento")
                }
            } else {
                Log.e(TAG, "Erro na requisição PIX: ${response.code()}")
                
                val apiErrorMessage = extractApiErrorMessage(response.errorBody())
                val errorMessage = apiErrorMessage ?: when (response.code()) {
                    400 -> "Dados do pagamento PIX inválidos"
                    401 -> "Erro de autenticação com gateway de pagamento"
                    422 -> "Não foi possível validar os dados do pagamento PIX"
                    500 -> "Erro no servidor de pagamento"
                    else -> "Erro ao gerar PIX (${response.code()})"
                }
                
                PixPaymentResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao processar PIX", e)
            PixPaymentResult.Error("Erro de conexão: ${e.localizedMessage}")
        }
    }
    
    /**
     * Consultar status de pagamento PIX
     */
    suspend fun checkPixPaymentStatus(transactionId: String): PixPaymentResult {
        return try {
            val authToken = getAuthorizationHeader()
            Log.d(TAG, "Consultando status do pagamento PIX")

            val response = apiService.getOrderStatus(authToken, transactionId)

            if (response.isSuccessful) {
                val pixResponse = response.body()

                // Verificar status nos charges (onde fica o status real do pagamento PIX)
                val chargeStatus = pixResponse?.charges?.firstOrNull()?.status
                val transactionStatus = pixResponse?.charges?.firstOrNull()?.lastTransaction?.status
                
                // PIX é considerado pago quando:
                // 1. Order status = "paid" OU
                // 2. Charge status = "paid" OU  
                // 3. Transaction status = "paid"
                val isPaid = pixResponse?.status == "paid" || 
                             chargeStatus == "paid" ||
                             transactionStatus == "paid"
                
                when {
                    isPaid -> {
                        Log.d(TAG, "Pagamento PIX confirmado")
                        PixPaymentResult.Paid(
                            transactionId = pixResponse?.id ?: transactionId,
                            message = "Pagamento confirmado!"
                        )
                    }
                    pixResponse?.status == "pending" -> {
                        Log.d(TAG, "Pagamento PIX ainda pendente")
                        val qrCode = pixResponse.charges?.firstOrNull()?.lastTransaction?.qrCode
                            ?: pixResponse.lastTransaction?.qrCode
                        val qrCodeUrl = pixResponse.charges?.firstOrNull()?.lastTransaction?.qrCodeUrl
                            ?: pixResponse.lastTransaction?.qrCodeUrl
                        
                        PixPaymentResult.Success(
                            transactionId = pixResponse.id,
                            qrCode = qrCode ?: "",
                            qrCodeUrl = qrCodeUrl,
                            expiresAt = pixResponse.expiresAt
                        )
                    }
                    pixResponse?.status == "canceled" -> {
                        Log.w(TAG, "Pagamento cancelado")
                        PixPaymentResult.Error("Pagamento cancelado")
                    }
                    else -> {
                        Log.w(TAG, "Status de pagamento desconhecido")
                        PixPaymentResult.Error("Status desconhecido: ${pixResponse?.status}")
                    }
                }
            } else {
                Log.e(TAG, "Erro ao consultar status do PIX: ${response.code()}")
                PixPaymentResult.Error("Erro ao consultar status (${response.code()})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao consultar status do PIX", e)
            PixPaymentResult.Error("Erro de conexão: ${e.localizedMessage}")
        }
    }

    private suspend fun getAuthorizationHeader(): String {
        val currentUser = auth.awaitCurrentUser()
            ?: throw IllegalStateException("Usuário não autenticado. Faça login novamente.")

        val idToken = suspendCancellableCoroutine<String> { continuation ->
            currentUser.getIdToken(false)
                .addOnSuccessListener { result ->
                    val token = result.token
                    if (token.isNullOrBlank()) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Não foi possível obter o token de autenticação.")
                            )
                        }
                        return@addOnSuccessListener
                    }

                    if (continuation.isActive) {
                        continuation.resume(token)
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
        }

        return "Bearer $idToken"
    }
    
    /**
     * Validar dados do cartão
     */
    fun validateCardData(
        cardNumber: String,
        cardHolder: String,
        expiryDate: String,
        cvv: String
    ): CardValidationResult {
        val errors = mutableListOf<String>()
        
        // Validar número do cartão (remover espaços)
        val cleanCardNumber = cardNumber.replace(" ", "")
        if (cleanCardNumber.length < 13 || cleanCardNumber.length > 19) {
            errors.add("Número do cartão inválido")
        } else if (!isValidCardNumber(cleanCardNumber)) {
            errors.add("Número do cartão não passou na validação Luhn")
        }
        
        // Validar nome do titular
        if (cardHolder.isBlank() || cardHolder.length < 3) {
            errors.add("Nome do titular inválido")
        }
        
        // Validar data de expiração (formato MM/YY)
        if (!isValidExpiryDate(expiryDate)) {
            errors.add("Data de expiração inválida ou expirada")
        }
        
        // Validar CVV
        if (cvv.length < 3 || cvv.length > 4 || !cvv.all { it.isDigit() }) {
            errors.add("CVV inválido")
        }
        
        return if (errors.isEmpty()) {
            CardValidationResult.Valid
        } else {
            CardValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Validar número do cartão usando algoritmo de Luhn
     */
    private fun isValidCardNumber(cardNumber: String): Boolean {
        if (!cardNumber.all { it.isDigit() }) return false
        
        var sum = 0
        var alternate = false
        
        for (i in cardNumber.length - 1 downTo 0) {
            var digit = cardNumber[i].toString().toInt()
            
            if (alternate) {
                digit *= 2
                if (digit > 9) {
                    digit = (digit % 10) + 1
                }
            }
            
            sum += digit
            alternate = !alternate
        }
        
        return sum % 10 == 0
    }
    
    /**
     * Validar data de expiração
     */
    private fun isValidExpiryDate(expiryDate: String): Boolean {
        if (!expiryDate.matches(Regex("\\d{2}/\\d{2}"))) return false
        
        val parts = expiryDate.split("/")
        val month = parts[0].toIntOrNull() ?: return false
        val year = parts[1].toIntOrNull() ?: return false
        
        if (month !in 1..12) return false
        
        // Obter ano e mês atuais
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) % 100
        val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        
        // Verificar se não está expirado
        return when {
            year < currentYear -> false
            year == currentYear && month < currentMonth -> false
            else -> true
        }
    }
    
    /**
     * Formatar número do cartão para exibição (XXXX XXXX XXXX XXXX)
     */
    fun formatCardNumber(cardNumber: String): String {
        val clean = cardNumber.replace(" ", "")
        return clean.chunked(4).joinToString(" ")
    }
    
    /**
     * Formatar data de expiração (MM/YY)
     */
    fun formatExpiryDate(expiryDate: String): String {
        val clean = expiryDate.replace("/", "")
        return if (clean.length >= 2) {
            "${clean.substring(0, 2)}/${clean.substring(2)}"
        } else {
            clean
        }
    }
    
    /**
     * Obter bandeira do cartão pelo número
     */
    fun getCardBrand(cardNumber: String): CardBrand {
        val clean = cardNumber.replace(" ", "")
        
        return when {
            clean.startsWith("4") -> CardBrand.VISA
            clean.matches(Regex("^5[1-5].*")) -> CardBrand.MASTERCARD
            clean.matches(Regex("^3[47].*")) -> CardBrand.AMEX
            clean.matches(Regex("^6(?:011|5).*")) -> CardBrand.DISCOVER
            clean.matches(Regex("^35(?:2[89]|[3-8][0-9]).*")) -> CardBrand.JCB
            clean.matches(Regex("^(?:5[0678]\\d\\d|6304|6390|67\\d\\d).*")) -> CardBrand.ELO
            else -> CardBrand.UNKNOWN
        }
    }
}

/**
 * Resultado do processamento de pagamento
 */
sealed class PaymentResult {
    data class Success(
        val transactionId: String,
        val status: String,
        val message: String
    ) : PaymentResult()
    
    data class Pending(
        val transactionId: String,
        val message: String
    ) : PaymentResult()
    
    data class Error(
        val message: String
    ) : PaymentResult()
}

/**
 * Resultado da validação do cartão
 */
sealed class CardValidationResult {
    object Valid : CardValidationResult()
    data class Invalid(val errors: List<String>) : CardValidationResult()
}

/**
 * Bandeiras de cartão de crédito
 */
enum class CardBrand {
    VISA,
    MASTERCARD,
    AMEX,
    DISCOVER,
    JCB,
    ELO,
    UNKNOWN
}

/**
 * Resultado do processamento de pagamento PIX
 */
sealed class PixPaymentResult {
    data class Success(
        val transactionId: String,
        val qrCode: String,
        val qrCodeUrl: String?,
        val expiresAt: String?
    ) : PixPaymentResult()
    
    data class Paid(
        val transactionId: String,
        val message: String
    ) : PixPaymentResult()
    
    data class Error(
        val message: String
    ) : PixPaymentResult()
}
