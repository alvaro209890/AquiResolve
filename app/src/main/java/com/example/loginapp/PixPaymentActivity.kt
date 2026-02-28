package com.example.loginapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.loginapp.databinding.ActivityPixPaymentBinding
import com.example.loginapp.models.payment.CustomerInfo
import com.example.loginapp.models.payment.PhoneDetails
import com.example.loginapp.models.payment.PhoneInfo
import com.example.loginapp.constants.PaymentResultCodes
import com.example.loginapp.payment.PagarMeManager
import com.example.loginapp.payment.PixPaymentResult
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

/**
 * Activity para pagamento via PIX
 */
class PixPaymentActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_ORDER_ID = "order_id"
        const val EXTRA_ORDER_DESCRIPTION = "order_description"
        const val EXTRA_ORDER_AMOUNT = "order_amount"
        const val EXTRA_CLIENT_NAME = "client_name"
        const val EXTRA_CLIENT_EMAIL = "client_email"
        const val EXTRA_CLIENT_PHONE = "client_phone"
        const val EXTRA_CLIENT_CPF = "client_cpf"
    }
    
    private lateinit var binding: ActivityPixPaymentBinding
    private lateinit var pagarMeManager: PagarMeManager
    
    private var orderId: String = ""
    private var orderDescription: String = ""
    private var orderAmount: Double = 0.0
    private var clientName: String = ""
    private var clientEmail: String = ""
    private var clientPhone: String = ""
    private var clientCpf: String = ""
    
    private var currentTransactionId: String? = null
    private var pixCode: String? = null
    private var countDownTimer: CountDownTimer? = null
    private var statusCheckTimer: CountDownTimer? = null
    private var isPaymentConfirmed = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityPixPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        pagarMeManager = PagarMeManager(this)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        
        getIntentData()
        setupUI()
        setupClickListeners()
    }
    
    private fun getIntentData() {
        orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: ""
        orderDescription = intent.getStringExtra(EXTRA_ORDER_DESCRIPTION) ?: ""
        orderAmount = intent.getDoubleExtra(EXTRA_ORDER_AMOUNT, 0.0)
        clientName = intent.getStringExtra(EXTRA_CLIENT_NAME) ?: ""
        clientEmail = intent.getStringExtra(EXTRA_CLIENT_EMAIL) ?: ""
        clientPhone = intent.getStringExtra(EXTRA_CLIENT_PHONE) ?: ""
        clientCpf = intent.getStringExtra(EXTRA_CLIENT_CPF) ?: ""
        
        // Limpar CPF (garantir apenas números)
        clientCpf = clientCpf.replace(Regex("[^\\d]"), "")
    }
    
    private fun setupUI() {
        binding.tvOrderDescription.text = orderDescription
        binding.tvOrderAmount.text = String.format("R$ %.2f", orderAmount)
        
        // Verificar se CPF está vazio
        if (clientCpf.isBlank() || clientCpf.length != 11) {
            android.util.Log.d("PixPayment", "CPF não disponível, mostrando campo de entrada")
            binding.layoutCpfInput.visibility = View.VISIBLE
            
            // Adicionar formatação de CPF
            binding.etCpfInput.addTextChangedListener(object : android.text.TextWatcher {
                private var isUpdating = false
                
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (isUpdating) return
                    isUpdating = true
                    
                    val clean = s.toString().replace(Regex("[^\\d]"), "")
                    val formatted = when {
                        clean.length <= 3 -> clean
                        clean.length <= 6 -> "${clean.substring(0, 3)}.${clean.substring(3)}"
                        clean.length <= 9 -> "${clean.substring(0, 3)}.${clean.substring(3, 6)}.${clean.substring(6)}"
                        else -> "${clean.substring(0, 3)}.${clean.substring(3, 6)}.${clean.substring(6, 9)}-${clean.substring(9, minOf(11, clean.length))}"
                    }
                    
                    binding.etCpfInput.setText(formatted)
                    binding.etCpfInput.setSelection(formatted.length)
                    
                    isUpdating = false
                }
            })
        }
    }
    
    private fun setupClickListeners() {
        binding.btnSwitchToCard.setOnClickListener {
            switchToCardPayment()
        }
        
        binding.btnGeneratePix.setOnClickListener {
            generatePixPayment()
        }
        
        binding.btnCopyPixCode.setOnClickListener {
            copyPixCodeToClipboard()
        }
        
        binding.btnCheckPayment.setOnClickListener {
            checkPaymentStatus()
        }
    }
    
    /**
     * Voltar para pagamento com cartão
     */
    private fun switchToCardPayment() {
        AlertDialog.Builder(this)
            .setTitle("Trocar para Cartão?")
            .setMessage("Deseja pagar com cartão de crédito ao invés de PIX?")
            .setPositiveButton("Sim, usar Cartão") { _, _ ->
                finish() // Volta para PaymentActivity
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun generatePixPayment() {
        // Se o campo de CPF está visível, pegar o valor dele
        if (binding.layoutCpfInput.visibility == View.VISIBLE) {
            val inputCpf = binding.etCpfInput.text.toString().replace(Regex("[^\\d]"), "")
            
            if (inputCpf.isBlank()) {
                binding.tilCpfInput.error = "Informe seu CPF"
                return
            }
            
            if (inputCpf.length != 11) {
                binding.tilCpfInput.error = "CPF inválido - deve ter 11 dígitos"
                return
            }
            
            clientCpf = inputCpf
            binding.tilCpfInput.error = null
        }
        
        // Validar CPF final
        val cleanCpf = clientCpf.replace(Regex("[^\\d]"), "")
        
        if (cleanCpf.isBlank()) {
            showError("CPF não informado. Por favor, preencha seu CPF acima.")
            return
        }
        
        if (cleanCpf.length != 11) {
            showError("CPF inválido (${cleanCpf.length} dígitos). Deve ter 11 dígitos.")
            return
        }
        
        // Atualizar clientCpf com valor limpo
        clientCpf = cleanCpf
        
        setLoading(true)
        
        lifecycleScope.launch {
            try {
                // Limpar e preparar telefone
                val cleanPhone = clientPhone.replace(Regex("[^\\d]"), "")
                val areaCode = if (cleanPhone.length >= 2) cleanPhone.substring(0, 2) else "11"
                val phoneNumber = if (cleanPhone.length > 2) cleanPhone.substring(2) else "999999999"
                
                // Preparar dados do cliente
                val customerInfo = CustomerInfo(
                    name = clientName,
                    email = clientEmail,
                    document = clientCpf,
                    documentType = "cpf",
                    type = "individual",
                    phones = PhoneInfo(
                        mobilePhone = PhoneDetails(
                            countryCode = "55",
                            areaCode = areaCode,
                            number = phoneNumber
                        )
                    )
                )
                
                // Processar PIX
                val result = pagarMeManager.processPixPayment(
                    customerInfo = customerInfo,
                    amount = orderAmount,
                    description = orderDescription,
                    orderId = orderId
                )
                
                handlePixResult(result)
                
            } catch (e: Exception) {
                setLoading(false)
                showError("Erro ao gerar PIX: ${e.localizedMessage}")
            }
        }
    }
    
    private fun handlePixResult(result: PixPaymentResult) {
        setLoading(false)
        
        when (result) {
            is PixPaymentResult.Success -> {
                currentTransactionId = result.transactionId
                pixCode = result.qrCode
                
                android.util.Log.d("PixPayment", "PIX gerado")
                
                // Exibir QR Code
                displayQrCode(result.qrCode)
                
                // Iniciar contagem regressiva
                startExpirationTimer(result.expiresAt)
                
                // ✅ Iniciar verificação automática a cada 5 segundos
                startAutomaticStatusCheck()
                
                // Mostrar layout do QR Code
                binding.btnGeneratePix.visibility = View.GONE
                binding.layoutQrCode.visibility = View.VISIBLE
                binding.layoutAutoCheck.visibility = View.VISIBLE
                
                Toast.makeText(this, "✅ QR Code gerado! Escaneie para pagar.\nVerificação automática ativada.", Toast.LENGTH_LONG).show()
            }
            
            is PixPaymentResult.Paid -> {
                showPaymentSuccess(result.transactionId)
            }
            
            is PixPaymentResult.Error -> {
                showError(result.message)
            }
        }
    }
    
    private fun displayQrCode(qrCodeText: String) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(qrCodeText, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            
            binding.ivQrCode.setImageBitmap(bmp)
            binding.tvPixCode.text = qrCodeText
        } catch (e: Exception) {
            showError("Erro ao gerar QR Code: ${e.localizedMessage}")
        }
    }
    
    private fun startExpirationTimer(expiresAt: String?) {
        // Por simplicidade, vamos usar 1 hora (3600 segundos)
        countDownTimer?.cancel()
        
        countDownTimer = object : CountDownTimer(3600000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding.tvExpiresAt.text = String.format("⏰ Expira em: %02d:%02d", minutes, seconds)
            }
            
            override fun onFinish() {
                binding.tvExpiresAt.text = "⏰ Código PIX expirado"
                showError("O código PIX expirou. Gere um novo código.")
            }
        }.start()
    }
    
    private fun copyPixCodeToClipboard() {
        pixCode?.let { code ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Código PIX", code)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Código PIX copiado!", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Iniciar verificação automática do status a cada 5 segundos
     */
    private fun startAutomaticStatusCheck() {
        android.util.Log.d("PixPayment", "Iniciando verificação automática de status (a cada 5s)")
        
        statusCheckTimer?.cancel()
        
        statusCheckTimer = object : CountDownTimer(3600000, 5000) {  // 1 hora, verifica a cada 5 segundos
            override fun onTick(millisUntilFinished: Long) {
                if (!isPaymentConfirmed) {
                    checkPaymentStatus(isAutomatic = true)
                }
            }
            
            override fun onFinish() {
                android.util.Log.d("PixPayment", "Tempo de verificação expirado")
            }
        }.start()
    }
    
    private fun checkPaymentStatus(isAutomatic: Boolean = false) {
        currentTransactionId?.let { transactionId ->
            if (!isAutomatic) {
                setLoading(true)
            }
            
            android.util.Log.d("PixPayment", "Verificando status do pagamento... (auto: $isAutomatic)")
            
            lifecycleScope.launch {
                try {
                    val result = pagarMeManager.checkPixPaymentStatus(transactionId)
                    
                    when (result) {
                        is PixPaymentResult.Paid -> {
                            setLoading(false)
                            
                            // Prevenir múltiplas confirmações
                            if (isPaymentConfirmed) {
                                android.util.Log.d("PixPayment", "⚠️ Pagamento já confirmado anteriormente, ignorando")
                                return@launch
                            }
                            
                            isPaymentConfirmed = true
                            statusCheckTimer?.cancel()
                            
                            android.util.Log.d("PixPayment", "✅✅✅ PAGAMENTO CONFIRMADO! ✅✅✅")
                            showPaymentSuccess(transactionId)
                        }
                        is PixPaymentResult.Success -> {
                            setLoading(false)
                            if (!isAutomatic) {
                                Toast.makeText(this@PixPaymentActivity, "⏳ Aguardando pagamento...", Toast.LENGTH_SHORT).show()
                            }
                            android.util.Log.d("PixPayment", "Status ainda: pending (aguardando)")
                        }
                        is PixPaymentResult.Error -> {
                            setLoading(false)
                            android.util.Log.e("PixPayment", "Erro ao verificar: ${result.message}")
                            if (!isAutomatic) {
                                showError(result.message)
                            }
                        }
                    }
                } catch (e: Exception) {
                    setLoading(false)
                    android.util.Log.e("PixPayment", "Exceção ao verificar status", e)
                    if (!isAutomatic) {
                        showError("Erro ao verificar status: ${e.localizedMessage}")
                    }
                }
            }
        }
    }
    
    private fun showPaymentSuccess(transactionId: String) {
        countDownTimer?.cancel()
        statusCheckTimer?.cancel()
        
        // Esconder indicador de verificação automática
        binding.layoutAutoCheck.visibility = View.GONE
        
        android.util.Log.d("PixPayment", "Exibindo confirmação de pagamento")
        
        // Tocar vibração longa para chamar atenção
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Vibração mais perceptível: 3 pulsos
                val pattern = longArrayOf(0, 200, 100, 200, 100, 400)
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 200, 100, 200, 100, 400), -1)
            }
        } catch (e: Exception) {
            android.util.Log.e("PixPayment", "Erro ao vibrar", e)
        }
        
        // Definir resultado COM CÓDIGO EXPLÍCITO
        val resultIntent = Intent().apply {
            putExtra(PaymentResultCodes.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(PaymentResultCodes.EXTRA_PAYMENT_STATUS, "paid")
        }
        
        // IMPORTANTE: Definir resultado ANTES de navegar
        setResult(PaymentResultCodes.RESULT_PAYMENT_SUCCESS, resultIntent)
        
        android.util.Log.d("PixPayment", "Resultado do pagamento definido")
        
        // Finalizar para retornar resultado ao CreateOrderActivity
        finish()
    }
    
    /**
     * Navegar para tela de confirmação de pagamento
     */
    private fun navigateToPaymentConfirmation(transactionId: String) {
        android.util.Log.d("PixPayment", "🎉 Navegando para tela de confirmação de pagamento")
        
        val confirmationIntent = Intent(this, PaymentConfirmationActivity::class.java).apply {
            putExtra(PaymentConfirmationActivity.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(PaymentConfirmationActivity.EXTRA_AMOUNT, orderAmount)
            putExtra(PaymentConfirmationActivity.EXTRA_PAYMENT_METHOD, "PIX")
            putExtra(PaymentConfirmationActivity.EXTRA_SERVICE_DESCRIPTION, orderDescription)
            // Protocolo será gerado na tela de confirmação
        }
        startActivity(confirmationIntent)
        finish()
    }
    
    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("❌ Erro")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun setLoading(loading: Boolean) {
        binding.layoutLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGeneratePix.isEnabled = !loading
        binding.btnCheckPayment.isEnabled = !loading
    }
    
    override fun onBackPressed() {
        // Se pagamento já foi confirmado, não mostrar dialog
        if (isPaymentConfirmed) {
            android.util.Log.d("PixPayment", "⚠️ onBackPressed ignorado - pagamento já confirmado")
            return
        }
        
        android.util.Log.d("PixPayment", "⚠️ onBackPressed - mostrando dialog de confirmação")
        
        AlertDialog.Builder(this)
            .setTitle("⚠️ Cancelar Pagamento PIX?")
            .setMessage("Se você sair agora sem pagar, seu pedido NÃO será criado.\n\nPara criar o pedido, é necessário efetuar o pagamento.")
            .setPositiveButton("Sair e Cancelar") { _, _ ->
                // Retornar como cancelado
                android.util.Log.d("PixPayment", "🚫 Usuário confirmou cancelamento - setando RESULT_CANCELED")
                setResult(RESULT_CANCELED)
                super.onBackPressed()
            }
            .setNegativeButton("Continuar Aqui", null)
            .show()
    }
    
    override fun onDestroy() {
        android.util.Log.d("PixPayment", "🔴 onDestroy() chamado")
        
        super.onDestroy()
        countDownTimer?.cancel()
        statusCheckTimer?.cancel()
        
        android.util.Log.d("PixPayment", "🔴 onDestroy() finalizado")
    }
    
    override fun onStop() {
        android.util.Log.d("PixPayment", "🟡 onStop() chamado - isPaymentConfirmed: $isPaymentConfirmed")
        super.onStop()
    }
    
    override fun onPause() {
        android.util.Log.d("PixPayment", "🟠 onPause() chamado - isPaymentConfirmed: $isPaymentConfirmed")
        super.onPause()
    }
}

