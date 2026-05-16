package com.aquiresolve.app

import android.util.Log
import com.aquiresolve.app.models.OrderData
import com.aquiresolve.app.utils.ProtocolGenerator
import com.aquiresolve.app.utils.VerificationCodeGenerator
import com.aquiresolve.app.utils.awaitCurrentUser
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.*

class FirebaseOrderManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "FirebaseOrderManager"
        private const val ORDERS_COLLECTION = "orders"
    }

    data class DetailedRatings(
        val qualityRating: Int? = null,
        val punctualityRating: Int? = null,
        val communicationRating: Int? = null,
        val cleanlinessRating: Int? = null
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
            
            // Preparar dados do pedido
            val order = orderData.copy(
                id = orderId,
                protocol = protocol,
                clientId = user.uid,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            
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
            Log.d(TAG, "Código do cliente: $clientCode | Código do prestador: $providerCode")
            
            Result.success(Pair(clientCode, providerCode))
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gerar códigos de verificação: ${e.message}")
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
                
                // Obter comissão do prestador e ID do prestador
                val providerCommission = snap.getDouble("providerCommission") ?: 0.0
                val assignedProvider = snap.getString("assignedProvider")
                
                // Ler dados do prestador ANTES de fazer qualquer escrita
                var currentEarnings = 0.0
                if (assignedProvider != null && providerCommission > 0) {
                    val providerRef = db.collection("providers").document(assignedProvider)
                    val providerSnap = tx.get(providerRef)
                    currentEarnings = providerSnap.getDouble("totalEarnings") ?: 0.0
                }
                
                // AGORA TODAS AS ESCRITAS
                // Código correto! Finalizar o pedido
                val updates = mapOf(
                    "status" to OrderData.STATUS_COMPLETED,
                    "completedAt" to Timestamp.now(),
                    "providerCompletionConfirmed" to true,
                    "clientCompletionConfirmed" to true,
                    "updatedAt" to Timestamp.now()
                )
                
                tx.update(docRef, updates)
                
                // Adicionar comissão ao lucro total do prestador
                if (assignedProvider != null && providerCommission > 0) {
                    val providerRef = db.collection("providers").document(assignedProvider)
                    val newEarnings = currentEarnings + providerCommission
                    
                    tx.update(providerRef, mapOf(
                        "totalEarnings" to newEarnings,
                        "completedJobs" to com.google.firebase.firestore.FieldValue.increment(1),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    ))
                    
                    Log.d(TAG, "💰 Comissão de R$ $providerCommission adicionada ao prestador $assignedProvider. Novo total: R$ $newEarnings")
                }
            }.await()
            
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

                // Ler dados do prestador ANTES de fazer qualquer escrita
                var currentEarnings = 0.0
                val providerCommission = snap.getDouble("providerCommission") ?: 0.0
                val assignedProvider = snap.getString("assignedProvider")
                
                if (newClient && newProvider && assignedProvider != null && providerCommission > 0) {
                    val providerRef = db.collection("providers").document(assignedProvider)
                    val providerSnap = tx.get(providerRef)
                    currentEarnings = providerSnap.getDouble("totalEarnings") ?: 0.0
                }

                // AGORA TODAS AS ESCRITAS
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
                    
                    // Adicionar comissão ao lucro total do prestador quando ambos confirmarem
                    if (assignedProvider != null && providerCommission > 0) {
                        val providerRef = db.collection("providers").document(assignedProvider)
                        val newEarnings = currentEarnings + providerCommission
                        
                        tx.update(providerRef, mapOf(
                            "totalEarnings" to newEarnings,
                            "completedJobs" to com.google.firebase.firestore.FieldValue.increment(1),
                            "updatedAt" to com.google.firebase.Timestamp.now()
                        ))
                        
                        Log.d(TAG, "💰 Comissão de R$ $providerCommission adicionada ao prestador $assignedProvider. Novo total: R$ $newEarnings")
                    }
                }

                tx.update(docRef, updates)
            }.await()

            Log.d(TAG, "Confirmação registrada ($actor): $orderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro na confirmação de conclusão: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Cancela um pedido (método simplificado)
     */
    suspend fun cancelOrder(orderId: String): Result<Unit> {
        return updateOrderStatus(orderId, OrderData.STATUS_CANCELLED)
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
        detailedRatings: DetailedRatings = DetailedRatings()
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

                transaction.update(orderRef, updates)
            }.await()

            // Recalcula a nota pública usando os pedidos reais avaliados do prestador.
            providerIdToUpdate?.takeIf { it.isNotBlank() }?.let { providerId ->
                updateProviderAverageRatingFromOrders(providerId)
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
    
    /**
     * Listener em tempo real para mudanças nos pedidos
     */
    fun listenToUserOrders(onOrdersChanged: (List<OrderData>) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            Log.e(TAG, "Usuário não autenticado")
            return
        }
        
        db.collection(ORDERS_COLLECTION)
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
     * Listener em tempo real para um pedido específico
     */
    fun listenToOrder(orderId: String, onOrderChanged: (OrderData?) -> Unit) {
        db.collection(ORDERS_COLLECTION)
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
            
            // Se cancelado pelo cliente, adicionar status de reembolso
            val updates = mutableMapOf<String, Any>(
                "status" to OrderData.STATUS_CANCELLED,
                "cancelledAt" to Timestamp.now(),
                "cancelledBy" to cancelledBy,
                "cancellationReason" to reason,
                "updatedAt" to Timestamp.now()
            )
            
            // Se cancelado pelo cliente, marcar como aguardando reembolso
            if (cancelledBy == "client") {
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
