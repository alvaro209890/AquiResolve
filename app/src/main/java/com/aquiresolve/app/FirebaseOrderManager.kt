package com.aquiresolve.app

import android.util.Log
import com.aquiresolve.app.api.PagarMeApiService
import com.aquiresolve.app.models.OrderData
import com.aquiresolve.app.utils.ProtocolGenerator
import com.aquiresolve.app.utils.VerificationCodeGenerator
import com.aquiresolve.app.utils.awaitCurrentUser
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*
import java.util.concurrent.TimeUnit

class FirebaseOrderManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val settlementApi: PagarMeApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.PAYMENTS_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PagarMeApiService::class.java)
    }
    
    companion object {
        private const val TAG = "FirebaseOrderManager"
        private const val ORDERS_COLLECTION = "orders"
        private const val PROVIDER_REVIEWS_COLLECTION = "provider_reviews"
        private const val CLIENT_REVIEWS_COLLECTION = "client_reviews"
    }

    data class DetailedRatings(
        val qualityRating: Int? = null,
        val punctualityRating: Int? = null,
        val communicationRating: Int? = null,
        val cleanlinessRating: Int? = null
    )

    data class ProviderReview(
        val id: String = "",
        val orderId: String = "",
        val providerId: String = "",
        val providerName: String = "",
        val clientId: String = "",
        val clientName: String = "",
        val rating: Int = 0,
        val review: String? = null,
        val qualityRating: Int? = null,
        val punctualityRating: Int? = null,
        val communicationRating: Int? = null,
        val cleanlinessRating: Int? = null,
        val tags: List<String> = emptyList(),
        val serviceType: String = "",
        val serviceName: String = "",
        val createdAt: Timestamp = Timestamp.now()
    )

    /**
     * Notas detalhadas do prestador sobre o cliente (mão inversa de DetailedRatings).
     */
    data class ClientDetailedRatings(
        val communicationRating: Int? = null,
        val cordialityRating: Int? = null,
        val clarityRating: Int? = null,
        val environmentRating: Int? = null
    )

    /**
     * Avaliação pública de um cliente, feita por prestadores (coleção client_reviews).
     */
    data class ClientReview(
        val id: String = "",
        val orderId: String = "",
        val clientId: String = "",
        val clientName: String = "",
        val providerId: String = "",
        val providerName: String = "",
        val rating: Int = 0,
        val review: String? = null,
        val communicationRating: Int? = null,
        val cordialityRating: Int? = null,
        val clarityRating: Int? = null,
        val environmentRating: Int? = null,
        val tags: List<String> = emptyList(),
        val serviceType: String = "",
        val serviceName: String = "",
        val createdAt: Timestamp = Timestamp.now()
    )
    
    /**
     * Cria um novo pedido no Firebase
     */
    suspend fun createOrder(orderData: OrderData): Result<String> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            // Gerar ID único para o pedido
            val orderId = db.collection(ORDERS_COLLECTION).document().id
            
            // Gerar protocolo único
            val protocol = ProtocolGenerator.generateProtocol()
            
            // Monta um payload ENXUTO e compatível com a regra `validOrderCreate`
            // (pay-before-distribution): grava SOMENTE os campos permitidos, com
            // status/paymentStatus = awaiting_payment. Usar `orderData.copy(...).set()`
            // serializava o OrderData inteiro (id, priority, status=distributing,
            // distributionStartedAt, adminNotes, flags de conclusão...), que a regra
            // NEGA com PERMISSION_DENIED.
            val now = Timestamp.now()
            val order = mutableMapOf<String, Any>(
                "clientId" to user.uid,
                "clientName" to orderData.clientName,
                "clientEmail" to orderData.clientEmail,
                "protocol" to protocol,
                "serviceType" to orderData.serviceType,
                "serviceName" to orderData.serviceName,
                "description" to orderData.description,
                "address" to orderData.address,
                "zipCode" to orderData.zipCode,
                "city" to orderData.city,
                "state" to orderData.state,
                "status" to OrderData.STATUS_AWAITING_PAYMENT,
                "paymentStatus" to OrderData.STATUS_AWAITING_PAYMENT,
                "estimatedPrice" to orderData.estimatedPrice,
                "providerCommission" to orderData.providerCommission,
                "createdAt" to now,
                "updatedAt" to now
            )
            // Opcionais — todos na allowlist (`hasOnly`) da regra; só grava se houver valor.
            orderData.clientPhone.takeIf { it.isNotBlank() }?.let { order["clientPhone"] = it }
            orderData.complement?.takeIf { it.isNotBlank() }?.let { order["complement"] = it }
            orderData.coordinates?.let { order["coordinates"] = it }
            orderData.scheduledDate?.let { order["scheduledDate"] = it }
            orderData.preferredTimeSlot.takeIf { it.isNotBlank() }?.let { order["preferredTimeSlot"] = it }
            orderData.finalPrice?.let { order["finalPrice"] = it }
            if (orderData.images.isNotEmpty()) order["images"] = orderData.images

            // Salvar no Firestore
            db.collection(ORDERS_COLLECTION)
                .document(orderId)
                .set(order)
                .await()
            
            Log.d(TAG, "Pedido criado com sucesso: $orderId | Protocolo: $protocol")
            Result.success(orderId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar pedido: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Busca pedidos do usuário logado
     */
    suspend fun getUserOrders(): Result<List<OrderData>> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            // Buscar pedidos (SEM orderBy para evitar índice composto)
            val snapshot = db.collection(ORDERS_COLLECTION)
                .whereEqualTo("clientId", user.uid)
                .get()
                .await()
            
            val orders = snapshot.documents.mapNotNull { doc ->
                doc.toObject(OrderData::class.java)?.copy(id = doc.id)
            }.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L } // Ordenar manualmente
            
            Log.d(TAG, "Pedidos carregados: ${orders.size}")
            Result.success(orders)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar pedidos: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Busca um pedido específico por ID
     */
    suspend fun getOrderById(orderId: String): Result<OrderData?> {
        return try {
            val snapshot = db.collection(ORDERS_COLLECTION)
                .document(orderId)
                .get()
                .await()
            
            val order = snapshot.toObject(OrderData::class.java)?.copy(id = snapshot.id)
            Result.success(order)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar pedido: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Atualiza o status de um pedido
     */
    suspend fun updateOrderStatus(orderId: String, newStatus: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "status" to newStatus,
                "updatedAt" to Timestamp.now()
            )
            
            db.collection(ORDERS_COLLECTION)
                .document(orderId)
                .update(updates)
                .await()
            
            Log.d(TAG, "Status do pedido atualizado: $orderId -> $newStatus")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar status: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Marca serviço como iniciado (prestador ou cliente podem acionar; status vai para in_progress)
     */
    suspend fun startService(orderId: String): Result<Unit> {
        return try {
            // Validar que o pedido existe e está em estado compatível
            val orderDoc = db.collection(ORDERS_COLLECTION).document(orderId).get().await()
            if (!orderDoc.exists()) {
                return Result.failure(Exception("Pedido não encontrado"))
            }
            val currentStatus = orderDoc.getString("status") ?: ""
            if (currentStatus == OrderData.STATUS_IN_PROGRESS) {
                Log.d(TAG, "Serviço já estava iniciado: $orderId")
                return Result.success(Unit)
            }
            val allowedStatuses = setOf(OrderData.STATUS_ASSIGNED)
            if (currentStatus !in allowedStatuses) {
                Log.w(TAG, "startService bloqueado: pedido $orderId está em status '$currentStatus'")
                return Result.failure(Exception("Pedido não está em estado que permita iniciar serviço"))
            }

            val updates = mapOf(
                "status" to OrderData.STATUS_IN_PROGRESS,
                "startedAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )

            db.collection(ORDERS_COLLECTION)
                .document(orderId)
                .update(updates)
                .await()

            Log.d(TAG, "Serviço iniciado: $orderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar serviço: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Gera códigos de verificação quando o prestador aceita um pedido
     */
    suspend fun generateVerificationCodes(orderId: String): Result<Pair<String, String>> {
        return try {
            val clientCode = VerificationCodeGenerator.generateCode()
            val providerCode = VerificationCodeGenerator.generateCode()
            
            val updates = mapOf(
                "clientVerificationCode" to clientCode,
                "providerVerificationCode" to providerCode,
                "verificationCodesGeneratedAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )
            
            db.collection(ORDERS_COLLECTION)
                .document(orderId)
                .update(updates)
                .await()
            
            Log.d(TAG, "Códigos de verificação gerados para pedido: $orderId")
            
            Result.success(Pair(clientCode, providerCode))
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gerar códigos de verificação: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Aceita um pedido disponível em uma única transação.
     * Centraliza a regra para evitar dois prestadores aceitando o mesmo pedido.
     */
    suspend fun acceptOrderAsProvider(orderId: String): Result<Unit> {
        return try {
            if (orderId.isBlank()) {
                return Result.failure(IllegalArgumentException("ID do pedido inválido"))
            }

            val currentUser = auth.awaitCurrentUser()
                ?: return Result.failure(IllegalStateException("Usuário não autenticado"))

            val providerDoc = db.collection("providers").document(currentUser.uid).get().await()
            val providerName = if (providerDoc.exists()) {
                providerDoc.getString("fullName") ?: currentUser.displayName ?: "Prestador"
            } else {
                currentUser.displayName ?: "Prestador"
            }

            val clientCode = VerificationCodeGenerator.generateCode()
            val providerCode = VerificationCodeGenerator.generateCode()
            val orderRef = db.collection(ORDERS_COLLECTION).document(orderId)
            val now = Timestamp.now()

            db.runTransaction { tx ->
                val snap = tx.get(orderRef)
                if (!snap.exists()) {
                    throw IllegalStateException("Pedido não encontrado")
                }

                val currentStatus = snap.getString("status") ?: OrderData.STATUS_DISTRIBUTING
                val assigned = snap.getString("assignedProvider")
                val allowedStatuses = setOf(OrderData.STATUS_DISTRIBUTING, OrderData.STATUS_PENDING, "available")

                if (currentStatus !in allowedStatuses || !assigned.isNullOrBlank()) {
                    throw IllegalStateException("Pedido indisponível")
                }

                tx.update(orderRef, mapOf(
                    "assignedProvider" to currentUser.uid,
                    "assignedProviderName" to providerName,
                    "status" to OrderData.STATUS_ASSIGNED,
                    "assignedAt" to now,
                    "clientVerificationCode" to clientCode,
                    "providerVerificationCode" to providerCode,
                    "verificationCodesGeneratedAt" to now,
                    "updatedAt" to now
                ))
            }.await()

            Log.d(TAG, "Pedido aceito com sucesso: $orderId pelo prestador ${currentUser.uid}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao aceitar pedido: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Finaliza um pedido verificando o código do cliente
     * O prestador deve fornecer o código do cliente para finalizar
     */
    suspend fun completeOrderWithVerification(orderId: String, clientCode: String): Result<Unit> {
        return try {
            val docRef = db.collection(ORDERS_COLLECTION).document(orderId)
            
            db.runTransaction { tx ->
                // TODAS AS LEITURAS PRIMEIRO
                val snap = tx.get(docRef)
                
                // Verificar se o pedido existe e está em andamento
                val status = snap.getString("status") ?: ""
                if (status != OrderData.STATUS_IN_PROGRESS && status != OrderData.STATUS_ASSIGNED) {
                    throw IllegalStateException("Pedido não está em andamento")
                }
                
                // Verificar o código do cliente
                val storedClientCode = snap.getString("clientVerificationCode")
                if (storedClientCode == null) {
                    throw IllegalStateException("Código de verificação não encontrado")
                }
                
                val cleanedCode = VerificationCodeGenerator.cleanCode(clientCode)
                if (cleanedCode != storedClientCode) {
                    throw IllegalArgumentException("Código de verificação incorreto")
                }

                // Código correto! Finalizar o pedido
                val updates = mapOf(
                    "status" to OrderData.STATUS_COMPLETED,
                    "completedAt" to Timestamp.now(),
                    "providerCompletionConfirmed" to true,
                    "clientCompletionConfirmed" to true,
                    "settlementStatus" to "pending",
                    "settlementRequestedAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
                
                tx.update(docRef, updates)
            }.await()

            settleCompletedOrderOnBackend(orderId)
            
            Log.d(TAG, "✅ Pedido finalizado com sucesso: $orderId")
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Erro ao finalizar pedido: ${e.message}")
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Código de verificação incorreto: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao finalizar pedido: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Confirma a conclusão pelo ator ("client" ou "provider"). Fecha o pedido quando ambos confirmarem
     * (Método antigo mantido para compatibilidade)
     */
    suspend fun confirmCompletion(orderId: String, actor: String): Result<Unit> {
        return try {
            val docRef = db.collection(ORDERS_COLLECTION).document(orderId)
            db.runTransaction { tx ->
                // TODAS AS LEITURAS PRIMEIRO
                val snap = tx.get(docRef)
                val clientConfirmed = snap.getBoolean("clientCompletionConfirmed") ?: false
                val providerConfirmed = snap.getBoolean("providerCompletionConfirmed") ?: false

                val newClient = if (actor == "client") true else clientConfirmed
                val newProvider = if (actor == "provider") true else providerConfirmed

                val updates = mutableMapOf<String, Any>(
                    "updatedAt" to Timestamp.now()
                )

                if (actor == "client") {
                    updates["clientCompletionConfirmed"] = true
                } else if (actor == "provider") {
                    updates["providerCompletionConfirmed"] = true
                }

                if (newClient && newProvider) {
                    updates["status"] = OrderData.STATUS_COMPLETED
                    updates["completedAt"] = Timestamp.now()
                    updates["settlementStatus"] = "pending"
                    updates["settlementRequestedAt"] = Timestamp.now()
                }

                tx.update(docRef, updates)
            }.await()

            val updatedOrder = db.collection(ORDERS_COLLECTION).document(orderId).get().await()
            if (updatedOrder.getString("status") == OrderData.STATUS_COMPLETED) {
                settleCompletedOrderOnBackend(orderId)
            }

            Log.d(TAG, "Confirmação registrada ($actor): $orderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro na confirmação de conclusão: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun settleCompletedOrder(orderId: String): Result<Unit> {
        return try {
            if (settleCompletedOrderOnBackend(orderId)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Liquidação financeira pendente"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Submete avaliação de pedido concluído com validação de regras de negócio.
     *
     * Regras:
     * - Apenas cliente dono do pedido pode avaliar.
     * - Pedido precisa estar concluído.
     * - Apenas uma avaliação por pedido (imutável).
     * - Atualiza média pública do prestador com base em pedidos reais avaliados.
     */
    suspend fun submitOrderRating(
        orderId: String,
        rating: Int,
        review: String? = null,
        detailedRatings: DetailedRatings = DetailedRatings(),
        tags: List<String> = emptyList()
    ): Result<Unit> {
        if (orderId.isBlank()) {
            return Result.failure(IllegalArgumentException("ID do pedido inválido"))
        }
        if (rating !in 1..5) {
            return Result.failure(IllegalArgumentException("A nota geral deve estar entre 1 e 5 estrelas"))
        }

        val detailedValues = listOf(
            detailedRatings.qualityRating,
            detailedRatings.punctualityRating,
            detailedRatings.communicationRating,
            detailedRatings.cleanlinessRating
        )
        if (detailedValues.any { it != null && it !in 1..5 }) {
            return Result.failure(IllegalArgumentException("As notas detalhadas devem estar entre 1 e 5 estrelas"))
        }

        return try {
            val user = auth.awaitCurrentUser()
                ?: return Result.failure(IllegalStateException("Usuário não autenticado"))

            val orderRef = db.collection(ORDERS_COLLECTION).document(orderId)
            var providerIdToUpdate: String? = null
            var clientNameForReview: String? = null
            var providerNameForReview: String? = null
            var serviceTypeForReview: String? = null
            var serviceNameForReview: String? = null

            db.runTransaction { transaction ->
                val orderDoc = transaction.get(orderRef)
                if (!orderDoc.exists()) {
                    throw IllegalStateException("Pedido não encontrado")
                }

                val clientId = orderDoc.getString("clientId")
                if (clientId != user.uid) {
                    throw SecurityException("Somente o cliente dono do pedido pode avaliar")
                }

                val status = orderDoc.getString("status")
                if (status != OrderData.STATUS_COMPLETED) {
                    throw IllegalStateException("Somente pedidos concluídos podem ser avaliados")
                }

                val existingRating = orderDoc.getLong("rating")?.toInt()
                    ?: orderDoc.getDouble("rating")?.toInt()
                if (existingRating != null && existingRating > 0) {
                    throw IllegalStateException("Este pedido já foi avaliado")
                }

                providerIdToUpdate = orderDoc.getString("assignedProvider")
                clientNameForReview = orderDoc.getString("clientName") ?: user.displayName
                providerNameForReview = orderDoc.getString("assignedProviderName")
                serviceTypeForReview = orderDoc.getString("serviceType")
                serviceNameForReview = orderDoc.getString("serviceName")

                val updates = mutableMapOf<String, Any>(
                    "rating" to rating,
                    "reviewedAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )

                val normalizedReview = review?.trim()
                if (!normalizedReview.isNullOrEmpty()) {
                    updates["review"] = normalizedReview
                }

                detailedRatings.qualityRating?.let { updates["qualityRating"] = it }
                detailedRatings.punctualityRating?.let { updates["punctualityRating"] = it }
                detailedRatings.communicationRating?.let { updates["communicationRating"] = it }
                detailedRatings.cleanlinessRating?.let { updates["cleanlinessRating"] = it }

                val normalizedTags = normalizeReviewTags(tags)
                if (normalizedTags.isNotEmpty()) {
                    updates["ratingTags"] = normalizedTags
                }

                transaction.update(orderRef, updates)
            }.await()

            // Recalcula a nota pública usando os pedidos reais avaliados do prestador.
            providerIdToUpdate?.takeIf { it.isNotBlank() }?.let { providerId ->
                updateProviderAverageRatingFromOrders(providerId)
            }

            // Cria documento na coleção provider_reviews para consulta pública
            providerIdToUpdate?.takeIf { it.isNotBlank() }?.let { providerId ->
                val reviewData = mutableMapOf<String, Any>(
                    "orderId" to orderId,
                    "providerId" to providerId,
                    "providerName" to (providerNameForReview ?: "Prestador"),
                    "clientId" to user.uid,
                    "clientName" to (clientNameForReview ?: "Cliente"),
                    "rating" to rating,
                    "createdAt" to Timestamp.now()
                )

                val normalizedReview = review?.trim()
                if (!normalizedReview.isNullOrEmpty()) {
                    reviewData["review"] = normalizedReview
                }

                serviceTypeForReview?.let { reviewData["serviceType"] = it }
                serviceNameForReview?.let { reviewData["serviceName"] = it }
                detailedRatings.qualityRating?.let { reviewData["qualityRating"] = it }
                detailedRatings.punctualityRating?.let { reviewData["punctualityRating"] = it }
                detailedRatings.communicationRating?.let { reviewData["communicationRating"] = it }
                detailedRatings.cleanlinessRating?.let { reviewData["cleanlinessRating"] = it }

                val normalizedTags = normalizeReviewTags(tags)
                if (normalizedTags.isNotEmpty()) {
                    reviewData["tags"] = normalizedTags
                }

                db.collection(PROVIDER_REVIEWS_COLLECTION)
                    .document(orderId)
                    .set(reviewData, SetOptions.merge())
                    .await()
            }

            Log.d(TAG, "Pedido avaliado com sucesso: $orderId -> $rating estrelas")
            Result.success(Unit)
        } catch (e: SecurityException) {
            Log.e(TAG, "Avaliação não autorizada: ${e.message}")
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Erro de regra ao avaliar pedido: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao avaliar pedido: ${e.message}")
            Result.failure(e)
        }
    }

    @Deprecated(
        message = "Use submitOrderRating para validação completa e persistência de notas detalhadas",
        replaceWith = ReplaceWith("submitOrderRating(orderId, rating, review)")
    )
    suspend fun rateOrder(orderId: String, rating: Int, review: String? = null): Result<Unit> {
        return submitOrderRating(orderId, rating, review)
    }

    /**
     * Busca todas as avaliações públicas de um prestador (da coleção provider_reviews).
     * Ordenado por data decrescente (mais recentes primeiro).
     */
    suspend fun getProviderReviews(providerId: String, limit: Long = 50): List<ProviderReview> {
        if (providerId.isBlank()) return emptyList()

        return try {
            val snapshot = db.collection(PROVIDER_REVIEWS_COLLECTION)
                .whereEqualTo("providerId", providerId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            snapshot.documents.map { doc ->
                ProviderReview(
                    id = doc.id,
                    orderId = doc.getString("orderId") ?: "",
                    providerId = doc.getString("providerId") ?: providerId,
                    providerName = doc.getString("providerName") ?: "",
                    clientId = doc.getString("clientId") ?: "",
                    clientName = doc.getString("clientName") ?: "Cliente",
                    rating = (doc.getLong("rating") ?: 0L).toInt(),
                    review = doc.getString("review"),
                    qualityRating = doc.getLong("qualityRating")?.toInt(),
                    punctualityRating = doc.getLong("punctualityRating")?.toInt(),
                    communicationRating = doc.getLong("communicationRating")?.toInt(),
                    cleanlinessRating = doc.getLong("cleanlinessRating")?.toInt(),
                    tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    serviceType = doc.getString("serviceType") ?: "",
                    serviceName = doc.getString("serviceName") ?: "",
                    createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar avaliações do prestador $providerId: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Retorna estatísticas agregadas das avaliações de um prestador.
     */
    suspend fun getProviderReviewStats(providerId: String): Map<String, Any> {
        if (providerId.isBlank()) return mapOf("averageRating" to 0.0, "totalReviews" to 0, "distribution" to mapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0))

        return try {
            val snapshot = db.collection(PROVIDER_REVIEWS_COLLECTION)
                .whereEqualTo("providerId", providerId)
                .get()
                .await()

            var total = 0.0
            var count = 0
            val distribution = mutableMapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0)

            for (doc in snapshot.documents) {
                val r = (doc.getLong("rating") ?: 0L).toInt()
                if (r in 1..5) {
                    total += r
                    count++
                    distribution[r] = (distribution[r] ?: 0) + 1
                }
            }

            mapOf(
                "averageRating" to if (count > 0) total / count else 0.0,
                "totalReviews" to count,
                "distribution" to distribution
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar estatísticas do prestador $providerId: ${e.message}", e)
            mapOf("averageRating" to 0.0, "totalReviews" to 0, "distribution" to mapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0))
        }
    }

    private suspend fun updateProviderAverageRatingFromOrders(providerId: String) {
        val ratedOrders = db.collection(ORDERS_COLLECTION)
            .whereEqualTo("assignedProvider", providerId)
            .get()
            .await()

        var totalRating = 0.0
        var totalRatings = 0

        for (doc in ratedOrders.documents) {
            val ratingValue = doc.getLong("rating")?.toInt()
                ?: doc.getDouble("rating")?.toInt()
                ?: continue
            if (ratingValue in 1..5) {
                totalRating += ratingValue
                totalRatings++
            }
        }

        val average = if (totalRatings > 0) totalRating / totalRatings else 0.0

        db.collection("providers")
            .document(providerId)
            .set(
                mapOf(
                    "rating" to average,
                    "totalRatings" to totalRatings,
                    "updatedAt" to Date()
                ),
                SetOptions.merge()
            )
            .await()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Avaliação do PRESTADOR sobre o CLIENTE (mão inversa)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * O prestador avalia o cliente após concluir o pedido.
     *
     * Regras de negócio:
     * - Apenas o prestador atribuído ao pedido pode avaliar.
     * - O pedido precisa estar concluído.
     * - Uma única avaliação por pedido (doc id = orderId em client_reviews).
     * - Atualiza a reputação pública do cliente (users/{clientId}).
     */
    suspend fun submitClientRating(
        orderId: String,
        rating: Int,
        review: String? = null,
        detailedRatings: ClientDetailedRatings = ClientDetailedRatings(),
        tags: List<String> = emptyList()
    ): Result<Unit> {
        if (orderId.isBlank()) {
            return Result.failure(IllegalArgumentException("ID do pedido inválido"))
        }
        if (rating !in 1..5) {
            return Result.failure(IllegalArgumentException("A nota geral deve estar entre 1 e 5 estrelas"))
        }

        val detailedValues = listOf(
            detailedRatings.communicationRating,
            detailedRatings.cordialityRating,
            detailedRatings.clarityRating,
            detailedRatings.environmentRating
        )
        if (detailedValues.any { it != null && it !in 1..5 }) {
            return Result.failure(IllegalArgumentException("As notas detalhadas devem estar entre 1 e 5 estrelas"))
        }

        return try {
            val user = auth.awaitCurrentUser()
                ?: return Result.failure(IllegalStateException("Usuário não autenticado"))

            val orderDoc = db.collection(ORDERS_COLLECTION).document(orderId).get().await()
            if (!orderDoc.exists()) {
                return Result.failure(IllegalStateException("Pedido não encontrado"))
            }

            val assignedProvider = orderDoc.getString("assignedProvider")
            if (assignedProvider != user.uid) {
                return Result.failure(SecurityException("Somente o prestador do pedido pode avaliar o cliente"))
            }

            val status = orderDoc.getString("status")
            if (status != OrderData.STATUS_COMPLETED) {
                return Result.failure(IllegalStateException("Somente pedidos concluídos podem ser avaliados"))
            }

            val clientId = orderDoc.getString("clientId")
            if (clientId.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Cliente do pedido não identificado"))
            }

            val reviewRef = db.collection(CLIENT_REVIEWS_COLLECTION).document(orderId)
            val existing = reviewRef.get().await()
            if (existing.exists()) {
                return Result.failure(IllegalStateException("Este cliente já foi avaliado neste pedido"))
            }

            val reviewData = mutableMapOf<String, Any>(
                "orderId" to orderId,
                "clientId" to clientId,
                "clientName" to (orderDoc.getString("clientName") ?: "Cliente"),
                "providerId" to user.uid,
                "providerName" to (orderDoc.getString("assignedProviderName") ?: user.displayName ?: "Prestador"),
                "rating" to rating,
                "createdAt" to Timestamp.now()
            )

            val normalizedReview = review?.trim()
            if (!normalizedReview.isNullOrEmpty()) {
                reviewData["review"] = normalizedReview
            }

            orderDoc.getString("serviceType")?.let { reviewData["serviceType"] = it }
            orderDoc.getString("serviceName")?.let { reviewData["serviceName"] = it }
            detailedRatings.communicationRating?.let { reviewData["communicationRating"] = it }
            detailedRatings.cordialityRating?.let { reviewData["cordialityRating"] = it }
            detailedRatings.clarityRating?.let { reviewData["clarityRating"] = it }
            detailedRatings.environmentRating?.let { reviewData["environmentRating"] = it }

            val normalizedTags = normalizeReviewTags(tags)
            if (normalizedTags.isNotEmpty()) {
                reviewData["tags"] = normalizedTags
            }

            reviewRef.set(reviewData).await()

            // Recalcula a reputação pública do cliente.
            updateClientAverageRatingFromReviews(clientId)

            Log.d(TAG, "Cliente avaliado com sucesso: $orderId -> $rating estrelas")
            Result.success(Unit)
        } catch (e: SecurityException) {
            Log.e(TAG, "Avaliação de cliente não autorizada: ${e.message}")
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Erro de regra ao avaliar cliente: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao avaliar cliente: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Indica se o pedido já recebeu avaliação do prestador sobre o cliente.
     * Nunca lança — em caso de erro, retorna false (permite tentar avaliar).
     */
    suspend fun hasRatedClient(orderId: String): Boolean {
        if (orderId.isBlank()) return false
        return try {
            db.collection(CLIENT_REVIEWS_COLLECTION).document(orderId).get().await().exists()
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao verificar avaliação de cliente: ${e.message}")
            false
        }
    }

    /**
     * Busca as avaliações públicas de um cliente (coleção client_reviews),
     * mais recentes primeiro.
     */
    suspend fun getClientReviews(clientId: String, limit: Long = 50): List<ClientReview> {
        if (clientId.isBlank()) return emptyList()

        return try {
            val snapshot = db.collection(CLIENT_REVIEWS_COLLECTION)
                .whereEqualTo("clientId", clientId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            snapshot.documents.map { doc ->
                ClientReview(
                    id = doc.id,
                    orderId = doc.getString("orderId") ?: "",
                    clientId = doc.getString("clientId") ?: clientId,
                    clientName = doc.getString("clientName") ?: "Cliente",
                    providerId = doc.getString("providerId") ?: "",
                    providerName = doc.getString("providerName") ?: "Prestador",
                    rating = (doc.getLong("rating") ?: 0L).toInt(),
                    review = doc.getString("review"),
                    communicationRating = doc.getLong("communicationRating")?.toInt(),
                    cordialityRating = doc.getLong("cordialityRating")?.toInt(),
                    clarityRating = doc.getLong("clarityRating")?.toInt(),
                    environmentRating = doc.getLong("environmentRating")?.toInt(),
                    tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    serviceType = doc.getString("serviceType") ?: "",
                    serviceName = doc.getString("serviceName") ?: "",
                    createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar avaliações do cliente $clientId: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Estatísticas agregadas das avaliações de um cliente.
     */
    suspend fun getClientReviewStats(clientId: String): Map<String, Any> {
        val empty = mapOf(
            "averageRating" to 0.0,
            "totalReviews" to 0,
            "distribution" to mapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0)
        )
        if (clientId.isBlank()) return empty

        return try {
            val snapshot = db.collection(CLIENT_REVIEWS_COLLECTION)
                .whereEqualTo("clientId", clientId)
                .get()
                .await()

            var total = 0.0
            var count = 0
            val distribution = mutableMapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0)

            for (doc in snapshot.documents) {
                val r = (doc.getLong("rating") ?: 0L).toInt()
                if (r in 1..5) {
                    total += r
                    count++
                    distribution[r] = (distribution[r] ?: 0) + 1
                }
            }

            mapOf(
                "averageRating" to if (count > 0) total / count else 0.0,
                "totalReviews" to count,
                "distribution" to distribution
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar estatísticas do cliente $clientId: ${e.message}", e)
            empty
        }
    }

    private suspend fun updateClientAverageRatingFromReviews(clientId: String) {
        val reviews = db.collection(CLIENT_REVIEWS_COLLECTION)
            .whereEqualTo("clientId", clientId)
            .get()
            .await()

        var totalRating = 0.0
        var totalRatings = 0

        for (doc in reviews.documents) {
            val ratingValue = doc.getLong("rating")?.toInt()
                ?: doc.getDouble("rating")?.toInt()
                ?: continue
            if (ratingValue in 1..5) {
                totalRating += ratingValue
                totalRatings++
            }
        }

        val average = if (totalRatings > 0) totalRating / totalRatings else 0.0

        db.collection("users")
            .document(clientId)
            .set(
                mapOf(
                    "clientRating" to average,
                    "clientTotalRatings" to totalRatings,
                    "updatedAt" to Date()
                ),
                SetOptions.merge()
            )
            .await()
    }

    /**
     * Normaliza tags de avaliação: remove vazias/duplicadas, corta tamanho e limita a 8.
     */
    private fun normalizeReviewTags(tags: List<String>): List<String> {
        return tags.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.take(40) }
            .distinct()
            .take(8)
            .toList()
    }

    private suspend fun settleCompletedOrderOnBackend(orderId: String): Boolean {
        try {
            val authHeader = getAuthorizationHeader()
            val response = settlementApi.settleCompletedOrder(authHeader, orderId)
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Liquidação financeira confirmada para pedido $orderId")
                return true
            }
            Log.w(TAG, "Liquidação pendente para pedido $orderId: HTTP ${response.code()}")
        } catch (e: Exception) {
            Log.w(TAG, "Liquidação pendente para pedido $orderId: ${e.message}")
        }
        return false
    }

    private suspend fun getAuthorizationHeader(): String {
        val currentUser = auth.awaitCurrentUser()
            ?: throw IllegalStateException("Usuário não autenticado")

        val cachedToken = currentUser.getIdToken(false).await().token
        if (!cachedToken.isNullOrBlank()) {
            return "Bearer $cachedToken"
        }

        val freshToken = currentUser.getIdToken(true).await().token
            ?: throw IllegalStateException("Não foi possível obter token de autenticação")
        return "Bearer $freshToken"
    }
    
    /**
     * Listener em tempo real para mudanças nos pedidos.
     * @return ListenerRegistration para remover o listener com .remove() quando não for mais necessário.
     */
    fun listenToUserOrders(onOrdersChanged: (List<OrderData>) -> Unit): ListenerRegistration? {
        val user = auth.currentUser
        if (user == null) {
            Log.e(TAG, "Usuário não autenticado")
            return null
        }
        
        return db.collection(ORDERS_COLLECTION)
            .whereEqualTo("clientId", user.uid)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erro no listener: ${error.message}")
                    return@addSnapshotListener
                }
                
                val orders = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(OrderData::class.java)
                } ?: emptyList()
                
                onOrdersChanged(orders)
            }
    }
    
    /**
     * Listener em tempo real para um pedido específico.
     * @return ListenerRegistration para remover o listener com .remove() quando não for mais necessário.
     */
    fun listenToOrder(orderId: String, onOrderChanged: (OrderData?) -> Unit): ListenerRegistration? {
        return db.collection(ORDERS_COLLECTION)
            .document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erro no listener do pedido: ${error.message}")
                    return@addSnapshotListener
                }
                
                val order = snapshot?.toObject(OrderData::class.java)
                onOrderChanged(order)
            }
    }
    
    /**
     * Cria um pedido com dados básicos (método helper)
     */
    suspend fun createSimpleOrder(
        serviceType: String,
        serviceName: String,
        description: String,
        address: String,
        city: String,
        state: String,
        zipCode: String,
        scheduledDate: Date? = null,
        priority: String = OrderData.PRIORITY_NORMAL,
        estimatedPrice: Double = 0.0
    ): Result<String> {
        val user = auth.awaitCurrentUser()
        if (user == null) {
            return Result.failure(Exception("Usuário não autenticado"))
        }
        
        val orderData = OrderData(
            serviceType = serviceType,
            serviceName = serviceName,
            description = description,
            priority = priority,
            address = address,
            city = city,
            state = state,
            zipCode = zipCode,
            scheduledDate = scheduledDate?.let { Timestamp(it) },
            estimatedPrice = estimatedPrice
        )
        
        return createOrder(orderData)
    }
    
    /**
     * Cancela um pedido com informações detalhadas
     */
    suspend fun cancelOrder(orderId: String, cancelledBy: String, reason: String = ""): Result<Unit> {
        return try {
            // Validar orderId
            if (orderId.isBlank()) {
                Log.e(TAG, "OrderId está vazio")
                return Result.failure(Exception("ID do pedido inválido"))
            }
            
            Log.d(TAG, "Tentando cancelar pedido: $orderId")
            
            val orderRef = db.collection(ORDERS_COLLECTION).document(orderId)
            
            // Verificar se o documento existe antes de atualizar
            val documentSnapshot = orderRef.get().await()
            if (!documentSnapshot.exists()) {
                Log.e(TAG, "Documento não encontrado: $orderId")
                return Result.failure(Exception("Pedido não encontrado"))
            }
            
            val updates = mutableMapOf<String, Any>(
                "status" to OrderData.STATUS_CANCELLED,
                "cancelledAt" to Timestamp.now(),
                "cancelledBy" to cancelledBy,
                "cancellationReason" to reason,
                "updatedAt" to Timestamp.now()
            )

            // Só sinaliza reembolso se o pedido REALMENTE foi pago. Cancelar um pedido
            // ainda em 'awaiting_payment' (nada cobrado) não deve criar pendência de
            // reembolso nem prometer estorno ao cliente.
            val paymentStatus = (documentSnapshot.getString("paymentStatus") ?: "").lowercase()
            val wasPaid = paymentStatus in setOf("paid", "captured", "approved", "confirmed")
            if (cancelledBy == "client" && wasPaid) {
                updates["refundStatus"] = "pending"
                updates["refundRequestedAt"] = Timestamp.now()
            }
            
            orderRef.update(updates).await()

            Log.d(TAG, "Pedido cancelado com sucesso: $orderId por $cancelledBy")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao cancelar pedido: ${e.message}")
            Log.e(TAG, "OrderId: $orderId")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }

    /**
     * Cliente solicita reembolso descrevendo o motivo e anexando fotos.
     * Só é permitido em pedido pago (a regra Firestore também valida isso).
     * Grava refundStatus='requested' + motivo + fotos; o admin aprova/recusa no painel.
     */
    suspend fun requestRefund(
        orderId: String,
        reason: String,
        photoUrls: List<String>
    ): Result<Unit> {
        if (orderId.isBlank()) {
            return Result.failure(IllegalArgumentException("ID do pedido inválido"))
        }
        val trimmedReason = reason.trim()
        if (trimmedReason.length < 10) {
            return Result.failure(IllegalArgumentException("Descreva o motivo com pelo menos 10 caracteres"))
        }
        return try {
            val user = auth.awaitCurrentUser()
                ?: return Result.failure(IllegalStateException("Usuário não autenticado"))

            val orderRef = db.collection(ORDERS_COLLECTION).document(orderId)
            val snap = orderRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("Pedido não encontrado"))
            }
            if (snap.getString("clientId") != user.uid) {
                return Result.failure(SecurityException("Somente o cliente dono do pedido pode solicitar reembolso"))
            }
            val paymentStatus = (snap.getString("paymentStatus") ?: "").lowercase()
            if (paymentStatus !in setOf("paid", "captured", "approved", "confirmed")) {
                return Result.failure(IllegalStateException("Só é possível solicitar reembolso de pedido pago"))
            }
            val current = (snap.getString("refundStatus") ?: "").lowercase()
            if (current in setOf("requested", "processing", "completed")) {
                return Result.failure(IllegalStateException("Já existe uma solicitação de reembolso para este pedido"))
            }

            val updates = mutableMapOf<String, Any>(
                "refundStatus" to "requested",
                "refundReason" to trimmedReason,
                "refundPhotos" to photoUrls,
                "refundRequestedAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )
            orderRef.update(updates).await()
            Log.d(TAG, "Solicitação de reembolso registrada: $orderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao solicitar reembolso: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Busca pedidos filtrados por status
     */
    suspend fun getOrdersByStatus(status: String? = null): Result<List<OrderData>> {
        return try {
            val user = auth.awaitCurrentUser()
            if (user == null) {
                return Result.failure(Exception("Usuário não autenticado"))
            }
            
            var query = db.collection(ORDERS_COLLECTION)
                .whereEqualTo("clientId", user.uid)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            
            if (status != null && status != "all") {
                query = query.whereEqualTo("status", status)
            }
            
            val snapshot = query.get().await()
            
            val orders = snapshot.documents.mapNotNull { doc ->
                doc.toObject(OrderData::class.java)?.copy(id = doc.id)
            }
            
            Log.d(TAG, "Pedidos filtrados carregados: ${orders.size} (status: $status)")
            Result.success(orders)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar pedidos filtrados: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Conta quantos pedidos um prestador completou
     */
    suspend fun countCompletedOrdersByProvider(providerId: String): Result<Int> {
        return try {
            val snapshot = db.collection(ORDERS_COLLECTION)
                .whereEqualTo("assignedProvider", providerId)
                .whereEqualTo("status", OrderData.STATUS_COMPLETED)
                .get()
                .await()
            
            val count = snapshot.size()
            Log.d(TAG, "Pedidos completados pelo prestador $providerId: $count")
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao contar pedidos completados: ${e.message}")
            Result.failure(e)
        }
    }
} 
