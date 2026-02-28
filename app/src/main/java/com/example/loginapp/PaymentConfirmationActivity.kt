package com.example.loginapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.loginapp.databinding.ActivityPaymentConfirmationBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tela de confirmação de pagamento com comprovante.
 * Exibe os detalhes do pagamento e permite compartilhar como imagem.
 */
class PaymentConfirmationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_PAYMENT_METHOD = "payment_method"
        const val EXTRA_CARD_LAST_DIGITS = "card_last_digits"
        const val EXTRA_SERVICE_TYPE = "service_type"
        const val EXTRA_SERVICE_DESCRIPTION = "service_description"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_PAYMENT_DATE = "payment_date"
    }

    private lateinit var binding: ActivityPaymentConfirmationBinding

    // Dados do pagamento
    private var transactionId: String = ""
    private var amount: Double = 0.0
    private var paymentMethod: String = ""
    private var cardLastDigits: String = ""
    private var serviceType: String = ""
    private var serviceDescription: String = ""
    private var protocol: String = ""
    private var paymentDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        getIntentData()
        setupUI()
        setupClickListeners()
    }

    /**
     * Obter dados passados pela intent
     */
    private fun getIntentData() {
        transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID) ?: ""
        amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        paymentMethod = intent.getStringExtra(EXTRA_PAYMENT_METHOD) ?: "Cartão de Crédito"
        cardLastDigits = intent.getStringExtra(EXTRA_CARD_LAST_DIGITS) ?: ""
        serviceType = intent.getStringExtra(EXTRA_SERVICE_TYPE) ?: ""
        serviceDescription = intent.getStringExtra(EXTRA_SERVICE_DESCRIPTION) ?: ""
        protocol = intent.getStringExtra(EXTRA_PROTOCOL) ?: ""
        paymentDate = intent.getStringExtra(EXTRA_PAYMENT_DATE) ?: getCurrentDateTime()
    }

    /**
     * Configurar elementos da UI
     */
    private fun setupUI() {
        // Data e hora do pagamento
        binding.tvPaymentDate.text = paymentDate

        // Valor
        binding.tvAmount.text = String.format("R$ %.2f", amount)

        // Método de pagamento
        binding.tvPaymentMethod.text = paymentMethod

        // Informações do cartão (se aplicável)
        if (cardLastDigits.isNotEmpty()) {
            binding.layoutCardInfo.visibility = View.VISIBLE
            binding.tvCardInfo.text = "**** **** **** $cardLastDigits"
        } else {
            binding.layoutCardInfo.visibility = View.GONE
        }

        // ID da transação
        binding.tvTransactionId.text = transactionId

        // Tipo de serviço
        binding.tvServiceType.text = serviceType.ifEmpty { "Serviço Geral" }

        // Descrição do serviço
        binding.tvServiceDescription.text = serviceDescription.ifEmpty { "-" }

        // Protocolo
        binding.tvProtocol.text = protocol.ifEmpty { generateProtocol() }
    }

    /**
     * Configurar listeners de clique
     */
    private fun setupClickListeners() {
        // Botão compartilhar
        binding.btnShare.setOnClickListener {
            shareReceipt()
        }

        // Botão voltar à tela inicial
        binding.btnGoHome.setOnClickListener {
            navigateToHome()
        }
    }

    /**
     * Compartilhar comprovante como imagem
     */
    private fun shareReceipt() {
        try {
            // Esconder botões temporariamente para a captura
            binding.btnShare.visibility = View.INVISIBLE
            binding.btnGoHome.visibility = View.INVISIBLE

            // Capturar a view como bitmap
            val view = binding.contentToShare
            val bitmap = getBitmapFromView(view)

            // Restaurar botões
            binding.btnShare.visibility = View.VISIBLE
            binding.btnGoHome.visibility = View.VISIBLE

            // Salvar bitmap em arquivo temporário
            val file = saveBitmapToFile(bitmap)

            if (file != null) {
                // Criar URI para compartilhamento
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )

                // Criar intent de compartilhamento
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Comprovante de Pagamento - Aqui Resolve")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Comprovante de pagamento\n" +
                                "Valor: R$ ${String.format("%.2f", amount)}\n" +
                                "Protocolo: $protocol\n" +
                                "Data: $paymentDate\n\n" +
                                "Aqui Resolve - www.aquiresolve.com.br"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(shareIntent, "Compartilhar Comprovante"))
            } else {
                Toast.makeText(this, "Erro ao gerar comprovante", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao compartilhar: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            
            // Restaurar botões em caso de erro
            binding.btnShare.visibility = View.VISIBLE
            binding.btnGoHome.visibility = View.VISIBLE
        }
    }

    /**
     * Capturar view como bitmap
     */
    private fun getBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(
            view.width,
            view.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    /**
     * Salvar bitmap em arquivo temporário
     */
    private fun saveBitmapToFile(bitmap: Bitmap): File? {
        return try {
            val fileName = "comprovante_${System.currentTimeMillis()}.png"
            val cacheDir = File(cacheDir, "receipts")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val file = File(cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Navegar para tela inicial
     */
    private fun navigateToHome() {
        val intent = Intent(this, ClientHomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    /**
     * Obter data/hora atual formatada
     */
    private fun getCurrentDateTime(): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        return dateFormat.format(Date())
    }

    /**
     * Gerar protocolo se não fornecido
     */
    private fun generateProtocol(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale("pt", "BR"))
        val date = dateFormat.format(Date())
        val random = (1000..9999).random()
        return "AR-$date-$random"
    }

    /**
     * Desabilitar botão voltar do sistema
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Não permitir voltar - deve usar o botão "Voltar à Tela Inicial"
        navigateToHome()
    }
}
