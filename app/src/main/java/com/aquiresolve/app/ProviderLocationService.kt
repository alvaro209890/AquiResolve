package com.aquiresolve.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Serviço de rastreamento de localização para prestadores verificados
 * Atualiza a localização do prestador no Firebase a cada 5 minutos
 */
class ProviderLocationService(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var locationCallback: LocationCallback? = null
    private var isTracking = false
    
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    companion object {
        private const val TAG = "ProviderLocationService"
        private const val UPDATE_INTERVAL = 5 * 60 * 1000L // 5 minutos em milissegundos
        private const val FASTEST_INTERVAL = 2 * 60 * 1000L // 2 minutos (mínimo)
        
        @Volatile
        private var instance: ProviderLocationService? = null
        
        fun getInstance(context: Context): ProviderLocationService {
            return instance ?: synchronized(this) {
                instance ?: ProviderLocationService(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    /**
     * Verifica se o prestador está verificado e pode ter localização rastreada.
     * Usa verificationStatus da coleção providers (verified/verificado = aprovado).
     */
    private suspend fun isProviderVerified(): Boolean {
        return try {
            val userId = auth.currentUser?.uid ?: return false
            
            val doc = firestore.collection("providers")
                .document(userId)
                .get()
                .await()
            
            if (!doc.exists()) return false
            
            val status = doc.getString("verificationStatus")?.lowercase() ?: "pending"
            status == "verified" || status == "verificado"
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar status do prestador: ${e.message}")
            false
        }
    }
    
    /**
     * Inicia o rastreamento de localização
     */
    @SuppressLint("MissingPermission")
    fun startLocationTracking() {
        if (isTracking) {
            Log.d(TAG, "Rastreamento já está ativo")
            return
        }
        
        scope.launch {
            // Verificar se é prestador verificado
            if (!isProviderVerified()) {
                Log.w(TAG, "⚠️ Prestador não verificado - rastreamento de localização não será iniciado")
                return@launch
            }
            
            Log.d(TAG, "🌍 Iniciando rastreamento de localização para prestador verificado...")
            
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                UPDATE_INTERVAL
            ).apply {
                setMinUpdateIntervalMillis(FASTEST_INTERVAL)
                setWaitForAccurateLocation(false)
                setMaxUpdateDelayMillis(UPDATE_INTERVAL)
            }.build()
            
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        Log.d(TAG, "📍 Nova localização recebida: ${location.latitude}, ${location.longitude}")
                        updateProviderLocation(location)
                    }
                }
            }
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            
            isTracking = true
            Log.d(TAG, "✅ Rastreamento de localização iniciado (atualização a cada 5 minutos)")
        }
    }
    
    /**
     * Para o rastreamento de localização
     */
    fun stopLocationTracking() {
        if (!isTracking) {
            Log.d(TAG, "Rastreamento já está parado")
            return
        }
        
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            Log.d(TAG, "🛑 Rastreamento de localização parado")
        }
        
        locationCallback = null
        isTracking = false
    }

    /**
     * Libera todos os recursos: para tracking + cancela scope.
     * Deve ser chamado quando o prestador faz logout ou o app é destruído.
     */
    fun shutdown() {
        stopLocationTracking()
        scope.cancel()
        Log.d(TAG, "🔌 ProviderLocationService encerrado")
    }
    
    /**
     * Atualiza a localização do prestador no Firebase
     */
    private fun updateProviderLocation(location: Location) {
        scope.launch {
            try {
                val userId = auth.currentUser?.uid ?: run {
                    Log.e(TAG, "❌ Usuário não autenticado")
                    return@launch
                }
                
                // Criar GeoPoint com as coordenadas
                val coordinates = GeoPoint(location.latitude, location.longitude)
                
                // Preparar dados de localização
                val locationData = hashMapOf(
                    "coordinates" to coordinates,
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracy" to location.accuracy,
                    "lastLocationUpdate" to com.google.firebase.Timestamp.now(),
                    "locationEnabled" to true
                )
                
                // Atualizar no Firestore
                firestore.collection("users")
                    .document(userId)
                    .update(locationData as Map<String, Any>)
                    .await()
                
                Log.d(TAG, "✅ Localização atualizada no Firebase:")
                Log.d(TAG, "   📍 Latitude: ${location.latitude}")
                Log.d(TAG, "   📍 Longitude: ${location.longitude}")
                Log.d(TAG, "   🎯 Precisão: ${location.accuracy}m")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao atualizar localização no Firebase: ${e.message}", e)
            }
        }
    }
    
    /**
     * Obtém a última localização conhecida e atualiza no Firebase
     */
    @SuppressLint("MissingPermission")
    fun updateLastKnownLocation() {
        scope.launch {
            try {
                if (!isProviderVerified()) {
                    Log.w(TAG, "⚠️ Prestador não verificado")
                    return@launch
                }
                
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        Log.d(TAG, "📍 Última localização conhecida obtida")
                        updateProviderLocation(it)
                    } ?: run {
                        Log.w(TAG, "⚠️ Última localização não disponível")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao obter última localização: ${e.message}", e)
            }
        }
    }
    
    /**
     * Verifica se o rastreamento está ativo
     */
    fun isTracking(): Boolean = isTracking
    
    /**
     * Desativa temporariamente a localização do prestador
     */
    fun disableLocation() {
        scope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                
                firestore.collection("users")
                    .document(userId)
                    .update("locationEnabled", false)
                    .await()
                
                Log.d(TAG, "📴 Localização desativada para o prestador")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao desativar localização: ${e.message}")
            }
        }
    }
    
    /**
     * Reativa a localização do prestador
     */
    fun enableLocation() {
        scope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                
                firestore.collection("users")
                    .document(userId)
                    .update("locationEnabled", true)
                    .await()
                
                Log.d(TAG, "📡 Localização reativada para o prestador")
                updateLastKnownLocation()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao reativar localização: ${e.message}")
            }
        }
    }
}

