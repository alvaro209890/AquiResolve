package com.aquiresolve.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aquiresolve.app.utils.NewOrderSoundHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * BroadcastReceiver que processa a ação de "Rejeitar" na notificação
 * de novo pedido.
 *
 * Chamado quando o prestador toca em "Rejeitar" na notificação heads-up
 * de pedido disponível.
 */
class RejectOrderReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "RejectOrderReceiver"
        const val ACTION_REJECT_ORDER = "com.aquiresolve.app.REJECT_ORDER"
        const val EXTRA_ORDER_ID = "order_id"
        const val EXTRA_ORDER_IDS = "order_ids"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REJECT_ORDER) return

        val orderIds = mutableListOf<String>()

        // Suporta múltiplos pedidos (ex: "3 novos pedidos disponíveis")
        intent.getStringExtra(EXTRA_ORDER_ID)?.let { orderIds.add(it) }
        intent.getStringArrayListExtra(EXTRA_ORDER_IDS)?.let { orderIds.addAll(it) }

        if (orderIds.isEmpty()) {
            Log.w(TAG, "Nenhum orderId recebido no intent de rejeição")
            return
        }

        Log.d(TAG, "Rejeitando ${orderIds.size} pedido(s): $orderIds")

        scope.launch {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "Usuário não autenticado — ignorando rejeição")
                return@launch
            }

            val db = FirebaseFirestore.getInstance()

            for (orderId in orderIds) {
                try {
                    // Marcar pedido como rejeitado APENAS por este prestador.
                    // Só 'rejectedBy' é alterado (arrayUnion do próprio uid) — assim a
                    // regra validProviderRejectUpdate aprova e o status do pedido NÃO
                    // muda, ou seja, recusar não cancela o pedido para os outros.
                    db.collection("orders")
                        .document(orderId)
                        .update(
                            "rejectedBy",
                            com.google.firebase.firestore.FieldValue.arrayUnion(currentUser.uid)
                        )
                        .await()

                    Log.d(TAG, "Pedido $orderId marcado como rejeitado por ${currentUser.uid}")

                    // Parar o som contínuo para este pedido
                    NewOrderSoundHelper.stopSound(orderId)

                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao rejeitar pedido $orderId: ${e.message}", e)
                }
            }

            // Notificar o ProviderNewOrderAlertManager para reavaliar
            ProviderNewOrderAlertManager.refreshMonitoring()
        }
    }
}
