package com.aquiresolve.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aquiresolve.app.models.OrderData
import com.aquiresolve.app.utils.NewOrderSoundHelper
import com.aquiresolve.app.utils.ServiceNicheCatalog
import com.aquiresolve.app.utils.TowingDispatch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
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
    // Reavalia o raio do guincho (degraus de 4 min) mesmo sem mudança nos pedidos.
    private const val DISPATCH_REFRESH_MS = 60_000L

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

    // Pedidos que casam o nicho (independente do raio); o gate de raio do guincho é
    // reavaliado por tempo via ticker, alertando quando um guincho passa a alcançar
    // este prestador conforme o raio cresce.
    private val lastMatchingOrders = mutableMapOf<String, OrderData>()
    private var providerLocation: GeoPoint? = null
    private val dispatchHandler = Handler(Looper.getMainLooper())

    // IDs dos pedidos para os quais já disparamos alerta sonoro contínuo.
    // Quando o status do pedido mudar para "assigned" (aceito por alguém),
    // paramos o som automaticamente.
    private val alertedOrderIds = mutableSetOf<String>()
    // Listener que monitora mudanças de status dos pedidos alertados
    private var ordersStatusListener: ListenerRegistration? = null
    private val dispatchTicker = object : Runnable {
        override fun run() {
            monitoredProviderId?.let { uid -> scope.launch { refreshProviderLocation(uid) } }
            evaluateAndAlert()
            dispatchHandler.postDelayed(this, DISPATCH_REFRESH_MS)
        }
    }

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

            val initialLocation = userDoc.getGeoPoint("coordinates")

            withContext(Dispatchers.Main) {
                stopMonitoring("reiniciar listener", keepSound = true)

                monitoredProviderId = userId
                providerServicesNormalized = services
                providerLocation = initialLocation
                hasInitialSnapshot = false
                knownAvailableOrderIds.clear()
                lastMatchingOrders.clear()

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

                        lastMatchingOrders.clear()
                        snapshot?.documents?.forEach { document ->
                            val order = try {
                                document.toObject(OrderData::class.java)?.copy(id = document.id)
                            } catch (e: Exception) {
                                Log.w(TAG, "Erro ao converter pedido ${document.id}: ${e.message}")
                                null
                            } ?: return@forEach
                            // Pular pedidos que este prestador já rejeitou
                            if (order.rejectedBy.contains(userId)) return@forEach
                            if (matchesProviderService(order)) lastMatchingOrders[document.id] = order
                        }

                        evaluateAndAlert()
                    }

                // Ticker para expandir o raio do guincho ao longo do tempo.
                dispatchHandler.removeCallbacks(dispatchTicker)
                dispatchHandler.postDelayed(dispatchTicker, DISPATCH_REFRESH_MS)

                Log.d(TAG, "Listener global de novos pedidos ativo para prestador: $userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar listener global de pedidos: ${e.message}", e)
        }
    }

    private fun matchesProviderService(order: OrderData): Boolean {
        return ServiceNicheCatalog.matchesProviderServices(providerServicesNormalized, order)
    }

    /**
     * Recalcula quais pedidos podem ser ofertados a este prestador AGORA (guincho
     * respeita o raio expansivo) e alerta sobre os que passaram a ficar disponíveis.
     */
    private fun evaluateAndAlert() {
        val context = appContext ?: return
        val now = System.currentTimeMillis()
        val availableIds = lastMatchingOrders.values
            .filter { TowingDispatch.canOfferToProvider(it, providerLocation, now) }
            .map { it.id }
            .toSet()

        if (!hasInitialSnapshot) {
            hasInitialSnapshot = true
            knownAvailableOrderIds.clear()
            knownAvailableOrderIds.addAll(availableIds)
            Log.d(TAG, "Snapshot inicial carregado com ${availableIds.size} pedidos no raio")
            return
        }

        val newIds = availableIds - knownAvailableOrderIds
        knownAvailableOrderIds.clear()
        knownAvailableOrderIds.addAll(availableIds)

        if (newIds.isNotEmpty()) {
            notifyNewOrders(context, newIds)
        }
    }

    private suspend fun refreshProviderLocation(userId: String) {
        try {
            val geo = firestore.collection("users").document(userId).get().await()
                .getGeoPoint("coordinates")
            withContext(Dispatchers.Main) { providerLocation = geo }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao atualizar localização do prestador: ${e.message}")
        }
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

    private fun notifyNewOrders(context: Context, newIds: Set<String>) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            try {
                // Iniciar som contínuo para cada novo pedido
                for (orderId in newIds) {
                    NewOrderSoundHelper.startContinuousPlay(context, orderId)
                    alertedOrderIds.add(orderId)
                }

                // Configurar listener para parar o som quando o pedido for aceito
                // (monitora TODOS os pedidos alertados, não só os novos)
                setupOrderAcceptedListener()

                showHeadsUpNotification(context, newIds)
                Log.d(TAG, "Alerta de novo pedido disparado (${newIds.size} pedidos, som contínuo)")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao disparar alerta de novo pedido: ${e.message}", e)
            }
        }
    }

    /**
     * Registra pedidos cujo som contínuo foi iniciado por OUTRO caminho (ex.: push FCM
     * com o app fechado, em FirebaseMessagingService) para que o som também pare
     * automaticamente quando algum prestador aceitar o pedido. Sem isso, um som
     * iniciado via FCM tocaria para sempre mesmo depois de outro prestador aceitar.
     */
    fun watchAlertedOrders(orderIds: Set<String>) {
        if (orderIds.isEmpty()) return
        Handler(Looper.getMainLooper()).post {
            alertedOrderIds.addAll(orderIds)
            setupOrderAcceptedListener()
        }
    }

    /**
     * Configura um listener Firestore que monitora TODOS os pedidos atualmente
     * alertados (alertedOrderIds). Quando o status de um deles muda para "assigned"
     * (alguém aceitou) ou sai de disponível, para o som daquele pedido.
     */
    private fun setupOrderAcceptedListener() {
        val monitoredIds = alertedOrderIds.toList().take(30) // limite do whereIn
        if (monitoredIds.isEmpty()) return

        Log.d(TAG, "Monitorando status de ${monitoredIds.size} pedido(s) alertados")

        // Remove listener anterior se existir
        ordersStatusListener?.remove()

        // Usa whereIn para monitorar todos os pedidos alertados de uma vez
        ordersStatusListener = firestore.collection("orders")
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), monitoredIds)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erro no listener de status: ${error.message}", error)
                    return@addSnapshotListener
                }

                snapshot?.documents?.forEach { doc ->
                    val orderId = doc.id
                    val status = doc.getString("status") ?: return@forEach
                    val assigned = doc.getString("assignedProvider")

                    // Para o som se o pedido foi aceito por alguém
                    // ou se o status não é mais distributing/pending
                    val isAccepted = status == OrderData.STATUS_ASSIGNED && !assigned.isNullOrBlank()
                    val isNoLongerAvailable = status != OrderData.STATUS_DISTRIBUTING
                        && status != OrderData.STATUS_PENDING
                        && status != "available"
                        && status != OrderData.STATUS_DISTRIBUTING.uppercase(Locale.ROOT)
                        && status != OrderData.STATUS_PENDING.uppercase(Locale.ROOT)
                        && status != "AVAILABLE"

                    if (isAccepted || isNoLongerAvailable) {
                        Log.d(TAG, "Pedido $orderId mudou para '$status' — parando som")
                        NewOrderSoundHelper.stopSound(orderId)
                        alertedOrderIds.remove(orderId)
                    }
                }

                // Se todos os pedidos já foram resolvidos, remove o listener
                if (alertedOrderIds.isEmpty()) {
                    ordersStatusListener?.remove()
                    ordersStatusListener = null
                    Log.d(TAG, "Todos os pedidos alertados foram resolvidos — listener removido")
                }
            }
    }

    private fun showHeadsUpNotification(context: Context, newOrderIds: Set<String>) {
        val count = newOrderIds.size
        val title = if (count == 1) "Novo pedido disponível" else "$count novos pedidos disponíveis"
        val message = if (count == 1) {
            "Toque para visualizar ou use os botões abaixo."
        } else {
            "Toque para visualizar ou use os botões abaixo."
        }

        // Intent principal: abrir tela de pedidos
        val viewIntent = Intent(context, ProviderOrdersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val viewPendingIntent = PendingIntent.getActivity(
            context, 2101, viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent de rejeitar: envia broadcast para RejectOrderReceiver
        val rejectIntent = Intent(context, RejectOrderReceiver::class.java).apply {
            action = RejectOrderReceiver.ACTION_REJECT_ORDER
            putStringArrayListExtra(RejectOrderReceiver.EXTRA_ORDER_IDS, ArrayList(newOrderIds))
            // Para single order, também seta o extra simples
            if (newOrderIds.size == 1) {
                putExtra(RejectOrderReceiver.EXTRA_ORDER_ID, newOrderIds.first())
            }
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context, 2102, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent de aceitar (primeiro pedido): abre direto na tela de detalhes
        val acceptIntent = if (newOrderIds.size == 1) {
            Intent(context, OrderDetailsActivity::class.java).apply {
                putExtra("order_id", newOrderIds.first())
                putExtra("is_provider_view", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            // Múltiplos pedidos: abre a lista
            Intent(context, ProviderOrdersActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context, 2103, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationManager.CHANNEL_ORDERS)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(true)  // Não some até ação explícita
            .setContentIntent(viewPendingIntent)
            .addAction(R.drawable.ic_check_circle, "Aceitar", acceptPendingIntent)
            .addAction(R.drawable.ic_close, "Rejeitar", rejectPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, notification)
            Log.d(TAG, "Notificação heads-up exibida com ações Aceitar/Rejeitar para $count pedido(s)")
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão para exibir notificação de novo pedido: ${e.message}")
        }
    }

    private fun stopMonitoring(reason: String, keepSound: Boolean = false) {
        if (ordersListener != null || monitoredProviderId != null) {
            Log.d(TAG, "Parando listener global de pedidos: $reason")
        }
        dispatchHandler.removeCallbacks(dispatchTicker)
        ordersListener?.remove()
        ordersListener = null
        ordersStatusListener?.remove()
        ordersStatusListener = null
        // Só para os sons se não for um refresh (keepSound=true)
        if (!keepSound) {
            NewOrderSoundHelper.stopSound()
            alertedOrderIds.clear()
        }
        monitoredProviderId = null
        hasInitialSnapshot = false
        providerServicesNormalized = emptySet()
        knownAvailableOrderIds.clear()
        lastMatchingOrders.clear()
        providerLocation = null
    }
}
