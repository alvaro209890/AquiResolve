// TEMPORARIAMENTE COMENTADO PARA PERMITIR BUILD
/*
package com.example.loginapp

import android.util.Log
import com.example.loginapp.models.OrderData
import com.example.loginapp.models.OrderResult
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Gerenciador de pedidos - Simula operações de banco de dados
 * 
 * Gerencia:
 * - Criação de pedidos
 * - Distribuição para prestadores
 * - Cotações
 * - Status dos pedidos
 */
object OrderManager {
    
    // Dados simulados
    private val mockOrders = mutableMapOf<String, OrderData>()
    private val mockQuotes = mutableMapOf<String, QuoteData>()
    
    // Preços tabelados para serviços simples (por região)
    private val fixedPrices = mapOf(
        "Elétrica" to mapOf(
            "Troca de chuveiro" to 80.0,
            "Instalação de tomada" to 45.0,
            "Reparo de disjuntores" to 60.0,
            "Instalação de ventilador" to 70.0
        ),
        "Encanador" to mapOf(
            "Troca de torneira" to 65.0,
            "Desentupimento de pia" to 55.0,
            "Reparo de vazamento" to 75.0,
            "Instalação de chuveiro" to 90.0
        ),
        "Limpeza" to mapOf(
            "Limpeza residencial" to 120.0,
            "Limpeza comercial" to 200.0,
            "Limpeza pós-obra" to 150.0
        ),
        "Jardinagem" to mapOf(
            "Poda de árvores" to 100.0,
            "Corte de grama" to 80.0,
            "Manutenção de jardim" to 90.0
        )
    )
    
    /**
     * Cria um novo pedido
     */
    suspend fun createOrder(request: CreateOrderRequest): OrderResult {
        // delay(1500) // Simular delay de rede
        
        val currentUser = LocalAuthManager.currentUser
        if (currentUser == null) {
            return OrderResult.Error("Usuário não autenticado")
        }
        
        // Validar dados obrigatórios
        if (request.serviceType.isEmpty() || request.serviceNiche.isEmpty()) {
            return OrderResult.Error("Tipo e nicho de serviço são obrigatórios")
        }
        
        if (request.description.isEmpty()) {
            return OrderResult.Error("Descrição é obrigatória")
        }
        
        if (request.cep.isEmpty() || request.address.isEmpty()) {
            return OrderResult.Error("CEP e endereço são obrigatórios")
        }
        
        // Determinar se é serviço simples ou complexo
        val isSimpleService = isSimpleService(request.serviceNiche, request.description)
        val serviceType = if (isSimpleService) "SIMPLE" else "COMPLEX"
        
        // Calcular preço para serviços simples
        val fixedPrice = if (isSimpleService) {
            calculateFixedPrice(request.serviceNiche, request.description)
        } else null
        
        val distanceFee = if (isSimpleService) {
            calculateDistanceFee(request.cep)
        } else null
        
        // Criar pedido
        val order = OrderData(
            id = "order_${System.currentTimeMillis()}",
            protocol = com.example.loginapp.utils.ProtocolGenerator.generateProtocol(),
            clientId = currentUser.id,
            clientName = currentUser.fullName,
            clientEmail = currentUser.email,
            clientPhone = currentUser.phone ?: "",
            serviceType = serviceType,
            serviceNiche = request.serviceNiche,
            description = request.description,
            images = request.images,
            cep = request.cep,
            address = request.address,
            complement = request.complement,
            preferredDate = request.preferredDate,
            preferredTime = request.preferredTime,
            fixedPrice = fixedPrice,
            distanceFee = distanceFee
        )
        
        // Salvar pedido
        mockOrders[order.id] = order
        
        // Distribuir para prestadores
        if (isSimpleService) {
            distributeSimpleOrder(order)
        } else {
            distributeComplexOrder(order)
        }
        
        // Enviar notificação para prestadores disponíveis
        sendNotificationsToProviders(order)
        
        return OrderResult.Success
    }
    
    /**
     * Verifica se é um serviço simples baseado no nicho e descrição
     */
    private fun isSimpleService(niche: String, description: String): Boolean {
        val simpleKeywords = listOf(
            "troca", "instalação", "reparo", "desentupimento", 
            "limpeza", "poda", "corte", "manutenção"
        )
        
        val descriptionLower = description.lowercase()
        return simpleKeywords.any { keyword -> descriptionLower.contains(keyword) }
    }
    
