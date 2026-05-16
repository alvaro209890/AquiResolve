package com.aquiresolve.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.adapters.ProviderDashboardPagerAdapter
import com.aquiresolve.app.databinding.ActivityProviderDashboardBinding
import com.aquiresolve.app.utils.LocationPermissionHelper
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

/**
 * ProviderDashboardActivity - Dashboard do prestador de serviços
 * 
 * Esta activity gerencia:
 * - Lista de pedidos disponíveis para o prestador
 * - Aceitar ou recusar pedidos
 * - Visualizar detalhes dos pedidos
 * - Gerenciar status dos serviços
 * - Rastreamento de localização em tempo real (prestadores verificados)
 */
class ProviderDashboardActivity : AppCompatActivity() {

    // ViewBinding para acesso aos elementos da interface
    private lateinit var binding: ActivityProviderDashboardBinding
    private lateinit var authManager: FirebaseAuthManager
    private lateinit var pagerAdapter: ProviderDashboardPagerAdapter
    private lateinit var orderManager: FirebaseOrderManager
    private var isLocationTrackingRequested = false
    
    companion object {
        private const val TAG = "ProviderDashboard"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Garantir Firebase inicializado
        if (!FirebaseConfig.isInitialized()) {
            FirebaseConfig.initialize(this)
        }
        
        // Inicializar managers
        authManager = FirebaseAuthManager(this)
        orderManager = FirebaseOrderManager()
        
        // Inicializar ViewBinding
        binding = ActivityProviderDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Configurar a interface
        setupUI()
        setupViewPager()
        
        // Carregar estatísticas de pedidos completados
        loadCompletedOrdersCount()
        
        // Configurar rastreamento de localização
        setupLocationTracking()
    }

