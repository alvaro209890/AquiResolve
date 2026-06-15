package com.aquiresolve.app

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import java.util.Locale

class FirebaseChatManager {
    
    private val firestore: FirebaseFirestore = FirebaseConfig.getFirestore()
    
    data class ChatMessage(
        val id: String = "",
        val orderId: String = "",
        val senderId: String = "",
        val senderName: String = "",
        val senderType: String = "", // "client", "provider" or "admin"
        val message: String = "",
        val timestamp: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
        val isRead: Boolean = false,
        val imageUrl: String? = null,
        val documentUrl: String? = null,
        val threadType: String = THREAD_CLIENT_PROVIDER,
        val visibility: String = VISIBILITY_PUBLIC,
        val messageType: String? = null,
        val fileName: String? = null,
        val fileSize: Long? = null,
        val fileType: String? = null
    )
    
    data class ChatRoom(
        val id: String = "",
        val orderId: String = "",
        val clientId: String = "",
        val clientName: String = "",
        val providerId: String = "",
        val providerName: String = "",
        val lastMessage: String = "",
        val lastMessageTime: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
        val unreadCount: Int = 0
    )

    companion object {
        const val THREAD_CLIENT_PROVIDER = "client_provider"
        const val THREAD_CLIENT_BASE = "client_base"
        const val THREAD_PROVIDER_BASE = "provider_base"
        const val THREAD_ADMIN_INTERNAL = "admin_internal"
        const val VISIBILITY_PUBLIC = "public"
        const val VISIBILITY_ADMIN_CLIENT = "admin_client"
        const val VISIBILITY_ADMIN_PROVIDER = "admin_provider"
        const val VISIBILITY_ADMIN_ONLY = "admin_only"
    }
    
