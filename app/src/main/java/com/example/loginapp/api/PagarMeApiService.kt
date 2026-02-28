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
 * Interface da API Pagar.me para Retrofit
 */
interface PagarMeApiService {
    
    /**
     * Criar uma ordem de pagamento com cartão
     * @param authorization Token de autenticação (API Key)
     * @param paymentRequest Dados da transação
     * @return Resposta da transação
     */
    @POST("orders")
    suspend fun createOrder(
        @Header("Authorization") authorization: String,
        @Body paymentRequest: PaymentRequest
    ): Response<PaymentResponse>
    
    /**
     * Criar uma ordem de pagamento PIX
     * @param authorization Token de autenticação (API Key)
     * @param pixPaymentRequest Dados da transação PIX
     * @return Resposta da transação PIX
     */
    @POST("orders")
    suspend fun createPixOrder(
        @Header("Authorization") authorization: String,
        @Body pixPaymentRequest: PixPaymentRequest
    ): Response<PixPaymentResponse>
    
    /**
     * Consultar status de uma ordem
     * @param authorization Token de autenticação
     * @param orderId ID da ordem
     * @return Status da ordem
     */
    @GET("orders/{orderId}")
    suspend fun getOrderStatus(
        @Header("Authorization") authorization: String,
        @Path("orderId") orderId: String
    ): Response<PixPaymentResponse>
}



