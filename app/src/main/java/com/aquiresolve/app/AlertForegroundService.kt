package com.aquiresolve.app

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "Foreground service iniciado")
            } catch (e: Exception) {
                // Não propaga: o alerta sonoro não pode depender do FGS subir.
                Log.e(TAG, "Falha ao agendar foreground service: ${e.message}", e)
            }
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ProviderOrdersActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("AquiResolve")
            .setContentText("Monitorando novos pedidos...")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        // Em API 29+ passamos o tipo explicitamente (dataSync). Envolto em try/catch:
        // se o sistema recusar o FGS (ex.: permissão/limite), NÃO derrubamos o processo —
        // o som contínuo do NewOrderSoundHelper segue tocando mesmo sem o FGS.
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isRunning = true
            Log.d(TAG, "Service iniciado em foreground")
        } catch (e: Exception) {
            isRunning = false
            Log.e(TAG, "Falha ao iniciar foreground service (som continua sem FGS): ${e.message}", e)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        Log.d(TAG, "Service destruído")
        super.onDestroy()
    }
}
