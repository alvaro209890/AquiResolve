package com.aquiresolve.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Foreground Service leve que mantém o processo do app vivo enquanto
 * há alertas sonoros de novos pedidos ativos.
 *
 * Isso garante que o Firestore listener do ProviderNewOrderAlertManager
 * continue funcionando e que o MediaPlayer do NewOrderSoundHelper
 * não seja interrompido pelo sistema matar o processo em background.
 */
class AlertForegroundService : Service() {

    companion object {
        const val TAG = "AlertForegroundService"
        const val CHANNEL_ID = "alert_channel"
        private const val NOTIFICATION_ID = 40030
        private var isRunning = false

        fun isActive(): Boolean = isRunning

        fun start(context: android.content.Context) {
            if (isRunning) return
            val intent = Intent(context, AlertForegroundService::class.java)
            context.startForegroundService(intent)
            Log.d(TAG, "Foreground service iniciado")
        }

        fun stop(context: android.content.Context) {
            if (!isRunning) return
            val intent = Intent(context, AlertForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service criado")

        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            "Alertas de Pedidos",
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantém o app ativo para alertas de novos pedidos"
            setSound(null, null) // sem som neste canal
            enableVibration(false)
        }
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ProviderOrdersActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("AquiResolve")
            .setContentText("Monitorando novos pedidos...")
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        isRunning = true
        Log.d(TAG, "Service iniciado em foreground")

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        Log.d(TAG, "Service destruído")
        super.onDestroy()
    }
}
