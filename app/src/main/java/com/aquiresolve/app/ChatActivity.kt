package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.models.OrderData
import com.aquiresolve.app.utils.ChatAccessHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ChatActivity legado: agora atua apenas como redirecionador para o chat correto.
 */
class ChatActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val orderId = intent.getStringExtra("order_id") ?: intent.getStringExtra("orderId")
        if (orderId.isNullOrBlank()) {
            showErrorAndFinish("Pedido não informado para abrir o chat")
            return
        }

        routeToChat(orderId)
    }

    private fun routeToChat(orderId: String) {
        lifecycleScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    showErrorAndFinish("Faça login para acessar o chat")
                    return@launch
                }

                val orderDoc = db.collection("orders").document(orderId).get().await()
                if (!orderDoc.exists()) {
                    showErrorAndFinish("Pedido não encontrado")
                    return@launch
                }

                val order = orderDoc.toObject(OrderData::class.java)?.copy(id = orderDoc.id)
                if (order == null) {
                    showErrorAndFinish("Dados do pedido inválidos")
                    return@launch
                }

                val (canAccess, message) = ChatAccessHelper.canAccessChat(order)
                if (!canAccess) {
                    AlertDialog.Builder(this@ChatActivity)
                        .setTitle("Chat Indisponível")
                        .setMessage(message ?: "O chat ainda não está disponível.")
                        .setPositiveButton("OK") { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                    return@launch
                }

                when {
                    currentUser.uid == order.clientId -> openClientChat(order)
                    !order.assignedProvider.isNullOrBlank() && currentUser.uid == order.assignedProvider -> openProviderChat(order)
                    else -> showErrorAndFinish("Você não tem permissão para acessar este chat")
                }
            } catch (e: Exception) {
                showErrorAndFinish("Erro ao abrir chat: ${e.message}")
            }
        }
    }

    private suspend fun openClientChat(order: OrderData) {
        val providerId = order.assignedProvider
        if (providerId.isNullOrBlank()) {
            showErrorAndFinish("Prestador ainda não atribuído ao pedido")
            return
        }

        val providerPhotoUrl = try {
            db.collection("providers").document(providerId)
                .get().await()
                .getString("profileImageUrl")
                ?: db.collection("users").document(providerId)
                    .get().await()
                    .getString("profileImageUrl")
        } catch (_: Exception) {
            null
        }

        val intent = Intent(this, ClientChatActivity::class.java).apply {
            putExtra("order_id", order.id)
            putExtra("provider_id", providerId)
            putExtra("provider_name", order.assignedProviderName)
            putExtra("provider_photo", providerPhotoUrl)
            putExtra("order_title", order.serviceName.ifEmpty { order.description })
        }
        startActivity(intent)
        finish()
    }

    private suspend fun openProviderChat(order: OrderData) {
        val clientPhotoUrl = try {
            db.collection("users").document(order.clientId)
                .get().await()
                .getString("profileImageUrl")
        } catch (_: Exception) {
            null
        }

        val intent = Intent(this, ProviderChatActivity::class.java).apply {
            putExtra("order_id", order.id)
            putExtra("client_id", order.clientId)
            putExtra("client_name", order.clientName)
            putExtra("client_photo", clientPhotoUrl)
            putExtra("order_title", order.serviceName.ifEmpty { order.description })
            putExtra("order_description", order.description)
        }
        startActivity(intent)
        finish()
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
