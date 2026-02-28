package com.example.loginapp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.loginapp.models.OrderData
import com.example.loginapp.utils.NewOrderSoundHelper
import com.example.loginapp.utils.ServiceNicheCatalog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Listener global para alertar prestador sobre novos pedidos disponíveis,
 * mesmo quando ele não está na tela de pedidos.
 */
object ProviderNewOrderAlertManager {

    private const val TAG = "ProviderNewOrderAlert"
    private const val ALERT_NOTIFICATION_ID = 40021

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var appContext: Context? = null
    private var initialized = false
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    private var monitoredProviderId: String? = null
    private var ordersListener: ListenerRegistration? = null
    private var listenerStartJob: Job? = null

    private var hasInitialSnapshot = false
    private val knownAvailableOrderIds = mutableSetOf<String>()
    private var providerServicesNormalized: Set<String> = emptySet()

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true

        authStateListener = FirebaseAuth.AuthStateListener {
            refreshMonitoring()
        }
        authStateListener?.let { auth.addAuthStateListener(it) }

        refreshMonitoring()
    }

    fun refreshMonitoring() {
        val context = appContext ?: return
        val currentUser = auth.currentUser

        if (currentUser == null) {
            stopMonitoring("usuário deslogado")
            return
        }

        listenerStartJob?.cancel()
        listenerStartJob = scope.launch {
            attachListenerIfProviderApproved(context, currentUser.uid)
        }
    }

    fun shutdown() {
        authStateListener?.let { auth.removeAuthStateListener(it) }
        authStateListener = null
        stopMonitoring("shutdown")
        scope.cancel()
        initialized = false
    }

    private suspend fun attachListenerIfProviderApproved(context: Context, userId: String) {
        try {
            val providerDoc = firestore.collection("providers").document(userId).get().await()
            val userDoc = firestore.collection("users").document(userId).get().await()

            val hasProviderProfile = providerDoc.exists()
            val isProviderInUsers = userDoc.getString("userType")
                ?.trim()
                ?.lowercase(Locale.ROOT) == FirebaseAuthManager.USER_TYPE_PROVIDER

            val verificationStatus = (providerDoc.getString("verificationStatus")
                ?: userDoc.getString("verificationStatus"))
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .orEmpty()

            val isApprovedByStatus = verificationStatus in setOf(
                "approved", "aprovado", "verified", "verificado"
            )
            val isApprovedByBoolean =
                providerDoc.getBoolean("isVerified") == true ||
                    providerDoc.getBoolean("verified") == true ||
                    userDoc.getBoolean("isVerified") == true ||
                    userDoc.getBoolean("verified") == true

            if (!hasProviderProfile && !isProviderInUsers) {
                withContext(Dispatchers.Main) { stopMonitoring("usuário não é prestador") }
                return
            }

            if (!isApprovedByStatus && !isApprovedByBoolean) {
                withContext(Dispatchers.Main) { stopMonitoring("prestador não aprovado") }
                return
            }

            val rawServices = extractProviderServices(providerDoc, userDoc)
            val services = ServiceNicheCatalog.normalizeProviderServices(rawServices)

            Log.d(TAG, "Nichos monitorados para $userId: ${ServiceNicheCatalog.canonicalizeProviderServices(rawServices)}")

            withContext(Dispatchers.Main) {
                stopMonitoring("reiniciar listener")

                monitoredProviderId = userId
                providerServicesNormalized = services
                hasInitialSnapshot = false
                knownAvailableOrderIds.clear()

                ordersListener = firestore.collection("orders")
                    .whereIn(
                        "status",
                        listOf(
                            OrderData.STATUS_DISTRIBUTING,
                            OrderData.STATUS_PENDING,
                            "available",
                            OrderData.STATUS_DISTRIBUTING.uppercase(Locale.ROOT),
                            OrderData.STATUS_PENDING.uppercase(Locale.ROOT),
                            "AVAILABLE"
                        )
                    )
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Erro no listener global de pedidos: ${error.message}", error)
                            return@addSnapshotListener
                        }

                        val availableIds = snapshot?.documents
                            ?.mapNotNull { document ->
                                val order = try {
                                    document.toObject(OrderData::class.java)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Erro ao converter pedido ${document.id}: ${e.message}")
                                    null
                                } ?: return@mapNotNull null

                                if (matchesProviderService(order)) document.id else null
                            }
                            ?.toSet()
                            ?: emptySet()

                        if (!hasInitialSnapshot) {
                            hasInitialSnapshot = true
                            knownAvailableOrderIds.clear()
                            knownAvailableOrderIds.addAll(availableIds)
                            Log.d(TAG, "Snapshot inicial carregado com ${availableIds.size} pedidos")
                            return@addSnapshotListener
                        }

                        val newIds = availableIds - knownAvailableOrderIds
                        knownAvailableOrderIds.clear()
                        knownAvailableOrderIds.addAll(availableIds)

                        if (newIds.isNotEmpty()) {
                            notifyNewOrders(context, newIds.size)
                        }
                    }

                Log.d(TAG, "Listener global de novos pedidos ativo para prestador: $userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar listener global de pedidos: ${e.message}", e)
        }
    }

    private fun matchesProviderService(order: OrderData): Boolean {
        return ServiceNicheCatalog.matchesProviderServices(providerServicesNormalized, order)
    }

    private fun extractProviderServices(providerDoc: DocumentSnapshot, userDoc: DocumentSnapshot): List<String> {
        val providerServices = extractStringList(providerDoc, "services", "serviceTypes", "serviceNiches")
        if (providerServices.isNotEmpty()) return providerServices

        return extractStringList(userDoc, "services", "serviceTypes", "serviceNiches")
    }

    private fun extractStringList(document: DocumentSnapshot, vararg fields: String): List<String> {
        fields.forEach { field ->
            val value = document.get(field)
            when (value) {
                is List<*> -> {
                    val items = value
                        .mapNotNull { it as? String }
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (items.isNotEmpty()) return items
                }
                is String -> {
                    val items = value
                        .split(",", ";", "|")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (items.isNotEmpty()) return items
                }
            }
        }
        return emptyList()
    }

    private fun notifyNewOrders(context: Context, newCount: Int) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            try {
                NewOrderSoundHelper.playNewOrderSound(context)
                showHeadsUpNotification(context, newCount)
                Log.d(TAG, "Alerta de novo pedido disparado ($newCount)")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao disparar alerta de novo pedido: ${e.message}", e)
            }
        }
    }

    private fun showHeadsUpNotification(context: Context, count: Int) {
        val title = if (count == 1) "Novo pedido disponível" else "$count novos pedidos disponíveis"
        val message = if (count == 1) {
            "Abra para visualizar o pedido."
        } else {
            "Abra para visualizar os novos pedidos."
        }

        val intent = Intent(context, ProviderOrdersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            2101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationManager.CHANNEL_ORDERS)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão para exibir notificação de novo pedido: ${e.message}")
        }
    }

    private fun stopMonitoring(reason: String) {
        if (ordersListener != null || monitoredProviderId != null) {
            Log.d(TAG, "Parando listener global de pedidos: $reason")
        }
        ordersListener?.remove()
        ordersListener = null
        monitoredProviderId = null
        hasInitialSnapshot = false
        providerServicesNormalized = emptySet()
        knownAvailableOrderIds.clear()
    }
}
