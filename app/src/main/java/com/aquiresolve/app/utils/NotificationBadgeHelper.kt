package com.aquiresolve.app.utils

import android.util.Log
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Helper para gerenciar badge de notificações no BottomNavigationView.
 * 
 * Monitora a coleção "notifications" do Firestore em tempo real
 * e exibe um badge vermelho com a contagem de notificações não lidas.
 */
object NotificationBadgeHelper {

    private const val TAG = "NotificationBadge"
    private var notificationsListener: ListenerRegistration? = null

    /**
     * Inicia o monitoramento de notificações e atualiza o badge.
     * 
     * @param bottomNav BottomNavigationView onde exibir o badge
     * @param menuItemId ID do item de menu que receberá o badge (ex: R.id.navigation_orders)
     * @param onNewNotification Callback opcional quando chega notificação nova
     */
    fun startListening(
        bottomNav: BottomNavigationView,
        menuItemId: Int,
        onNewNotification: ((title: String, message: String) -> Unit)? = null
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        var lastKnownCount = 0

        // Parar listener anterior se existir
        stopListening()

        notificationsListener = db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erro ao monitorar notificações: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val count = snapshot.size()

                    // Atualizar badge
                    if (count > 0) {
                        val badge = bottomNav.getOrCreateBadge(menuItemId)
                        badge.isVisible = true
                        badge.number = count
                        badge.backgroundColor = android.graphics.Color.parseColor("#FF0000")
                        badge.badgeTextColor = android.graphics.Color.WHITE
                        badge.maxCharacterCount = 3

                        // Notificar sobre novas notificações (se count aumentou)
                        if (count > lastKnownCount && onNewNotification != null) {
                            // Pega a notificação mais recente
                            val docs = snapshot.documents.sortedByDescending { 
                                it.getTimestamp("timestamp")?.toDate()?.time ?: 0L 
                            }
                            val latest = docs.firstOrNull()
                            if (latest != null) {
                                val title = latest.getString("title") ?: "Nova notificação"
                                val message = latest.getString("message") ?: ""
                                onNewNotification(title, message)
                            }
                        }
                    } else {
                        // Remover badge se não houver notificações
                        try {
                            bottomNav.removeBadge(menuItemId)
                        } catch (_: Exception) {
                            // Pode lançar exceção se badge não existir
                        }
                    }

                    lastKnownCount = count
                }
            }
    }

    /**
     * Remove o badge e para o listener
     */
    fun stopListening() {
        notificationsListener?.remove()
        notificationsListener = null
    }

    /**
     * Busca contagem de notificações não lidas manualmente (one-shot)
     */
    suspend fun getUnreadCount(): Int {
        return try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return 0
            val snapshot = FirebaseFirestore.getInstance().collection("notifications")
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()
            snapshot.size()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar contagem: ${e.message}")
            0
        }
    }
}