    /**
     * Calcula preço fixo para serviços simples
     */
    private fun calculateFixedPrice(niche: String, description: String): Double {
        return when {
            niche == "Elétrica" && description.contains("chuveiro") -> 80.0
            niche == "Elétrica" && description.contains("tomada") -> 45.0
            niche == "Encanador" && description.contains("torneira") -> 65.0
            niche == "Encanador" && description.contains("desentupimento") -> 55.0
            niche == "Limpeza" -> 120.0
            niche == "Jardinagem" -> 90.0
            else -> 100.0 // Preço padrão
        }
    }
    
    /**
     * Calcula taxa de distância
     */
    private fun calculateDistanceFee(cep: String): Double {
        // Simulação: CEPs que começam com números maiores têm taxa
        val cepNumber = cep.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
        return if (cepNumber > 50000) 25.0 else 0.0
    }
    
    /**
     * Distribui pedido simples para prestadores
     */
    private suspend fun distributeSimpleOrder(order: OrderData) {
        // delay(1000) // Simular processamento
        
        // TODO: INTEGRAR COM ORDERDISTRIBUTIONMANAGER
        // Usar o sistema de distribuição inteligente
        val distributionResult = OrderDistributionManager.distributeOrder(order)
        
        when (distributionResult) {
            is OrderDistributionManager.DistributionResult.Assigned -> {
                // Pedido atribuído com sucesso
                val updatedOrder = order.copy(
                    status = OrderStatus.ASSIGNED,
                    assignedProviderId = distributionResult.providerId,
                    assignedProviderName = getProviderName(distributionResult.providerId)
                )
                mockOrders[order.id] = updatedOrder
            }
            is OrderDistributionManager.DistributionResult.Queued -> {
                // Pedido adicionado à fila
                val updatedOrder = order.copy(
                    status = OrderStatus.PENDING
                )
                mockOrders[order.id] = updatedOrder
            }
            is OrderDistributionManager.DistributionResult.Error -> {
                // Erro na distribuição
                println("Erro na distribuição: ${distributionResult.message}")
                val updatedOrder = order.copy(
                    status = OrderStatus.PENDING
                )
                mockOrders[order.id] = updatedOrder
            }
            else -> {
                // Pedido complexo ou outro status
                val updatedOrder = order.copy(
                    status = OrderStatus.PENDING
                )
                mockOrders[order.id] = updatedOrder
            }
        }
    }
    
    /**
     * Distribui pedido complexo para prestadores
     */
    private suspend fun distributeComplexOrder(order: OrderData) {
        // delay(1000) // Simular processamento
        
        // TODO: INTEGRAR COM ORDERDISTRIBUTIONMANAGER
        // Usar o sistema de distribuição inteligente para pedidos complexos
        val distributionResult = OrderDistributionManager.distributeOrder(order)
        
        when (distributionResult) {
            is OrderDistributionManager.DistributionResult.Success -> {
                // Pedido disponibilizado para prestadores
                val updatedOrder = order.copy(
                    status = OrderStatus.PENDING
                )
                mockOrders[order.id] = updatedOrder
            }
            is OrderDistributionManager.DistributionResult.Queued -> {
                // Pedido adicionado à fila
                val updatedOrder = order.copy(
                    status = OrderStatus.PENDING
                )
                mockOrders[order.id] = updatedOrder
            }
            is OrderDistributionManager.DistributionResult.Error -> {
                // Erro na distribuição
                println("Erro na distribuição: ${distributionResult.message}")
                val updatedOrder = order.copy(
                    status = OrderStatus.PENDING
                )
                mockOrders[order.id] = updatedOrder
            }
            else -> {
                // Outro status
                val updatedOrder = order.copy(
                    status = OrderStatus.PENDING
                )
                mockOrders[order.id] = updatedOrder
            }
        }
    }
    
    /**
     * Obtém pedidos de um cliente
     */
    suspend fun getClientOrders(clientId: String): List<OrderData> {
        // delay(500) // Simular delay de rede
        return mockOrders.values.filter { it.clientId == clientId }
            .sortedByDescending { it.createdAt }
    }
    
    /**
     * Obtém pedidos disponíveis para prestadores
     */
    suspend fun getAvailableOrdersForProvider(providerId: String): List<OrderData> {
        // delay(500) // Simular delay de rede
        
        val provider = LocalAuthManager.getAllVerifiedProviders().find { it.id == providerId }
        if (provider == null) return emptyList()
        
        return mockOrders.values.filter { order ->
            // Pedidos pendentes ou com cotações
            (order.status == OrderStatus.PENDING || order.status == OrderStatus.QUOTES_RECEIVED) &&
            // Do nicho do prestador
            provider.services.contains(order.serviceNiche) &&
            // Na região do prestador (simulação simples)
            order.cep.startsWith(provider.cep.take(3))
        }.sortedByDescending { it.createdAt }
    }
    
