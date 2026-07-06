package com.aquiresolve.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utilitário para gerenciar permissões do aplicativo
 */
object PermissionHelper {

    // NOTA: o app NÃO usa mais READ_MEDIA_IMAGES/READ_MEDIA_VIDEO/READ_EXTERNAL_STORAGE.
    // A seleção de imagens usa o Android Photo Picker (ActivityResultContracts.PickVisualMedia),
    // que não exige permissão. Só a câmera continua pedindo permissão (CAMERA).

    /**
     * Verifica se a permissão de câmera foi concedida
     */
    fun isCameraPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Obtém todas as permissões necessárias para o app (apenas câmera — mídia usa o Photo Picker)
     */
    fun getAllRequiredPermissions(): Array<String> {
        return arrayOf(Manifest.permission.CAMERA)
    }

    /**
     * Verifica se precisa solicitar permissão de notificação
     */
    fun needsNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
    
    /**
     * Verifica se a permissão de notificação foi concedida
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Para versões anteriores ao Android 13, não é necessária permissão
        }
    }
}