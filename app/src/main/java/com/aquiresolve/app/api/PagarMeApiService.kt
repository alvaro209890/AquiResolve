package com.aquiresolve.app.api

import com.aquiresolve.app.models.payment.PaymentRequest
import com.aquiresolve.app.models.payment.PaymentResponse
import com.aquiresolve.app.models.payment.PixPaymentRequest
import com.aquiresolve.app.models.payment.PixPaymentResponse
import com.aquiresolve.app.models.payment.OrderSettlementResponse
import com.aquiresolve.app.models.payment.PricingRequest
import com.aquiresolve.app.models.payment.PricingResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Interface da API de pagamentos do backend para Retrofit
 */
interface PagarMeApiService {

    /**
     * Calcula preço e repasse no backend, não no APK.
     */
    @POST("pricing/calculate")
    suspend fun calculatePricing(
        @Header("Authorization") authorization: String,
        @Body pricingRequest: PricingRequest
    ): Response<PricingResponse>

    /**
     * Cria um pagamento com cartão via backend.
     */
    @POST("card")
    suspend fun createOrder(
        @Header("Authorization") authorization: String,
        @Body paymentRequest: PaymentRequest
    ): Response<PaymentResponse>

    /**
     * Cria um pagamento PIX via backend.
     */
    @POST("pix")
    suspend fun createPixOrder(
        @Header("Authorization") authorization: String,
        @Body pixPaymentRequest: PixPaymentRequest
    ): Response<PixPaymentResponse>

    /**
     * Consulta o status de uma ordem no backend.
     */
    @GET("{orderId}/status")
    suspend fun getOrderStatus(
        @Header("Authorization") authorization: String,
        @Path("orderId") orderId: String
    ): Response<PixPaymentResponse>

    /**
     * Liquida cashback e comissão de uma OS concluída de forma idempotente no backend.
     */
    @POST("orders/{orderId}/settle")
    suspend fun settleCompletedOrder(
        @Header("Authorization") authorization: String,
        @Path("orderId") orderId: String
    ): Response<OrderSettlementResponse>
}

