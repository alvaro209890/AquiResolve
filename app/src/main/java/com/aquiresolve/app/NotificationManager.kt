package com.aquiresolve.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay
import java.util.*

/**
 * Gerenciador de notificações - Simula notificações push
 * 
 * Gerencia:
 * - Criação de canais de notificação
 * - Envio de notificações locais
 * - Diferentes tipos de notificação
 * - Histórico de notificações
 */
object NotificationManager {
    
    // Constantes para canais de notificação
    const val CHANNEL_ORDERS = "orders"
    const val CHANNEL_CHAT = "chat"
    const val CHANNEL_GENERAL = "general"
    
    // IDs de notificação
    private var notificationId = 1000
    
    // Histórico de notificações
    private val notificationHistory = mutableListOf<NotificationData>()
    
    /**
     * Dados de uma notificação
     */
    data class NotificationData(
        val id: String,
        val title: String,
        val message: String,
        val type: NotificationType,
        val targetId: String? = null, // ID do pedido, chat, etc.
        val createdAt: Date = Date(),
        val isRead: Boolean = false
    )
    
    /**
     * Tipos de notificação
     */
    enum class NotificationType {
        NEW_ORDER,           // Novo pedido para prestador
        ORDER_STATUS,        // Mudança de status do pedido
        NEW_QUOTE,          // Nova cotação para cliente
        CHAT_MESSAGE,       // Nova mensagem no chat
        RATING_RECEIVED,    // Nova avaliação recebida
        PAYMENT_RECEIVED,   // Pagamento recebido
        REMINDER,           // Lembrete de agendamento
        GENERAL            // Notificação geral
    }
    
    /**
     * Inicializa os canais de notificação
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Canal para pedidos
            val ordersChannel = NotificationChannel(
                CHANNEL_ORDERS,
                "Pedidos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações sobre pedidos e cotações"
                enableVibration(true)
                enableLights(true)
            }
            
            // Canal para chat
            val chatChannel = NotificationChannel(
                CHANNEL_CHAT,
                "Chat",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações de mensagens"
                enableVibration(true)
            }
            
            // Canal geral
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "Geral",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificações gerais do app"
            }
            
            notificationManager.createNotificationChannels(
                listOf(ordersChannel, chatChannel, generalChannel)
            )
        }
    }
    
    /**
     * Envia notificação de novo pedido
     */
    suspend fun sendNewOrderNotification(
        context: Context,
        orderId: String,
        serviceType: String,
        clientName: String
    ) {
        val title = "Novo Pedido Recebido"
        val message = "Pedido de $serviceType de $clientName"
        
        sendNotification(
            context = context,
            title = title,
            message = message,
            type = NotificationType.NEW_ORDER,
            targetId = orderId,
            channelId = CHANNEL_ORDERS,
            intent = Intent(context, OrderDetailsActivity::class.java).apply {
                putExtra("order_id", orderId)
            }
        )
    }
    
    /**
     * Envia notificação de mudança de status
     */
    suspend fun sendOrderStatusNotification(
        context: Context,
        orderId: String,
        status: String,
        isClient: Boolean
    ) {
        val title = "Status do Pedido Atualizado"
        val message = "Seu pedido foi $status"
        
        val targetActivity = if (isClient) {
            OrderDetailsActivity::class.java
        } else {
            OrderDetailsActivity::class.java
        }
        
        sendNotification(
            context = context,
            title = title,
            message = message,
            type = NotificationType.ORDER_STATUS,
            targetId = orderId,
            channelId = CHANNEL_ORDERS,
            intent = Intent(context, targetActivity).apply {
                putExtra("order_id", orderId)
            }
        )
    }
    
    /**
     * Envia notificação de nova cotação
     */
    suspend fun sendNewQuoteNotification(
        context: Context,
        orderId: String,
        providerName: String,
        price: Double
    ) {
        val title = "Nova Cotação Recebida"
        val message = "$providerName enviou uma cotação de R$ %.2f".format(price).replace(".", ",")
        
        sendNotification(
            context = context,
            title = title,
            message = message,
            type = NotificationType.NEW_QUOTE,
            targetId = orderId,
            channelId = CHANNEL_ORDERS,
            intent = Intent(context, OrderDetailsActivity::class.java).apply {
                putExtra("order_id", orderId)
            }
        )
    }
    
