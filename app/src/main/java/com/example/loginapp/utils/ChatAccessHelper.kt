package com.example.loginapp.utils

import com.example.loginapp.models.OrderData
import com.google.firebase.Timestamp
import java.util.concurrent.TimeUnit

/**
 * Helper para verificar acesso ao chat
 */
object ChatAccessHelper {
    
    /**
     * Verifica se o chat pode ser acessado (5 minutos após aceitação do prestador)
     * @param order Pedido a ser verificado
     * @return Pair<Boolean, String?> onde Boolean indica se pode acessar e String é mensagem de erro (se houver)
     */
    fun canAccessChat(order: OrderData): Pair<Boolean, String?> {
        // Se o pedido não foi aceito por um prestador, não pode acessar
        if (order.status != OrderData.STATUS_ASSIGNED && order.status != OrderData.STATUS_IN_PROGRESS) {
            return false to "O chat só fica disponível após o prestador aceitar o pedido."
        }

        // Se não há assignedAt, liberar o chat (não bloquear por dados incompletos)
        val assignedAt = order.assignedAt ?: return true to null

        // Calcular tempo decorrido desde a aceitação
        val currentTime = Timestamp.now().toDate().time
        val assignedTime = assignedAt.toDate().time
        val elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(currentTime - assignedTime)

        // Verificar se passaram 5 minutos (300 segundos)
        if (elapsedSeconds < 300) {
            val totalRemaining = 300 - elapsedSeconds
            val remainingMinutes = totalRemaining / 60
            val remainingSeconds = totalRemaining % 60
            return false to "O chat será liberado em ${remainingMinutes}min ${remainingSeconds}s após o prestador aceitar o pedido."
        }

        return true to null
    }
    
    /**
     * Calcula tempo restante até liberação do chat (em segundos)
     */
    fun getTimeUntilChatUnlock(order: OrderData): Long? {
        if (order.status != OrderData.STATUS_ASSIGNED && order.status != OrderData.STATUS_IN_PROGRESS) {
            return null
        }
        
        val assignedAt = order.assignedAt ?: return null
        val now = Timestamp.now()
        val assignedTime = assignedAt.toDate().time
        val currentTime = now.toDate().time
        val elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(currentTime - assignedTime)
        
        return if (elapsedSeconds < 300) { // 5 minutos = 300 segundos
            300 - elapsedSeconds
        } else {
            0
        }
    }
}
