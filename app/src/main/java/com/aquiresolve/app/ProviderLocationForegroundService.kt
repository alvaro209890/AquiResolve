package com.aquiresolve.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Foreground Service real para rastreamento contínuo de localização do prestador.
 *
 * Android 10+ restringe localização em background. Para manter updates confiáveis
 * enquanto o app está minimizado, o rastreamento precisa rodar como Foreground
 * Service com notificação persistente e foregroundServiceType="location" no Manifest.
 */
class ProviderLocationForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private var locationCallback: LocationCallback? = null
    private var isTracking = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTrackingAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startTrackingIfAllowed()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    private fun startTrackingIfAllowed() {
        if (isTracking) {
            Log.d(TAG, "Rastreamento foreground já está ativo")
            return
        }

        if (!hasLocationPermission()) {
            Log.w(TAG, "Sem permissão de localização; encerrando foreground service")
            stopSelf()
            return
        }

        serviceScope.launch {
            if (!isProviderVerified()) {
                Log.w(TAG, "Prestador não verificado; rastreamento foreground não será iniciado")
                stopSelf()
                return@launch
            }

            val request = LocationRequest.Builder(
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
                        Log.d(TAG, "Localização foreground: ${location.latitude}, ${location.longitude}")
                        updateProviderLocation(location)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback as LocationCallback,
                Looper.getMainLooper()
            )

            isTracking = true
            updateLocationEnabled(true)
            updateLastKnownLocation()
            Log.d(TAG, "Rastreamento foreground iniciado")
        }
    }

    private fun stopTrackingAndSelf() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        locationCallback = null
        isTracking = false
        serviceScope.launch { updateLocationEnabled(false) }
        stopForegroundCompat()
        stopSelf()
        Log.d(TAG, "Rastreamento foreground parado")
    }

    private fun updateLastKnownLocation() {
        if (!hasLocationPermission()) return
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { updateProviderLocation(it) }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Falha ao obter última localização: ${e.message}", e)
        }
    }

    private fun updateProviderLocation(location: Location) {
        serviceScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                val data = mapOf(
                    "coordinates" to GeoPoint(location.latitude, location.longitude),
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracy" to location.accuracy,
                    "lastLocationUpdate" to Timestamp.now(),
                    "locationEnabled" to true
                )
                firestore.collection("users").document(userId).update(data).await()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao atualizar localização do prestador: ${e.message}", e)
            }
        }
    }

    private suspend fun updateLocationEnabled(enabled: Boolean) {
        try {
            val userId = auth.currentUser?.uid ?: return
            firestore.collection("users").document(userId)
                .update("locationEnabled", enabled)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar locationEnabled=$enabled: ${e.message}", e)
        }
    }

    private suspend fun isProviderVerified(): Boolean {
        return try {
            val userId = auth.currentUser?.uid ?: return false
            val doc = firestore.collection("providers").document(userId).get().await()
            val status = doc.getString("verificationStatus")?.lowercase() ?: return false
            status == "verified" || status == "verificado"
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar prestador: ${e.message}", e)
            false
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("AquiResolve compartilhando localização")
        .setContentText("Sua localização está ativa para pedidos e acompanhamento do cliente.")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, ProviderDashboardActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun createLocationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rastreamento de localização",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação exibida enquanto o prestador compartilha localização."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        isTracking = false
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ProviderLocationFgService"
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 4201
        private const val UPDATE_INTERVAL = 5 * 60 * 1000L
        private const val FASTEST_INTERVAL = 2 * 60 * 1000L

        const val ACTION_START = "com.aquiresolve.app.location.START"
        const val ACTION_STOP = "com.aquiresolve.app.location.STOP"

        fun start(context: Context) {
            val intent = Intent(context, ProviderLocationForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProviderLocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao solicitar parada do foreground service: ${e.message}", e)
            }
        }
    }
}