    /**
     * Cria uma cotação para um pedido
     */
    suspend fun createQuote(
        orderId: String,
        providerId: String,
        price: Double,
        description: String,
        estimatedTime: String
    ): OrderResult {
        // delay(1000) // Simular delay de rede
        
        val order = mockOrders[orderId] ?: return OrderResult.Error("Pedido não encontrado")
        val provider = LocalAuthManager.getAllVerifiedProviders().find { it.id == providerId }
            ?: return OrderResult.Error("Prestador não encontrado")
        
        if (order.status != OrderStatus.PENDING && order.status != OrderStatus.QUOTES_RECEIVED) {
            return OrderResult.Error("Pedido não está disponível para cotações")
        }
        
        val quote = QuoteData(
            id = "quote_${System.currentTimeMillis()}",
            orderId = orderId,
            providerId = providerId,
            providerName = provider.fullName,
            providerRating = 4.5, // Simulado
            price = price,
            description = description,
            estimatedTime = estimatedTime
        )
        
        // Salvar cotação
        mockQuotes[quote.id] = quote
        
        // Atualizar pedido
        val updatedQuotes = order.quotes + quote
        val updatedOrder = order.copy(
            quotes = updatedQuotes,
            status = OrderStatus.QUOTES_RECEIVED
        )
        mockOrders[orderId] = updatedOrder
        
        return OrderResult.Success
    }
    
    /**
     * Aceita uma cotação
     */
    suspend fun acceptQuote(orderId: String, quoteId: String): OrderResult {
        // delay(1000) // Simular delay de rede
        
        val order = mockOrders[orderId] ?: return OrderResult.Error("Pedido não encontrado")
        val quote = mockQuotes[quoteId] ?: return OrderResult.Error("Cotação não encontrada")
        
        // Atualizar cotação
        val updatedQuote = quote.copy(status = QuoteStatus.ACCEPTED)
        mockQuotes[quoteId] = updatedQuote
        
        // Atualizar pedido
        val updatedQuotes = order.quotes.map { 
            if (it.id == quoteId) updatedQuote else it.copy(status = QuoteStatus.REJECTED)
        }
        val updatedOrder = order.copy(
            quotes = updatedQuotes,
            selectedQuoteId = quoteId,
            status = OrderStatus.ASSIGNED,
            assignedProviderId = quote.providerId,
            assignedProviderName = quote.providerName
        )
        mockOrders[orderId] = updatedOrder
        
        return OrderResult.Success
    }
    
    /**
     * Obtém cotações de um pedido
     */
    suspend fun getOrderQuotes(orderId: String): List<QuoteData> {
        // delay(500) // Simular delay de rede
        return mockQuotes.values.filter { it.orderId == orderId }
            .sortedBy { it.createdAt }
    }
    
    /**
     * Atualiza status de um pedido
     */
    suspend fun updateOrderStatus(orderId: String, status: OrderStatus): OrderResult {
        // delay(500) // Simular delay de rede
        
        val order = mockOrders[orderId] ?: return OrderResult.Error("Pedido não encontrado")
        val updatedOrder = order.copy(
            status = status,
            updatedAt = Date()
        )
        mockOrders[orderId] = updatedOrder
        
        return OrderResult.Success
    }
    
    /**
     * Obtém estatísticas de pedidos
     */
    suspend fun getOrderStats(): Map<String, Int> {
        // delay(300) // Simular delay de rede
        
        return mapOf(
            "total" to mockOrders.size,
            "pending" to mockOrders.values.count { it.status == OrderStatus.PENDING },
            "assigned" to mockOrders.values.count { it.status == OrderStatus.ASSIGNED },
            "completed" to mockOrders.values.count { it.status == OrderStatus.COMPLETED }
        )
    }
    
    /**
     * Envia notificações para prestadores disponíveis
     */
    private suspend fun sendNotificationsToProviders(order: OrderData) {
        // TODO: INTEGRAR COM NOTIFICATIONMANAGER
        // Enviar notificações push para prestadores
        
        // Encontrar prestadores disponíveis na região
        val availableProviders = LocalAuthManager.findProvidersByRegionAndService(
            order.cep, order.serviceNiche
        )
        
        // Enviar notificação para cada prestador
        availableProviders.forEach { provider ->
            // Simular delay entre notificações
            // delay(100)
            
            // TODO: Implementar envio real de notificação
            // Por enquanto, apenas simular
            println("Notificação enviada para ${provider.fullName}: Novo pedido de ${order.serviceNiche}")
        }
    }
    
    /**
     * Obtém nome do prestador por ID
     */
    private fun getProviderName(providerId: String): String {
        // TODO: BUSCAR NO BANCO DE DADOS
        return "Prestador" // Simulado
    }
}
*/ 