package com.example.loginapp.api

import com.example.loginapp.models.payment.PaymentRequest
import com.example.loginapp.models.payment.PaymentResponse
import com.example.loginapp.models.payment.PixPaymentRequest
import com.example.loginapp.models.payment.PixPaymentResponse
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
}


