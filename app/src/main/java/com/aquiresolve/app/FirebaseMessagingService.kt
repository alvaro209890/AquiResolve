package com.aquiresolve.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aquiresolve.app.utils.NewOrderSoundHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class FirebaseMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val CHANNEL_ID = "default_channel"
        private const val CHANNEL_NAME = "Default Channel"
        private const val CHANNEL_DESCRIPTION = "Canal padrão para notificações"
        
        // Canal dedicado para mensagens (som garantido, não agrupado com outras notificações)
        private const val CHANNEL_MSG_ID = "messages_channel"
        private const val CHANNEL_MSG_NAME = "Mensagens"
        private const val CHANNEL_MSG_DESCRIPTION = "Notificações de mensagens da Central e chat"
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Enviar token para o servidor
        sendRegistrationToServer(token)
        // Se houver usuário logado, salvar token no Firestore
        try {
            val auth = FirebaseConfig.getAuth()
            val currentUser = auth.currentUser
            if (currentUser != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    FirebaseNotificationManager.saveUserToken(currentUser.uid)
                }
            }
        } catch (_: Exception) {
            // ignorar erros silenciosamente
        }
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Prioridade: mescla notification + data. O backend manda title/body no
        // campo `notification` e campos extras (type, orderId, providerId, etc.) no `data`.
        val notif = remoteMessage.notification
        val title = notif?.title
            ?: remoteMessage.data["title"]
            ?: "Nova notificação"
        val message = notif?.body
            ?: remoteMessage.data["message"]
            ?: remoteMessage.data["body"]
            ?: "Você tem uma nova notificação"
        val orderId = remoteMessage.data["order_id"]
            ?: remoteMessage.data["orderId"]
        val type = remoteMessage.data["type"]
        val providerId = remoteMessage.data["providerId"]
        val clientId = remoteMessage.data["clientId"]
        val messageId = remoteMessage.data["messageId"]
        
        sendNotification(title, message, orderId, type, providerId, clientId)
    }
    
    private fun sendRegistrationToServer(@Suppress("UNUSED_PARAMETER") token: String) {
        // Implementação intencionalmente vazia para evitar persistência local desnecessária.
    }
    
    private fun sendNotification(
        title: String,
        message: String,
        orderId: String?,
        type: String?,
        providerId: String? = null,
        clientId: String? = null
    ) {
        val isMessageType = type.equals("provider_message", ignoreCase = true)
            || type.equals("central_message", ignoreCase = true)
            || type.equals("chat", ignoreCase = true)
            || type.equals("chat_message", ignoreCase = true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val privacyManager = FirebasePrivacyManager(this@FirebaseMessagingService)
                
                // Verificar se notificações estão habilitadas
                if (!privacyManager.isSettingEnabled("notifications_enabled")) return@launch
                
                // Filtrar por tipo de notificação
                when {
                    type.equals("order", ignoreCase = true) ->
                        if (!privacyManager.isSettingEnabled("order_notifications_enabled")) return@launch
                    isMessageType ->
                        if (!privacyManager.isSettingEnabled("chat_notifications_enabled")) return@launch
                    type.equals("payment", ignoreCase = true) ->
                        if (!privacyManager.isSettingEnabled("payment_notifications_enabled")) return@launch
                }
                
                // Verificar horário silencioso
                if (privacyManager.isSettingEnabled("quiet_hours_enabled")) {
                    val startStr = privacyManager.getSettingString("quiet_hours_start", "22:00")
                    val endStr = privacyManager.getSettingString("quiet_hours_end", "07:00")
                    if (isInQuietHours(startStr, endStr)) return@launch
                }
                
                val soundEnabled = privacyManager.isSettingEnabled("notification_sound_enabled")
                val vibrationEnabled = privacyManager.isSettingEnabled("notification_vibration_enabled")
                
                runOnMainThread {
                    displayNotification(
                        title, message, orderId,
                        soundEnabled, vibrationEnabled,
                        type, isMessageType
                    )
                }
            } catch (e: Exception) {
                runOnMainThread {
                    displayNotification(
                        title, message, orderId,
                        true, true,
                        type, isMessageType
                    )
                }
            }
        }
    }
    
    private fun runOnMainThread(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }
    
    private fun isInQuietHours(startStr: String, endStr: String): Boolean {
        return try {
            val now = Calendar.getInstance()
            val (startH, startM) = startStr.split(":").map { it.toIntOrNull() ?: 0 }
            val (endH, endM) = endStr.split(":").map { it.toIntOrNull() ?: 0 }
            val startCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, startH); set(Calendar.MINUTE, startM) }
            val endCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, endH); set(Calendar.MINUTE, endM) }
            val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val startMinutes = startCal.get(Calendar.HOUR_OF_DAY) * 60 + startCal.get(Calendar.MINUTE)
            val endMinutes = endCal.get(Calendar.HOUR_OF_DAY) * 60 + endCal.get(Calendar.MINUTE)
            if (startMinutes > endMinutes) { // Ex: 22:00 - 07:00 (cruza meia-noite)
                nowMinutes >= startMinutes || nowMinutes < endMinutes
            } else {
                nowMinutes >= startMinutes && nowMinutes < endMinutes
            }
        } catch (_: Exception) { false }
    }
    
    private fun displayNotification(
        title: String,
        message: String,
        orderId: String?,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        type: String?,
        isMessageType: Boolean
    ) {
        // Escolhe qual Activity abrir ao tocar na notificação
        val intent = if (type.equals("chat_message", ignoreCase = true)) {
            // Mensagem de chat cliente↔prestador → abrir ChatActivity com o pedido
            Intent(this, ChatActivity::class.java).apply {
                putExtra("order_id", orderId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        } else if (orderId != null) {
            Intent(this, OrderDetailsActivity::class.java).apply {
                putExtra("order_id", orderId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        } else if (type.equals("provider_message", ignoreCase = true)) {
            // Abrir chat do prestador (ProviderChatActivity ou HomeActivity como fallback)
            Intent(this, HomeActivity::class.java).apply {
                putExtra("open_provider_chat", true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        } else if (type.equals("central_message", ignoreCase = true)) {
            // Abrir chat do cliente com a Central
            Intent(this, ClientCentralChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        } else {
            Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val isOrderNotification = type.equals("order", ignoreCase = true) ||
            title.contains("pedido", ignoreCase = true) ||
            message.contains("pedido", ignoreCase = true)

        // Para pedidos novos: dispara o alerta contínuo (loop) + Foreground Service
        // Isso garante que o prestador ouça o som mesmo com o app fechado
        if (isOrderNotification && soundEnabled && orderId != null) {
            // Inicia o Foreground Service pra manter o processo vivo
            AlertForegroundService.start(applicationContext)

            // Som contínuo em loop — só para quando aceitar/rejeitar
            Handler(Looper.getMainLooper()).post {
                NewOrderSoundHelper.startContinuousPlay(applicationContext, orderId)
            }

            // Reativa o listener Firestore pra monitorar novos pedidos e registra
            // este pedido para que o som PARE sozinho quando algum prestador aceitar
            // (sem isso, um som iniciado por FCM tocaria sem parar após o aceite).
            val alarmManager = ProviderNewOrderAlertManager
            Handler(Looper.getMainLooper()).post {
                alarmManager.refreshMonitoring()
                alarmManager.watchAlertedOrders(setOf(orderId))
            }
        } else if (isOrderNotification && soundEnabled) {
            NewOrderSoundHelper.playNewOrderSound(applicationContext)
        }

        // Para mensagens, sempre usa o canal dedicado com som garantido
        val (channelId, channelName, channelDesc, soundUri) = if (isMessageType) {
            // Canal de mensagens: som SEMPRE toca (ignora soundEnabled global).
            // O usuário controla via chat_notifications_enabled e quiet_hours.
            Tuple4(
                CHANNEL_MSG_ID,
                CHANNEL_MSG_NAME,
                CHANNEL_MSG_DESCRIPTION,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )
        } else {
            Tuple4(
                CHANNEL_ID,
                CHANNEL_NAME,
                CHANNEL_DESCRIPTION,
                if (soundEnabled && !isOrderNotification)
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                else null
            )
        }
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 250, 250, 250) else longArrayOf(0))
            .setContentIntent(pendingIntent)
            .setPriority(if (isMessageType) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Criar canais de notificação para Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal padrão
            val defaultChannel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = CHANNEL_DESCRIPTION }
            notificationManager.createNotificationChannel(defaultChannel)

            // Canal dedicado para mensagens (importância alta = som + heads-up)
            val msgChannel = NotificationChannel(
                CHANNEL_MSG_ID, CHANNEL_MSG_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_MSG_DESCRIPTION
                enableVibration(vibrationEnabled)
            }
            notificationManager.createNotificationChannel(msgChannel)
        }
        
        // ID único por tipo para não agrupar mensagens com outras notificações
        val notifyId = if (isMessageType) System.currentTimeMillis().toInt() else 0
        notificationManager.notify(notifyId, notificationBuilder.build())
    }
    
    /** Tuple simples para retornar múltiplos valores em displayNotification */
    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
} 