    /**
     * Configura os elementos da interface do usuário
     */
    private fun setupUI() {
        // Configurar a toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Dashboard Prestador"
        
        // Configurar a status bar
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)
    }

    /**
     * Configura o ViewPager2 com as abas
     */
    private fun setupViewPager() {
        pagerAdapter = ProviderDashboardPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        
        // Configurar as abas
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "Pedidos"
                1 -> tab.text = "Perfil"
            }
        }.attach()
        
        // Verificar se há uma aba padrão especificada
        val defaultTab = intent.getIntExtra("default_tab", 0)
        if (defaultTab in 0..1) {
            binding.viewPager.currentItem = defaultTab
        }
        
        // Verificar se há mensagem de boas-vindas
        val showWelcomeMessage = intent.getBooleanExtra("show_welcome_message", false)
        if (showWelcomeMessage) {
            val welcomeMessage = intent.getStringExtra("welcome_message") ?: "Bem-vindo!"
            showToast(welcomeMessage)
        }
    }


    /**
     * Carrega a contagem de pedidos completados do prestador
     */
    private fun loadCompletedOrdersCount() {
        lifecycleScope.launch {
            try {
                val user = authManager.getCurrentUser()
                if (user == null) {
                    Log.w(TAG, "Usuário não autenticado")
                    return@launch
                }
                
                val result = orderManager.countCompletedOrdersByProvider(user.uid)
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    binding.tvCompletedOrdersCount.text = count.toString()
                    Log.d(TAG, "✅ Pedidos completados carregados: $count")
                } else {
                    Log.e(TAG, "❌ Erro ao carregar pedidos completados: ${result.exceptionOrNull()?.message}")
                    binding.tvCompletedOrdersCount.text = "0"
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar contagem de pedidos: ${e.message}", e)
                binding.tvCompletedOrdersCount.text = "0"
            }
        }
    }
    
    /**
     * Exibe uma mensagem toast para o usuário
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Configura o rastreamento de localização para prestadores verificados.
     * Usa verificationStatus da coleção providers (verified/verificado = aprovado).
     */
    private fun setupLocationTracking() {
        lifecycleScope.launch {
            try {
                val userData = authManager.getLocalUserData()
                if (userData?.userType != FirebaseAuthManager.USER_TYPE_PROVIDER) {
                    Log.d(TAG, "Usuário não é prestador - rastreamento não será ativado")
                    return@launch
                }
                
                // Verificar status na coleção providers
                val verificationStatus = ProviderVerificationManager().getVerificationStatus(userData.uid)
                if (verificationStatus?.status != ProviderVerificationManager.VerificationStatus.APPROVED) {
                    Log.d(TAG, "Prestador não verificado - rastreamento não será ativado")
                    return@launch
                }
                
                Log.d(TAG, "✅ Prestador verificado detectado - configurando rastreamento de localização")
                
                // Verificar se já tem permissão
                if (LocationPermissionHelper.hasLocationPermission(this@ProviderDashboardActivity)) {
                    Log.d(TAG, "✅ Permissão de localização já concedida")
                    startLocationTracking()
                } else {
                    Log.d(TAG, "⚠️ Permissão de localização não concedida - solicitando...")
                    requestLocationPermission()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao configurar rastreamento de localização: ${e.message}", e)
            }
        }
    }
    
    /**
     * Solicita permissão de localização ao usuário
     */
    private fun requestLocationPermission() {
        if (LocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
            // Mostrar explicação antes de solicitar
            LocationPermissionHelper.showPermissionRationaleDialog(this) {
                LocationPermissionHelper.requestLocationPermission(this)
            }
        } else {
            // Solicitar diretamente
            LocationPermissionHelper.requestLocationPermission(this)
        }
    }
    
    /**
     * Inicia o rastreamento de localização
     */
    private fun startLocationTracking() {
        // Verificar se o GPS está ativado
        if (!LocationPermissionHelper.isLocationEnabled(this)) {
            Log.w(TAG, "⚠️ GPS desativado no dispositivo")
            LocationPermissionHelper.showEnableLocationDialog(this)
            return
        }
        
        Log.d(TAG, "🌍 Iniciando rastreamento foreground de localização...")
        ProviderLocationForegroundService.start(this)
        isLocationTrackingRequested = true
        showToast("📍 Rastreamento de localização ativado")
    }
    
    /**
     * Para o rastreamento de localização
     */
    private fun stopLocationTracking() {
        Log.d(TAG, "🛑 Parando rastreamento foreground de localização...")
        ProviderLocationForegroundService.stop(this)
        isLocationTrackingRequested = false
    }
    
    /**
     * Callback de resultado da solicitação de permissão
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            LocationPermissionHelper.LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "✅ Permissão de localização concedida")
                    showToast("✅ Permissão de localização concedida")
                    startLocationTracking()
                } else {
                    Log.w(TAG, "❌ Permissão de localização negada")
                    
                    // Verificar se foi negada permanentemente
                    if (!LocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                        LocationPermissionHelper.showPermissionDeniedDialog(this)
                    } else {
                        showToast("⚠️ Permissão de localização necessária para receber pedidos próximos")
                    }
                }
            }
        }
    }
    
    /**
     * Quando a activity volta para o foreground
     */
    override fun onResume() {
        super.onResume()
        ProviderNewOrderAlertManager.refreshMonitoring()
        
        // Verificar se o GPS foi ativado
        lifecycleScope.launch {
            try {
                val userData = authManager.getLocalUserData()
                val verificationStatus = userData?.uid?.let { ProviderVerificationManager().getVerificationStatus(it) }
                
                if (userData?.userType == FirebaseAuthManager.USER_TYPE_PROVIDER && 
                    verificationStatus?.status == ProviderVerificationManager.VerificationStatus.APPROVED &&
                    LocationPermissionHelper.hasLocationPermission(this@ProviderDashboardActivity) &&
                    !isLocationTrackingRequested) {
                    
                    if (LocationPermissionHelper.isLocationEnabled(this@ProviderDashboardActivity)) {
                        Log.d(TAG, "🔄 Retomando rastreamento de localização...")
                        startLocationTracking()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao retomar rastreamento: ${e.message}", e)
            }
        }
    }
    
    /**
     * Quando a activity vai para o background
     */
    override fun onPause() {
        super.onPause()
        // Manter rastreamento ativo mesmo em background (mas só enquanto app estiver aberto)
        Log.d(TAG, "Activity pausada - rastreamento continua em background")
    }

    /**
     * Limpa os recursos quando a activity é destruída
     */
    override fun onDestroy() {
        super.onDestroy()
        
        // Parar rastreamento ao fechar o app
        if (isFinishing) {
            Log.d(TAG, "🛑 Activity sendo finalizada - parando rastreamento de localização")
            stopLocationTracking()
        }
    }
    
    /**
     * Tratamento do botão voltar
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