    /**
     * Envia notificação de nova mensagem
     */
    suspend fun sendChatMessageNotification(
        context: Context,
        orderId: String,
        senderName: String,
        message: String
    ) {
        val title = "Nova Mensagem de $senderName"
        val shortMessage = if (message.length > 50) {
            message.substring(0, 47) + "..."
        } else {
            message
        }
        
        sendNotification(
            context = context,
            title = title,
            message = shortMessage,
            type = NotificationType.CHAT_MESSAGE,
            targetId = orderId,
            channelId = CHANNEL_CHAT,
            intent = Intent(context, OrderDetailsActivity::class.java).apply {
                putExtra("order_id", orderId)
            }
        )
    }
    
    /**
     * Envia notificação de nova avaliação
     */
    suspend fun sendRatingNotification(
        context: Context,
        orderId: String,
        clientName: String,
        rating: Int
    ) {
        val title = "Nova Avaliação Recebida"
        val message = "$clientName avaliou seu serviço com $rating estrelas"
        
        sendNotification(
            context = context,
            title = title,
            message = message,
            type = NotificationType.RATING_RECEIVED,
            targetId = orderId,
            channelId = CHANNEL_GENERAL,
            intent = Intent(context, OrderDetailsActivity::class.java).apply {
                putExtra("order_id", orderId)
            }
        )
    }
    
    /**
     * Envia notificação de pagamento
     */
    suspend fun sendPaymentNotification(
        context: Context,
        orderId: String,
        amount: Double
    ) {
        val title = "Pagamento Recebido"
        val message = "R$ %.2f recebido pelo serviço".format(amount).replace(".", ",")
        
        sendNotification(
            context = context,
            title = title,
            message = message,
            type = NotificationType.PAYMENT_RECEIVED,
            targetId = orderId,
            channelId = CHANNEL_GENERAL,
            intent = Intent(context, OrderDetailsActivity::class.java).apply {
                putExtra("order_id", orderId)
            }
        )
    }
    
    /**
     * Envia notificação de lembrete
     */
    suspend fun sendReminderNotification(
        context: Context,
        orderId: String,
        serviceType: String,
        scheduledTime: String
    ) {
        val title = "Lembrete de Agendamento"
        val message = "Serviço de $serviceType agendado para $scheduledTime"
        
        sendNotification(
            context = context,
            title = title,
            message = message,
            type = NotificationType.REMINDER,
            targetId = orderId,
            channelId = CHANNEL_GENERAL,
            intent = Intent(context, OrderDetailsActivity::class.java).apply {
                putExtra("order_id", orderId)
            }
        )
    }
    
    /**
     * Envia uma notificação genérica
     */
    private suspend fun sendNotification(
        context: Context,
        title: String,
        message: String,
        type: NotificationType,
        targetId: String? = null,
        channelId: String = CHANNEL_GENERAL,
        intent: Intent? = null
    ) {
        // Simular delay de rede
        delay(500)
        
        // Criar PendingIntent
        val pendingIntent = if (intent != null) {
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }
        
        // Construir notificação
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply {
                if (pendingIntent != null) {
                    setContentIntent(pendingIntent)
                }
            }
            .build()
        
        // Enviar notificação
        try {
            NotificationManagerCompat.from(context).notify(notificationId++, notification)
            
            // Salvar no histórico
            val notificationData = NotificationData(
                id = "notification_${System.currentTimeMillis()}",
                title = title,
                message = message,
                type = type,
                targetId = targetId
            )
            notificationHistory.add(notificationData)
            
        } catch (e: SecurityException) {
            // Permissão de notificação não concedida
            println("Erro ao enviar notificação: ${e.message}")
        }
    }
    
    /**
     * Obtém histórico de notificações
     */
    fun getNotificationHistory(): List<NotificationData> {
        return notificationHistory.sortedByDescending { it.createdAt }
    }
    
    /**
     * Marca notificação como lida
     */
    fun markAsRead(notificationId: String) {
        notificationHistory.find { it.id == notificationId }?.let {
            val index = notificationHistory.indexOf(it)
            notificationHistory[index] = it.copy(isRead = true)
        }
    }
    
    /**
     * Obtém notificações não lidas
     */
    fun getUnreadNotifications(): List<NotificationData> {
        return notificationHistory.filter { !it.isRead }
    }
    
    /**
     * Limpa histórico de notificações
     */
    fun clearHistory() {
        notificationHistory.clear()
    }
} 
