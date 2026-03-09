package com.aquiresolve.app

import android.content.Context
import android.content.Intent

/**
 * ChatManager - Gerenciador para navegação entre telas de chat
 * 
 * Responsável por:
 * - Determinar qual tela de chat abrir baseado no tipo de usuário
 * - Preparar os dados necessários para cada tela
 * - Facilitar a navegação entre cliente e prestador
 */
object ChatManager {

    /**
     * Abre a tela de chat apropriada baseada no tipo de usuário
     * 
     * @param context Contexto da aplicação
     * @param userType Tipo do usuário ("client" ou "provider")
     * @param orderId ID do pedido/serviço
     * @param otherUserId ID do outro usuário (cliente ou prestador)
     * @param otherUserName Nome do outro usuário
     * @param otherUserPhoto Foto do outro usuário (opcional)
     * @param orderTitle Título do serviço
     * @param orderDescription Descrição do serviço (apenas para prestador)
     * @param orderBudget Orçamento do serviço (apenas para prestador)
     */
    fun openChat(
        context: Context,
        userType: String,
        orderId: String,
        otherUserId: String,
        otherUserName: String,
        otherUserPhoto: String? = null,
        orderTitle: String? = null,
        orderDescription: String? = null,
        orderBudget: String? = null
    ) {
        val intent = when (userType.lowercase()) {
            "client" -> {
                Intent(context, ClientChatActivity::class.java).apply {
                    putExtra("order_id", orderId)
                    putExtra("provider_id", otherUserId)
                    putExtra("provider_name", otherUserName)
                    putExtra("provider_photo", otherUserPhoto)
                    putExtra("order_title", orderTitle)
                }
            }
            "provider" -> {
                Intent(context, ProviderChatActivity::class.java).apply {
                    putExtra("order_id", orderId)
                    putExtra("client_id", otherUserId)
                    putExtra("client_name", otherUserName)
                    putExtra("client_photo", otherUserPhoto)
                    putExtra("order_title", orderTitle)
                    putExtra("order_description", orderDescription)
                    putExtra("order_budget", orderBudget)
                }
            }
            else -> {
                // Fallback seguro: ChatActivity agora redireciona para o chat real com base no pedido.
                Intent(context, ChatActivity::class.java).apply {
                    putExtra("order_id", orderId)
                }
            }
        }
        
        context.startActivity(intent)
    }

    /**
     * Abre o chat do cliente
     */
    fun openClientChat(
        context: Context,
        orderId: String,
        providerId: String,
        providerName: String,
        providerPhoto: String? = null,
        orderTitle: String? = null
    ) {
        openChat(
            context = context,
            userType = "client",
            orderId = orderId,
            otherUserId = providerId,
            otherUserName = providerName,
            otherUserPhoto = providerPhoto,
            orderTitle = orderTitle
        )
    }

    /**
     * Abre o chat do prestador
     */
    fun openProviderChat(
        context: Context,
        orderId: String,
        clientId: String,
        clientName: String,
        clientPhoto: String? = null,
        orderTitle: String? = null,
        orderDescription: String? = null,
        orderBudget: String? = null
    ) {
        openChat(
            context = context,
            userType = "provider",
            orderId = orderId,
            otherUserId = clientId,
            otherUserName = clientName,
            otherUserPhoto = clientPhoto,
            orderTitle = orderTitle,
            orderDescription = orderDescription,
            orderBudget = orderBudget
        )
    }

    /**
     * Exemplo de uso para cliente
     */
    fun openChatFromClientOrder(
        context: Context,
        orderId: String,
        providerId: String,
        providerName: String,
        orderTitle: String
    ) {
        openClientChat(
            context = context,
            orderId = orderId,
            providerId = providerId,
            providerName = providerName,
            orderTitle = orderTitle
        )
    }

    /**
     * Exemplo de uso para prestador
     */
    fun openChatFromProviderOrder(
        context: Context,
        orderId: String,
        clientId: String,
        clientName: String,
        orderTitle: String,
        orderDescription: String,
        orderBudget: String
    ) {
        openProviderChat(
            context = context,
            orderId = orderId,
            clientId = clientId,
            clientName = clientName,
            orderTitle = orderTitle,
            orderDescription = orderDescription,
            orderBudget = orderBudget
        )
    }
}
