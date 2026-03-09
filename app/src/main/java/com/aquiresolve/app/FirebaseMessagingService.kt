package com.aquiresolve.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
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
                val notificationManager = FirebaseNotificationManager(this)
                CoroutineScope(Dispatchers.IO).launch {
                    notificationManager.saveUserToken(currentUser.uid)
                }
            }
        } catch (_: Exception) {
            // ignorar erros silenciosamente
        }
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Prioridade: dados da mensagem > notificação
        if (remoteMessage.data.isNotEmpty()) {
            val title = remoteMessage.data["title"] ?: "Nova notificação"
            val message = remoteMessage.data["message"] ?: "Você tem uma nova notificação"
            val orderId = remoteMessage.data["order_id"] ?: remoteMessage.data["orderId"]
            val type = remoteMessage.data["type"] // order, chat, payment
            sendNotification(title, message, orderId, type)
        } else {
            remoteMessage.notification?.let { notification ->
                val title = notification.title ?: "Nova notificação"
                val message = notification.body ?: "Você tem uma nova notificação"
                sendNotification(title, message, null, null)
            }
        }
    }
    
    private fun sendRegistrationToServer(@Suppress("UNUSED_PARAMETER") token: String) {
        // Implementação intencionalmente vazia para evitar persistência local desnecessária.
    }
    
    private fun sendNotification(title: String, message: String, orderId: String?, type: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val privacyManager = FirebasePrivacyManager(this@FirebaseMessagingService)
                
                // Verificar se notificações estão habilitadas
                if (!privacyManager.isSettingEnabled("notifications_enabled")) return@launch
                
                // Filtrar por tipo de notificação (quando type vem no payload)
                when (type?.lowercase()) {
                    "order" -> if (!privacyManager.isSettingEnabled("order_notifications_enabled")) return@launch
                    "chat" -> if (!privacyManager.isSettingEnabled("chat_notifications_enabled")) return@launch
                    "payment" -> if (!privacyManager.isSettingEnabled("payment_notifications_enabled")) return@launch
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
                    displayNotification(title, message, orderId, soundEnabled, vibrationEnabled, type)
                }
            } catch (e: Exception) {
                runOnMainThread {
                    displayNotification(title, message, orderId, true, true, type)
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
        type: String?
    ) {
        val intent = if (orderId != null) {
            Intent(this, OrderDetailsActivity::class.java).apply {
                putExtra("order_id", orderId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        } else {
            Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val isOrderNotification = type.equals("order", ignoreCase = true) ||
            title.contains("pedido", ignoreCase = true) ||
            message.contains("pedido", ignoreCase = true)

        if (isOrderNotification && soundEnabled) {
            NewOrderSoundHelper.playNewOrderSound(applicationContext)
        }

        val defaultSoundUri = if (soundEnabled && !isOrderNotification) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            null
        }
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 250, 250, 250) else longArrayOf(0))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Criar canal de notificação para Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Enviar notificação
        notificationManager.notify(0, notificationBuilder.build())
    }
} 
