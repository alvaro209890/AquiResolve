package com.aquiresolve.app.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper para gerenciar permissões de localização
 */
object LocationPermissionHelper {
    
    const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    
    /**
     * Verifica se as permissões de localização foram concedidas
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        return fineLocationGranted || coarseLocationGranted
    }
    
    /**
     * Solicita permissões de localização
     */
    fun requestLocationPermission(activity: Activity) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        ActivityCompat.requestPermissions(
            activity,
            permissions.toTypedArray(),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }
    
    /**
     * Verifica se deve mostrar a explicação da permissão
     */
    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    /**
     * Mostra um diálogo explicativo antes de solicitar a permissão
     */
    fun showPermissionRationaleDialog(
        activity: Activity,
        onPositive: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle("📍 Permissão de Localização")
            .setMessage(
                "Para que você possa receber pedidos próximos à sua localização, " +
                "precisamos acessar sua localização em tempo real.\n\n" +
                "Sua localização será atualizada em segundo plano enquanto o rastreamento estiver ativo, " +
                "com uma notificação fixa do AquiResolve.\n\n" +
                "Você pode desativar o compartilhamento de localização a qualquer momento nas configurações."
            )
            .setPositiveButton("Permitir") { _, _ ->
                onPositive()
            }
            .setNegativeButton("Agora Não", null)
            .setCancelable(false)
            .show()
    }
    
    /**
     * Mostra diálogo quando a permissão foi negada permanentemente
     */
    fun showPermissionDeniedDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("⚠️ Permissão Negada")
            .setMessage(
                "A permissão de localização é necessária para que você possa receber pedidos próximos.\n\n" +
                "Para ativar, vá em Configurações do Sistema > Aplicativos > AppServiço > Permissões > Localização."
            )
            .setPositiveButton("Entendi", null)
            .show()
    }
    
    /**
     * Verifica se o GPS está habilitado no dispositivo
     */
    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        }
    }
    
    /**
     * Mostra diálogo solicitando ativação do GPS
     */
    fun showEnableLocationDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("📍 Localização Desativada")
            .setMessage(
                "Para receber pedidos próximos, você precisa ativar a localização do seu dispositivo.\n\n" +
                "Deseja ativar agora?"
            )
            .setPositiveButton("Ativar") { _, _ ->
                val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                activity.startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}



