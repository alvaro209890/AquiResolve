package com.example.loginapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.loginapp.adapters.CartItemsAdapter
import com.example.loginapp.constants.PaymentResultCodes
import com.example.loginapp.databinding.ActivityClientCartBinding
import com.example.loginapp.models.CartItemData
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClientCartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClientCartBinding
    private lateinit var cartAdapter: CartItemsAdapter
    private lateinit var cartManager: FirebaseCartManager
    private lateinit var authManager: FirebaseAuthManager

    private var cartItems: List<CartItemData> = emptyList()
    private var checkoutInProgress = false
    private var checkoutResultProcessed = false

    private val paymentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handlePaymentResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!FirebaseConfig.isInitialized()) {
            FirebaseConfig.initialize(this)
        }

        binding = ActivityClientCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cartManager = FirebaseCartManager()
        authManager = FirebaseAuthManager(this)

        setupRecycler()
        setupClickListeners()
        loadCartItems()
    }

    override fun onResume() {
        super.onResume()
        if (!checkoutInProgress) {
            loadCartItems()
        }
    }

    private fun setupRecycler() {
        cartAdapter = CartItemsAdapter(
            onRemoveClick = { item -> confirmRemoveItem(item) }
        )

        binding.rvCartItems.apply {
            layoutManager = LinearLayoutManager(this@ClientCartActivity)
            adapter = cartAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnContinueShopping.setOnClickListener {
            startActivity(Intent(this, ServicesActivity::class.java))
        }

        binding.btnCheckoutCart.setOnClickListener {
            startCheckout()
        }
    }

    private fun loadCartItems() {
        val userId = authManager.getLocalUserData()?.uid
            ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: return

        setLoading(true)
        lifecycleScope.launch {
            try {
                val result = cartManager.getItems(userId)
                if (result.isSuccess) {
                    cartItems = result.getOrNull() ?: emptyList()
                    cartAdapter.updateItems(cartItems)
                    updateSummary()
                } else {
                    showToast("❌ Erro ao carregar carrinho")
                }
            } catch (e: Exception) {
                showToast("❌ Erro ao carregar carrinho: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun updateSummary() {
        val total = cartItems.sumOf { it.estimatedPrice }

        binding.tvCartItemsCount.text = "${cartItems.size} item(ns)"
        binding.tvCartTotal.text = String.format("R$ %.2f", total)

        val empty = cartItems.isEmpty()
        binding.tvEmptyCart.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvCartItems.visibility = if (empty) View.GONE else View.VISIBLE
        binding.layoutSummary.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun confirmRemoveItem(item: CartItemData) {
        val userId = authManager.getLocalUserData()?.uid
            ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: return

        AlertDialog.Builder(this)
            .setTitle("Remover item")
            .setMessage("Deseja remover este serviço do carrinho?")
            .setPositiveButton("Remover") { _, _ ->
                lifecycleScope.launch {
                    val result = cartManager.removeItem(userId, item.id)
                    if (result.isSuccess) {
                        loadCartItems()
                        showToast("🗑️ Item removido")
                    } else {
                        showToast("❌ Não foi possível remover o item")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startCheckout() {
        if (checkoutInProgress || cartItems.isEmpty()) {
            if (cartItems.isEmpty()) showToast("Seu carrinho está vazio")
            return
        }

        val user = authManager.getLocalUserData()
        val clientName = user?.fullName?.ifBlank { "Usuário" } ?: "Usuário"
        val clientEmail = user?.email ?: ""

        val firstItem = cartItems.first()
        val total = cartItems.sumOf { it.estimatedPrice }

        val intent = Intent(this, PaymentActivity::class.java).apply {
            putExtra(PaymentActivity.EXTRA_ORDER_ID, "cart_checkout")
            putExtra(PaymentActivity.EXTRA_ORDER_DESCRIPTION, "Carrinho (${cartItems.size} serviços)")
            putExtra(PaymentActivity.EXTRA_ORDER_AMOUNT, total)
            putExtra(PaymentActivity.EXTRA_CLIENT_NAME, clientName)
            putExtra(PaymentActivity.EXTRA_CLIENT_EMAIL, clientEmail)
            putExtra(PaymentActivity.EXTRA_CLIENT_PHONE, user?.phone ?: "")
            putExtra(PaymentActivity.EXTRA_CLIENT_ADDRESS, firstItem.address)
            putExtra(PaymentActivity.EXTRA_CLIENT_CITY, firstItem.city)
            putExtra(PaymentActivity.EXTRA_CLIENT_STATE, firstItem.state)
            putExtra(PaymentActivity.EXTRA_CLIENT_CPF, "")
        }

        checkoutInProgress = true
        checkoutResultProcessed = false
        paymentLauncher.launch(intent)
    }

    private fun handlePaymentResult(resultCode: Int, data: Intent?) {
        if (!checkoutInProgress || checkoutResultProcessed) return

        when (resultCode) {
            PaymentResultCodes.RESULT_PAYMENT_SUCCESS -> {
                checkoutResultProcessed = true
                val transactionId = data?.getStringExtra(PaymentResultCodes.EXTRA_TRANSACTION_ID).orEmpty()
                finalizeCheckout(transactionId, "paid")
            }

            PaymentResultCodes.RESULT_PAYMENT_PENDING -> {
                checkoutResultProcessed = true
                val transactionId = data?.getStringExtra(PaymentResultCodes.EXTRA_TRANSACTION_ID).orEmpty()
                finalizeCheckout(transactionId, "pending")
            }

            PaymentResultCodes.RESULT_PAYMENT_FAILED -> {
                checkoutInProgress = false
                val message = data?.getStringExtra(PaymentResultCodes.EXTRA_ERROR_MESSAGE)
                    ?: "Pagamento não aprovado"
                showToast("❌ $message")
            }

            Activity.RESULT_CANCELED -> {
                checkoutInProgress = false
                showToast("Pagamento cancelado")
            }

            else -> {
                checkoutInProgress = false
            }
        }
    }

    private fun finalizeCheckout(transactionId: String, paymentStatus: String) {
        val userId = authManager.getLocalUserData()?.uid
            ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: run {
                showToast("❌ Usuário não autenticado")
                checkoutInProgress = false
                return
            }

        val user = authManager.getLocalUserData()
        val clientName = user?.fullName?.ifBlank { "Usuário" } ?: "Usuário"
        val clientEmail = user?.email ?: ""
        val totalAmount = cartItems.sumOf { it.estimatedPrice }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val checkoutResult = cartManager.checkoutCart(
                    userId = userId,
                    clientName = clientName,
                    clientEmail = clientEmail,
                    transactionId = transactionId,
                    paymentStatus = paymentStatus
                )

                if (checkoutResult.isFailure) {
                    checkoutInProgress = false
                    showToast("❌ Erro ao criar pedidos: ${checkoutResult.exceptionOrNull()?.message}")
                    return@launch
                }

                val createdOrders = checkoutResult.getOrNull() ?: emptyList()
                val protocol = "CART-${SimpleDateFormat("yyyyMMddHHmm", Locale("pt", "BR")).format(Date())}"

                val confirmationIntent = Intent(this@ClientCartActivity, PaymentConfirmationActivity::class.java).apply {
                    putExtra(PaymentConfirmationActivity.EXTRA_TRANSACTION_ID, transactionId)
                    putExtra(PaymentConfirmationActivity.EXTRA_AMOUNT, totalAmount)
                    putExtra(PaymentConfirmationActivity.EXTRA_PAYMENT_METHOD, if (paymentStatus == "pending") "PIX" else "Pagamento Online")
                    putExtra(PaymentConfirmationActivity.EXTRA_SERVICE_TYPE, "Carrinho")
                    putExtra(PaymentConfirmationActivity.EXTRA_SERVICE_DESCRIPTION, "${createdOrders.size} serviço(s) finalizado(s) em conjunto")
                    putExtra(PaymentConfirmationActivity.EXTRA_PROTOCOL, protocol)
                }

                startActivity(confirmationIntent)
                finish()
            } catch (e: Exception) {
                checkoutInProgress = false
                showToast("❌ Erro ao finalizar carrinho: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnCheckoutCart.isEnabled = !loading
        binding.btnContinueShopping.isEnabled = !loading
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
