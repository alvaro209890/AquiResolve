package com.aquiresolve.app

import android.app.Activity
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.constants.PaymentResultCodes
import com.aquiresolve.app.databinding.ActivityTowingOrderBinding
import com.aquiresolve.app.models.OrderData
import com.aquiresolve.app.utils.ProtocolGenerator
import com.aquiresolve.app.utils.awaitCurrentUser
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.NumberFormat
import java.util.Locale

/**
 * Fluxo dedicado do GUINCHO: o cliente escolhe a ORIGEM (onde está o veículo) e o
 * DESTINO; o app calcula a rota de carro (origem→destino) via [RouteClient] e o
 * preço = taxa de saída + R$/km × distância (config em `app_config/guincho`).
 * O preço final é reconfirmado no backend (`/pricing/calculate` com distanceKm)
 * antes de criar o pedido.
 */
class TowingOrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTowingOrderBinding

    private var originPoint: GeoPoint? = null
    private var destinationPoint: GeoPoint? = null
    private var originAddress: String = ""
    private var destinationAddress: String = ""

    private var config: TowingConfig = TowingConfig()
    private var lastRoute: RouteClient.Route? = null

    // Preço definitivo (vindo do backend) usado para criar o pedido.
    private var finalPrice: Double = 0.0
    private var finalProviderCommission: Double = 0.0

    private var paymentProcessed = false

    private val currency: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    private val pickOriginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val lat = result.data?.getDoubleExtra(AddressMapPickerActivity.EXTRA_LAT, Double.NaN)
            val lng = result.data?.getDoubleExtra(AddressMapPickerActivity.EXTRA_LNG, Double.NaN)
            if (lat != null && lng != null && !lat.isNaN() && !lng.isNaN()) {
                originPoint = GeoPoint(lat, lng)
                resolveAddressAndRefresh(GeoPoint(lat, lng), isOrigin = true)
            }
        }
    }

    private val pickDestinationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val lat = result.data?.getDoubleExtra(AddressMapPickerActivity.EXTRA_LAT, Double.NaN)
            val lng = result.data?.getDoubleExtra(AddressMapPickerActivity.EXTRA_LNG, Double.NaN)
            if (lat != null && lng != null && !lat.isNaN() && !lng.isNaN()) {
                destinationPoint = GeoPoint(lat, lng)
                resolveAddressAndRefresh(GeoPoint(lat, lng), isOrigin = false)
            }
        }
    }

    private val paymentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handlePaymentResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        binding = ActivityTowingOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnPickOrigin.setOnClickListener {
            pickOriginLauncher.launch(Intent(this, AddressMapPickerActivity::class.java))
        }
        binding.btnPickDestination.setOnClickListener {
            pickDestinationLauncher.launch(Intent(this, AddressMapPickerActivity::class.java))
        }
        binding.btnRequest.setOnClickListener { startCheckout() }

        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)

        lifecycleScope.launch {
            config = TowingConfigRepository.load()
        }
    }

    private fun resolveAddressAndRefresh(point: GeoPoint, isOrigin: Boolean) {
        lifecycleScope.launch {
            val label = reverseGeocode(point)
            if (isOrigin) {
                originAddress = label
                binding.tvOriginAddress.text = label
            } else {
                destinationAddress = label
                binding.tvDestinationAddress.text = label
            }
            recalculate()
        }
    }

    private suspend fun reverseGeocode(point: GeoPoint): String = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val results = Geocoder(this@TowingOrderActivity, Locale("pt", "BR"))
                .getFromLocation(point.latitude, point.longitude, 1)
            val addr = results?.firstOrNull()
            if (addr != null) {
                val line = addr.getAddressLine(0)
                if (!line.isNullOrBlank()) return@withContext line
                listOfNotNull(addr.thoroughfare, addr.subLocality, addr.locality)
                    .joinToString(", ")
                    .ifBlank { "Local selecionado" }
            } else {
                "Lat %.5f, Lng %.5f".format(point.latitude, point.longitude)
            }
        } catch (_: Exception) {
            "Lat %.5f, Lng %.5f".format(point.latitude, point.longitude)
        }
    }

    /** Quando origem e destino existem: busca a rota, recalcula o preço no backend e mostra a quebra. */
    private fun recalculate() {
        val from = originPoint ?: return
        val to = destinationPoint ?: run {
            binding.tvHint.text = "Agora escolha o destino."
            return
        }

        binding.progressRoute.visibility = View.VISIBLE
        binding.tvHint.visibility = View.GONE
        binding.btnRequest.isEnabled = false

        lifecycleScope.launch {
            val route = RouteClient.fetchRoute(from, to)
            binding.progressRoute.visibility = View.GONE

            if (route == null) {
                binding.tvHint.visibility = View.VISIBLE
                binding.tvHint.text = "Não foi possível calcular o trajeto agora. Tente novamente."
                return@launch
            }
            lastRoute = route
            drawRoute(route)

            // Preço definitivo do backend (fonte da verdade). Fallback: config local.
            val km = route.distanceKm
            val pricing = withContext(Dispatchers.IO) {
                runCatching {
                    com.aquiresolve.app.payment.PagarMeManager(this@TowingOrderActivity)
                        .calculateServicePricing("Guincho", "Transporte de veículo", km)
                }.getOrNull()
            }

            val total: Double
            val commission: Double
            when (pricing) {
                is com.aquiresolve.app.payment.PricingResult.Success -> {
                    total = pricing.estimatedPrice
                    commission = pricing.providerCommission
                }
                else -> {
                    total = config.estimatePrice(km)
                    commission = total * config.providerPercent / 100.0
                }
            }
            finalPrice = total
            finalProviderCommission = commission

            bindPriceBreakdown(km, total)
            binding.btnRequest.isEnabled = true
        }
    }

    private fun bindPriceBreakdown(km: Double, total: Double) {
        val distanceCost = (total - config.baseFee).coerceAtLeast(0.0)
        binding.priceCard.visibility = View.VISIBLE
        binding.tvBaseFee.text = currency.format(config.baseFee)
        binding.tvDistanceLine.text = "Trajeto (%.1f km × %s)".format(km, currency.format(config.pricePerKm))
        binding.tvDistanceCost.text = currency.format(distanceCost)
        binding.tvTotal.text = currency.format(total)
    }

    private fun drawRoute(route: RouteClient.Route) {
        val map = binding.map
        map.overlays.clear()

        originPoint?.let { o ->
            map.overlays.add(Marker(map).apply {
                position = o
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Origem"
            })
        }
        destinationPoint?.let { d ->
            map.overlays.add(Marker(map).apply {
                position = d
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Destino"
            })
        }
        map.overlays.add(Polyline().apply {
            outlinePaint.color = ContextCompat.getColor(this@TowingOrderActivity, R.color.primary_color)
            outlinePaint.strokeWidth = 8f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            setPoints(route.points)
        })

        binding.mapCard.visibility = View.VISIBLE
        val box = BoundingBox.fromGeoPoints(route.points).increaseByScale(1.4f)
        val apply = Runnable { runCatching { map.zoomToBoundingBox(box, false) } }
        if (map.width > 0 && map.height > 0) apply.run() else map.post(apply)
        map.invalidate()
    }

    private fun startCheckout() {
        val from = originPoint
        val to = destinationPoint
        val route = lastRoute
        if (from == null || to == null || route == null || finalPrice <= 0.0) {
            toast("Escolha origem e destino primeiro.")
            return
        }

        binding.btnRequest.isEnabled = false
        lifecycleScope.launch {
            var createdOrderId: String? = null
            try {
                val user = FirebaseAuth.getInstance().awaitCurrentUser()
                if (user == null) {
                    toast("Usuário não autenticado")
                    binding.btnRequest.isEnabled = true
                    return@launch
                }

                val db = FirebaseFirestore.getInstance()
                val userDoc = db.collection("users").document(user.uid).get().await()
                val userName = userDoc.getString("fullName") ?: "Usuário"
                val userEmail = user.email ?: ""
                val userCpf = userDoc.getString("cpf") ?: ""

                val km = route.distanceKm
                val protocol = ProtocolGenerator.generateProtocol()
                val now = Timestamp.now()
                val orderRef = db.collection("orders").document()
                createdOrderId = orderRef.id

                val order = mutableMapOf<String, Any>(
                    "clientId" to user.uid,
                    "clientName" to userName,
                    "clientEmail" to userEmail,
                    "protocol" to protocol,
                    "serviceType" to "Guincho",
                    "serviceName" to "Transporte de veículo",
                    "serviceCategory" to "Guincho",
                    "description" to "Guincho de \"$originAddress\" até \"$destinationAddress\" (%.1f km)".format(km),
                    "address" to originAddress.ifBlank { "Origem do guincho" },
                    "city" to "",
                    "state" to "",
                    "originAddress" to originAddress,
                    "destinationAddress" to destinationAddress,
                    "originLocation" to com.google.firebase.firestore.GeoPoint(from.latitude, from.longitude),
                    "destinationLocation" to com.google.firebase.firestore.GeoPoint(to.latitude, to.longitude),
                    "coordinates" to com.google.firebase.firestore.GeoPoint(from.latitude, from.longitude),
                    "distanceKm" to km,
                    "status" to OrderData.STATUS_AWAITING_PAYMENT,
                    "paymentStatus" to OrderData.STATUS_AWAITING_PAYMENT,
                    "estimatedPrice" to finalPrice,
                    "providerCommission" to finalProviderCommission,
                    "createdAt" to now,
                    "updatedAt" to now
                )
                user.phoneNumber?.takeIf { it.isNotBlank() }?.let { order["clientPhone"] = it }

                orderRef.set(order).await()
                getSharedPreferences("pending_order_prefs", MODE_PRIVATE)
                    .edit().putString("orderId", orderRef.id).apply()
                paymentProcessed = false

                val intent = Intent(this@TowingOrderActivity, PaymentActivity::class.java).apply {
                    putExtra(PaymentActivity.EXTRA_ORDER_ID, orderRef.id)
                    putExtra(PaymentActivity.EXTRA_ORDER_DESCRIPTION, "Guincho - Transporte de veículo")
                    putExtra(PaymentActivity.EXTRA_ORDER_AMOUNT, finalPrice)
                    putExtra(PaymentActivity.EXTRA_CLIENT_NAME, userName)
                    putExtra(PaymentActivity.EXTRA_CLIENT_EMAIL, userEmail)
                    putExtra(PaymentActivity.EXTRA_CLIENT_PHONE, user.phoneNumber ?: "")
                    putExtra(PaymentActivity.EXTRA_CLIENT_ADDRESS, originAddress)
                    putExtra(PaymentActivity.EXTRA_CLIENT_CITY, "")
                    putExtra(PaymentActivity.EXTRA_CLIENT_STATE, "")
                    putExtra(PaymentActivity.EXTRA_CLIENT_CPF, userCpf.replace(Regex("[^\\d]"), ""))
                }
                paymentLauncher.launch(intent)
            } catch (e: Exception) {
                createdOrderId?.let { runCatching { FirebaseFirestore.getInstance().collection("orders").document(it).delete() } }
                clearPendingSession()
                binding.btnRequest.isEnabled = true
                toast("Erro ao processar pedido: ${e.message}")
            }
        }
    }

    private fun handlePaymentResult(resultCode: Int, data: Intent?) {
        if (paymentProcessed) return
        paymentProcessed = true

        val transactionId = data?.getStringExtra(PaymentResultCodes.EXTRA_TRANSACTION_ID).orEmpty()
        val paymentStatus = data?.getStringExtra(PaymentResultCodes.EXTRA_PAYMENT_STATUS).orEmpty()
        val paymentMethod = data?.getStringExtra(PaymentResultCodes.EXTRA_PAYMENT_METHOD)
            ?.takeIf { it.isNotBlank() } ?: "Pagamento Online"

        when (resultCode) {
            PaymentResultCodes.RESULT_PAYMENT_SUCCESS ->
                finalizeAfterPayment(transactionId, paymentStatus.ifBlank { "paid" }, paymentMethod)
            PaymentResultCodes.RESULT_PAYMENT_PENDING ->
                finalizeAfterPayment(transactionId, "pending", paymentMethod)
            PaymentResultCodes.RESULT_PAYMENT_FAILED ->
                cancelPendingOrder("❌ Pagamento Recusado", "Não foi possível processar o pagamento. Tente novamente.")
            Activity.RESULT_CANCELED ->
                cancelPendingOrder("❌ Pedido Cancelado", "O pagamento não foi realizado. Seu pedido foi cancelado.")
            else -> paymentProcessed = false
        }
    }

    private fun finalizeAfterPayment(transactionId: String, paymentStatus: String, paymentMethod: String) {
        lifecycleScope.launch {
            try {
                val orderId = getPendingOrderId() ?: throw IllegalStateException("Pedido pendente não encontrado")

                if (transactionId.isNotBlank()) {
                    runCatching {
                        com.aquiresolve.app.payment.PagarMeManager(this@TowingOrderActivity)
                            .checkPixPaymentStatus(transactionId)
                    }
                }

                val db = FirebaseFirestore.getInstance()
                val order = db.collection("orders").document(orderId).get().await()
                    .toObject(OrderData::class.java)?.copy(id = orderId)
                    ?: throw IllegalStateException("Pedido não encontrado após pagamento")

                clearPendingSession()

                val intent = Intent(this@TowingOrderActivity, PaymentConfirmationActivity::class.java).apply {
                    putExtra(PaymentConfirmationActivity.EXTRA_TRANSACTION_ID, transactionId)
                    putExtra(PaymentConfirmationActivity.EXTRA_AMOUNT, order.estimatedPrice)
                    putExtra(
                        PaymentConfirmationActivity.EXTRA_PAYMENT_METHOD,
                        if (paymentStatus == "pending") "$paymentMethod (Pendente)" else paymentMethod
                    )
                    putExtra(PaymentConfirmationActivity.EXTRA_SERVICE_TYPE, order.serviceName.ifEmpty { order.serviceType })
                    putExtra(PaymentConfirmationActivity.EXTRA_SERVICE_DESCRIPTION, order.description)
                    putExtra(PaymentConfirmationActivity.EXTRA_PROTOCOL, order.protocol)
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                toast("O pagamento foi processado, mas houve um erro ao sincronizar. Confira em Meus Pedidos.")
            }
        }
    }

    private fun cancelPendingOrder(title: String, message: String) {
        lifecycleScope.launch {
            getPendingOrderId()?.let { id ->
                runCatching { FirebaseFirestore.getInstance().collection("orders").document(id).delete() }
            }
            clearPendingSession()
            if (isFinishing) return@launch
            AlertDialog.Builder(this@TowingOrderActivity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK") { _, _ -> binding.btnRequest.isEnabled = true }
                .setCancelable(false)
                .show()
        }
    }

    private fun getPendingOrderId(): String? =
        getSharedPreferences("pending_order_prefs", MODE_PRIVATE).getString("orderId", null)

    private fun clearPendingSession() {
        getSharedPreferences("pending_order_prefs", MODE_PRIVATE).edit().remove("orderId").apply()
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    companion object {
        fun isTowingCategory(category: String?): Boolean {
            val v = category?.trim()?.lowercase() ?: return false
            return v.contains("guincho") || v.contains("reboque")
        }
    }
}
