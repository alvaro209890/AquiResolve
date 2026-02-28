package com.example.loginapp.utils

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.loginapp.R

/**
 * Helper para exibir diálogos de códigos de verificação
 */
object VerificationCodeDialog {
    
    /**
     * Mostra diálogo com os códigos de verificação após aceitar pedido
     */
    fun showVerificationCodesDialog(
        context: Context,
        clientCode: String,
        providerCode: String,
        orderProtocol: String = "",
        onDismiss: () -> Unit = {}
    ) {
        val formattedClientCode = VerificationCodeGenerator.formatCodeForDisplay(clientCode)
        val formattedProviderCode = VerificationCodeGenerator.formatCodeForDisplay(providerCode)
        
        val message = """
            ✅ Pedido aceito com sucesso!
            
            📋 Protocolo: $orderProtocol
            
            🔐 Códigos de Verificação:
            
            👤 Código do Cliente:
            $formattedClientCode
            
            🔧 Seu Código (Prestador):
            $formattedProviderCode
            
            ⚠️ IMPORTANTE:
            • Guarde estes códigos com segurança
            • O cliente receberá o código dele
            • Para finalizar o serviço, você precisará do código do cliente
            
            💡 Dica: Tire um print desta tela ou anote os códigos!
        """.trimIndent()
        
        AlertDialog.Builder(context)
            .setTitle("🔐 Códigos de Verificação Gerados")
            .setMessage(message)
            .setPositiveButton("Copiar Código do Cliente") { _, _ ->
                copyToClipboard(context, clientCode, "Código do Cliente")
            }
            .setNeutralButton("Copiar Meu Código") { _, _ ->
                copyToClipboard(context, providerCode, "Seu Código")
            }
            .setNegativeButton("Entendi") { dialog, _ ->
                dialog.dismiss()
                onDismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Mostra diálogo para o cliente com seu código de verificação
     */
    fun showClientCodeDialog(
        context: Context,
        clientCode: String,
        orderProtocol: String = "",
        providerName: String = "Prestador"
    ) {
        val formattedCode = VerificationCodeGenerator.formatCodeForDisplay(clientCode)
        
        val message = """
            ✅ Seu pedido foi aceito por $providerName!
            
            📋 Protocolo: $orderProtocol
            
            🔐 Seu Código de Verificação:
            $formattedCode
            
            ⚠️ IMPORTANTE:
            • Guarde este código com segurança
            • Você precisará fornecer este código ao prestador quando o serviço for concluído
            • O prestador precisará deste código para finalizar o pedido
            
            💡 Dica: Tire um print desta tela ou anote o código!
        """.trimIndent()
        
        AlertDialog.Builder(context)
            .setTitle("🔐 Código de Verificação")
            .setMessage(message)
            .setPositiveButton("Copiar Código") { _, _ ->
                copyToClipboard(context, clientCode, "Código de Verificação")
            }
            .setNegativeButton("Fechar", null)
            .setCancelable(false)
            .show()
    }
    
    /**
     * Mostra diálogo para solicitar código do cliente ao finalizar serviço
     */
    fun showCodeInputDialog(
        context: Context,
        onCodeEntered: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val input = android.widget.EditText(context).apply {
            hint = "Digite o código de 6 dígitos"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            maxLines = 1
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        
        AlertDialog.Builder(context)
            .setTitle("🔐 Finalizar Serviço")
            .setMessage(
                "Para finalizar o serviço, você precisa do código de verificação do cliente.\n\n" +
                "Peça ao cliente para fornecer o código que foi gerado quando você aceitou o pedido."
            )
            .setView(input)
            .setPositiveButton("Finalizar") { _, _ ->
                val code = input.text.toString().trim()
                if (code.length == 6) {
                    onCodeEntered(code)
                } else {
                    Toast.makeText(context, "⚠️ Código deve ter 6 dígitos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar") { _, _ ->
                onCancel()
            }
            .setCancelable(true)
            .show()
    }
    
    /**
     * Copia texto para a área de transferência
     */
    private fun copyToClipboard(context: Context, text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "✅ $label copiado!", Toast.LENGTH_SHORT).show()
    }
}



