    suspend fun sendMessage(message: ChatMessage): Result<String> {
        return try {
            val threadType = normalizeThreadType(message.threadType)
            val visibility = normalizeVisibility(message.visibility, threadType)
            val resolvedMessageType = message.messageType ?: when {
                !message.imageUrl.isNullOrEmpty() -> "image"
                !message.documentUrl.isNullOrEmpty() -> "file"
                else -> "text"
            }
            val metadata = mutableMapOf<String, Any>()
            message.imageUrl?.takeIf { it.isNotBlank() }?.let { metadata["imageUrl"] = it }
            message.documentUrl?.takeIf { it.isNotBlank() }?.let { metadata["documentUrl"] = it }
            message.fileName?.takeIf { it.isNotBlank() }?.let { metadata["fileName"] = it }
            message.fileSize?.let { metadata["fileSize"] = it }
            message.fileType?.takeIf { it.isNotBlank() }?.let { metadata["fileType"] = it }

            val messageMap = hashMapOf(
                "orderId" to message.orderId,
                "senderId" to message.senderId,
                "senderName" to message.senderName,
                "senderType" to message.senderType,
                "message" to message.message,
                "content" to message.message,
                "timestamp" to message.timestamp,
                "isRead" to message.isRead,
                "imageUrl" to message.imageUrl,
                "documentUrl" to message.documentUrl,
                "threadType" to threadType,
                "visibility" to visibility,
                "messageType" to resolvedMessageType,
                "fileName" to message.fileName,
                "fileSize" to message.fileSize,
                "fileType" to message.fileType,
                "metadata" to metadata
            )

            // Usar subcoleção por pedido para evitar necessidade de índice composto
            val docRef = firestore
                .collection("orders")
                .document(message.orderId)
                .collection("messages")
                .add(messageMap)
                .await()
            
            // Atualizar a sala de chat
            updateChatRoom(message.orderId, message)

            // Atualizar/criar a conversa para o painel admin (Central Operacional)
            upsertChatConversation(message.orderId, message)

            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMessagesForOrder(orderId: String): Result<List<ChatMessage>> {
        return try {
            val query = firestore
                .collection("orders")
                .document(orderId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
            
            val messages = query.documents.mapNotNull { doc ->
                documentToChatMessage(doc, orderId)
            }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMessagesFlow(orderId: String, viewerRole: String? = null): Flow<List<ChatMessage>> = callbackFlow {
        val registration = firestore
            .collection("orders")
            .document(orderId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents
                    ?.mapNotNull { doc -> documentToChatMessage(doc, orderId) }
                    ?.filter { message -> viewerRole == null || isVisibleToRole(message, viewerRole) }
                    ?: emptyList()
                trySend(messages).isSuccess
            }
        awaitClose { registration.remove() }
    }
    
    suspend fun getChatRoomsForUser(userId: String): Result<List<ChatRoom>> {
        return try {
            val query = firestore.collection("chatRooms")
                .whereEqualTo("clientId", userId)
                .orderBy("lastMessageTime", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val chatRooms = query.documents.mapNotNull { doc ->
                doc.toObject(ChatRoom::class.java)?.copy(id = doc.id)
            }
            Result.success(chatRooms)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getChatRoomsForProvider(providerId: String): Result<List<ChatRoom>> {
        return try {
            val query = firestore.collection("chatRooms")
                .whereEqualTo("providerId", providerId)
                .orderBy("lastMessageTime", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val chatRooms = query.documents.mapNotNull { doc ->
                doc.toObject(ChatRoom::class.java)?.copy(id = doc.id)
            }
            Result.success(chatRooms)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun markMessagesAsRead(orderId: String, userId: String): Result<Unit> {
        return try {
            val query = firestore
                .collection("orders")
                .document(orderId)
                .collection("messages")
                .whereEqualTo("isRead", false)
                .get()
                .await()
            
            val batch = firestore.batch()
            for (document in query.documents) {
                val senderId = document.getString("senderId")
                if (senderId != userId) {
                    batch.update(document.reference, "isRead", true)
                }
            }
            batch.commit().await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createChatRoom(chatRoom: ChatRoom): Result<String> {
        return try {
            val chatRoomMap = hashMapOf(
                "orderId" to chatRoom.orderId,
                "clientId" to chatRoom.clientId,
                "clientName" to chatRoom.clientName,
                "providerId" to chatRoom.providerId,
                "providerName" to chatRoom.providerName,
                "lastMessage" to chatRoom.lastMessage,
                "lastMessageTime" to chatRoom.lastMessageTime,
                "unreadCount" to chatRoom.unreadCount
            )
            
            val docRef = firestore.collection("chatRooms").add(chatRoomMap).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun updateChatRoom(orderId: String, message: ChatMessage) {
        try {
            val chatRoomQuery = firestore.collection("chatRooms")
                .whereEqualTo("orderId", orderId)
                .get()
                .await()
            
            if (chatRoomQuery.documents.isNotEmpty()) {
                val chatRoomDoc = chatRoomQuery.documents.first()
                chatRoomDoc.reference.update(
                    mapOf(
                        "lastMessage" to message.message,
                        "lastMessageTime" to message.timestamp,
                        // unreadCount será administrado por cliente/prestador ao ler
                        "unreadCount" to (chatRoomDoc.getLong("unreadCount") ?: 0)
                    )
                ).await()
            }
        } catch (e: Exception) {
            // Log error but don't fail the message send
            e.printStackTrace()
        }
    }
    
    /**
     * Cria/atualiza `chatConversations/{orderId}` para alimentar a Central Operacional do painel admin.
     * Na primeira mensagem resolve as identidades pelo pedido (orders/{orderId}); nas demais só atualiza
     * a última mensagem (merge), sem sobrescrever status/priority/notas definidos pelo admin.
     */
    private suspend fun upsertChatConversation(orderId: String, message: ChatMessage) {
        try {
            val messageType = message.messageType ?: when {
                !message.imageUrl.isNullOrEmpty() -> "image"
                !message.documentUrl.isNullOrEmpty() -> "file"
                else -> "text"
            }
            val lastMessage = mapOf(
                "content" to message.message,
                "senderName" to message.senderName,
                "senderId" to message.senderId,
                "timestamp" to message.timestamp,
                "messageType" to messageType,
                "threadType" to normalizeThreadType(message.threadType),
                "visibility" to normalizeVisibility(message.visibility, message.threadType)
            )

            val convRef = firestore.collection("chatConversations").document(orderId)
            val existing = convRef.get().await()

            if (existing.exists()) {
                convRef.set(
                    mapOf(
                        "orderId" to orderId,
                        "lastMessage" to lastMessage,
                        "updatedAt" to Timestamp.now()
                    ),
                    SetOptions.merge()
                ).await()
                return
            }

            // Primeira mensagem: resolve cliente/prestador pelo pedido.
            val order = firestore.collection("orders").document(orderId).get().await()
            val clienteId = order.getString("clientId")
                ?: if (message.senderType == "client") message.senderId else ""
            val clienteName = order.getString("clientName")
                ?: if (message.senderType == "client") message.senderName else ""
            val prestadorId = order.getString("assignedProvider")
                ?: if (message.senderType == "provider") message.senderId else ""
            val prestadorName = order.getString("assignedProviderName")
                ?: order.getString("providerName")
                ?: if (message.senderType == "provider") message.senderName else ""
            val protocol = order.getString("protocol")
                ?: order.getString("orderProtocol")
                ?: ""

            convRef.set(
                mapOf(
                    "orderId" to orderId,
                    "clienteId" to clienteId,
                    "clienteName" to clienteName,
                    "prestadorId" to prestadorId,
                    "prestadorName" to prestadorName,
                    "orderProtocol" to protocol,
                    "status" to "active",
                    "priority" to "medium",
                    "lastMessage" to lastMessage,
                    "unreadCount" to mapOf("cliente" to 0, "prestador" to 0, "admin" to 0),
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            ).await()
        } catch (e: Exception) {
            // Não falha o envio da mensagem se a sincronização da conversa falhar.
            e.printStackTrace()
        }
    }

    private fun documentToChatMessage(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        fallbackOrderId: String
    ): ChatMessage? {
        if (doc.getBoolean("isDeleted") == true) {
            return null
        }

        val rawThreadType = doc.getString("threadType") ?: doc.getString("channel")
        val threadType = normalizeThreadType(rawThreadType)
        val visibility = normalizeVisibility(doc.getString("visibility"), threadType)
        val metadata = doc.get("metadata") as? Map<*, *>
        val imageUrl = doc.getString("imageUrl")
            ?: doc.getString("image_url")
            ?: doc.getString("mediaUrl")
            ?: doc.getString("attachmentUrl")
            ?: metadata?.get("imageUrl") as? String
        val documentUrl = doc.getString("documentUrl")
            ?: doc.getString("fileUrl")
            ?: metadata?.get("documentUrl") as? String
        val senderType = normalizeSenderType(doc.getString("senderType"))

        return ChatMessage(
            id = doc.id,
            orderId = doc.getString("orderId") ?: fallbackOrderId,
            senderId = doc.getString("senderId") ?: "",
            senderName = doc.getString("senderName") ?: when (senderType) {
                "admin" -> "Base AquiResolve"
                "provider" -> "Prestador"
                else -> "Cliente"
            },
            senderType = senderType,
            message = doc.getString("message") ?: doc.getString("content") ?: "",
            timestamp = doc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
            isRead = doc.getBoolean("isRead") ?: false,
            imageUrl = imageUrl,
            documentUrl = documentUrl,
            threadType = threadType,
            visibility = visibility,
            messageType = doc.getString("messageType") ?: if (!documentUrl.isNullOrBlank()) "file" else null,
            fileName = doc.getString("fileName") ?: metadata?.get("fileName") as? String,
            fileSize = doc.getLong("fileSize") ?: (metadata?.get("fileSize") as? Number)?.toLong(),
            fileType = doc.getString("fileType") ?: metadata?.get("fileType") as? String
        )
    }

    private fun normalizeSenderType(value: String?): String {
        return when (value?.lowercase(Locale.ROOT)) {
            "provider", "prestador" -> "provider"
            "admin", "support", "system" -> "admin"
            else -> "client"
        }
    }

    private fun normalizeThreadType(value: String?): String {
        return when (value?.lowercase(Locale.ROOT)) {
            THREAD_CLIENT_BASE -> THREAD_CLIENT_BASE
            THREAD_PROVIDER_BASE -> THREAD_PROVIDER_BASE
            THREAD_ADMIN_INTERNAL -> THREAD_ADMIN_INTERNAL
            else -> THREAD_CLIENT_PROVIDER
        }
    }

    private fun normalizeVisibility(value: String?, threadType: String): String {
        return when (value?.lowercase(Locale.ROOT)) {
            VISIBILITY_ADMIN_CLIENT -> VISIBILITY_ADMIN_CLIENT
            VISIBILITY_ADMIN_PROVIDER -> VISIBILITY_ADMIN_PROVIDER
            VISIBILITY_ADMIN_ONLY -> VISIBILITY_ADMIN_ONLY
            VISIBILITY_PUBLIC -> VISIBILITY_PUBLIC
            else -> when (normalizeThreadType(threadType)) {
                THREAD_CLIENT_BASE -> VISIBILITY_ADMIN_CLIENT
                THREAD_PROVIDER_BASE -> VISIBILITY_ADMIN_PROVIDER
                THREAD_ADMIN_INTERNAL -> VISIBILITY_ADMIN_ONLY
                else -> VISIBILITY_PUBLIC
            }
        }
    }

    private fun isVisibleToRole(message: ChatMessage, viewerRole: String): Boolean {
        return when (viewerRole.lowercase(Locale.ROOT)) {
            "client", "cliente" -> message.visibility == VISIBILITY_PUBLIC || message.visibility == VISIBILITY_ADMIN_CLIENT
            "provider", "prestador" -> message.visibility == VISIBILITY_PUBLIC || message.visibility == VISIBILITY_ADMIN_PROVIDER
            else -> true
        }
    }

    suspend fun deleteMessage(orderId: String, messageId: String): Result<Unit> {
        return try {
            firestore
                .collection("orders")
                .document(orderId)
                .collection("messages")
                .document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteChatRoom(chatRoomId: String): Result<Unit> {
        return try {
            firestore.collection("chatRooms").document(chatRoomId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
