package com.example.loginapp.constants

/**
 * Códigos de resultado para pagamento
 * Centralizados para garantir consistência
 */
object PaymentResultCodes {
    const val RESULT_PAYMENT_SUCCESS = 1001
    const val RESULT_PAYMENT_PENDING = 1002
    const val RESULT_PAYMENT_FAILED = 1003
    
    // Extras
    const val EXTRA_TRANSACTION_ID = "transaction_id"
    const val EXTRA_PAYMENT_STATUS = "payment_status"
    const val EXTRA_ERROR_MESSAGE = "error_message"
}




























