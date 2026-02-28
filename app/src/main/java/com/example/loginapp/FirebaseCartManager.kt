package com.example.loginapp

import android.util.Log
import com.example.loginapp.models.CartItemData
import com.example.loginapp.utils.ProtocolGenerator
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Gerenciador de carrinho do cliente.
 */
class FirebaseCartManager {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FirebaseCartManager"
        private const val CARTS_COLLECTION = "carts"
        private const val ITEMS_COLLECTION = "items"
    }

    suspend fun addItem(item: CartItemData): Result<String> {
        return try {
            val userId = item.clientId.ifBlank { auth.currentUser?.uid ?: "" }
            if (userId.isBlank()) {
                return Result.failure(Exception("Usuário não autenticado"))
            }

            val cartItemsRef = db.collection(CARTS_COLLECTION)
                .document(userId)
                .collection(ITEMS_COLLECTION)

            val itemId = item.id.ifBlank { cartItemsRef.document().id }
            val payload = item.copy(
                id = itemId,
                clientId = userId,
                updatedAt = Timestamp.now(),
                createdAt = item.createdAt
            )

            cartItemsRef.document(itemId)
                .set(payload)
                .await()

            Result.success(itemId)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao adicionar item no carrinho", e)
            Result.failure(e)
        }
    }

    suspend fun getItems(userId: String): Result<List<CartItemData>> {
        return try {
            val snapshot = db.collection(CARTS_COLLECTION)
                .document(userId)
                .collection(ITEMS_COLLECTION)
                .orderBy("createdAt")
                .get()
                .await()

            val items = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CartItemData::class.java)?.copy(id = doc.id)
            }

            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar itens do carrinho", e)
            Result.failure(e)
        }
    }

    suspend fun removeItem(userId: String, itemId: String): Result<Unit> {
        return try {
            db.collection(CARTS_COLLECTION)
                .document(userId)
                .collection(ITEMS_COLLECTION)
                .document(itemId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover item do carrinho", e)
            Result.failure(e)
        }
    }

    suspend fun clearCart(userId: String): Result<Unit> {
        return try {
            val snapshot = db.collection(CARTS_COLLECTION)
                .document(userId)
                .collection(ITEMS_COLLECTION)
                .get()
                .await()

            val batch = db.batch()
            snapshot.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao limpar carrinho", e)
            Result.failure(e)
        }
    }

    suspend fun checkoutCart(
        userId: String,
        clientName: String,
        clientEmail: String,
        transactionId: String,
        paymentStatus: String
    ): Result<List<String>> {
        return try {
            val cartSnapshot = db.collection(CARTS_COLLECTION)
                .document(userId)
                .collection(ITEMS_COLLECTION)
                .get()
                .await()

            if (cartSnapshot.isEmpty) {
                return Result.failure(Exception("Carrinho vazio"))
            }

            val items = cartSnapshot.documents.mapNotNull { doc ->
                doc.toObject(CartItemData::class.java)?.copy(id = doc.id)
            }

            if (items.isEmpty()) {
                return Result.failure(Exception("Carrinho vazio"))
            }

            val now = Timestamp.now()
            val batch = db.batch()
            val createdOrderIds = mutableListOf<String>()

            items.forEach { item ->
                val orderRef = db.collection("orders").document()
                createdOrderIds.add(orderRef.id)

                val providerCommission = com.example.loginapp.models.ServicePricing.getProviderValue(
                    category = item.serviceNiche,
                    serviceType = item.serviceType
                ) ?: com.example.loginapp.models.ServicePricing.getDefaultProviderValue(item.serviceNiche)

                val orderData = mutableMapOf<String, Any>(
                    "clientId" to userId,
                    "clientName" to clientName,
                    "clientEmail" to clientEmail,
                    "protocol" to ProtocolGenerator.generateProtocol(),
                    "serviceType" to item.serviceType,
                    "serviceName" to item.serviceNiche,
                    "description" to item.description,
                    "address" to item.address,
                    "zipCode" to item.zipCode,
                    "complement" to item.complement,
                    "city" to item.city,
                    "state" to item.state,
                    "status" to "distributing",
                    "paymentStatus" to paymentStatus,
                    "transactionId" to transactionId,
                    "estimatedPrice" to item.estimatedPrice,
                    "providerCommission" to providerCommission,
                    "images" to item.imageUrls,
                    "createdAt" to now,
                    "updatedAt" to now,
                    "confirmedAt" to now
                )

                item.preferredDate?.let { orderData["scheduledDate"] = it }
                item.preferredTime?.let { orderData["preferredTimeSlot"] = it }
                item.coordinates?.let { orderData["coordinates"] = it }

                batch.set(orderRef, orderData)

                val cartItemRef = db.collection(CARTS_COLLECTION)
                    .document(userId)
                    .collection(ITEMS_COLLECTION)
                    .document(item.id)
                batch.delete(cartItemRef)
            }

            batch.commit().await()
            Result.success(createdOrderIds)
        } catch (e: Exception) {
            Log.e(TAG, "Erro no checkout do carrinho", e)
            Result.failure(e)
        }
    }
}
