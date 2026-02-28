package com.example.loginapp.utils

import java.util.Random

/**
 * Gerador de códigos de verificação para finalização de pedidos
 * Gera códigos de 6 dígitos numéricos
 */
object VerificationCodeGenerator {
    
    private val random = Random()
    
    /**
     * Gera um código de verificação de 6 dígitos
     * Formato: XXXXXX (ex: 123456)
     */
    fun generateCode(): String {
        // Gerar número aleatório entre 100000 e 999999
        val code = random.nextInt(900000) + 100000
        return code.toString()
    }
    
    /**
     * Gera um código de verificação com tamanho customizado
     */
    fun generateCode(length: Int): String {
        require(length > 0) { "O tamanho do código deve ser maior que 0" }
        require(length <= 10) { "O tamanho máximo do código é 10 dígitos" }
        
        val min = Math.pow(10.0, (length - 1).toDouble()).toInt()
        val max = Math.pow(10.0, length.toDouble()).toInt() - 1
        
        val code = random.nextInt(max - min + 1) + min
        return code.toString().padStart(length, '0')
    }
    
    /**
     * Valida se um código está no formato correto (6 dígitos numéricos)
     */
    fun isValidCode(code: String): Boolean {
        return code.matches(Regex("^\\d{6}$"))
    }
    
    /**
     * Formata código para exibição (adiciona espaços)
     * Ex: 123456 -> 123 456
     */
    fun formatCodeForDisplay(code: String): String {
        return if (code.length == 6) {
            "${code.substring(0, 3)} ${code.substring(3, 6)}"
        } else {
            code
        }
    }
    
    /**
     * Remove formatação do código (remove espaços e caracteres não numéricos)
     */
    fun cleanCode(code: String): String {
        return code.replace(Regex("[^\\d]"), "")
    }
}








