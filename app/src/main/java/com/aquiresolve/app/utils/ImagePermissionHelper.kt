package com.aquiresolve.app.utils

import android.Manifest
import android.app.Activity
import androidx.activity.ComponentActivity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Helper para gerenciar permissões relacionadas a imagens
 * 
 * Funcionalidades:
 * - Verificação de permissões
 * - Solicitação de permissões
 * - Navegação para configurações
 * - Diálogos informativos
 */
class ImagePermissionHelper {
    
    companion object {
        private const val TAG = "ImagePermissionHelper"
        
        // Só a câmera exige permissão. A galeria usa o Android Photo Picker
        // (ActivityResultContracts.PickVisualMedia), que dispensa permissão de mídia —
        // por isso READ_MEDIA_IMAGES/READ_EXTERNAL_STORAGE foram removidas do app.
        val CAMERA_PERMISSION = Manifest.permission.CAMERA

        /**
         * Verifica se a permissão de câmera está concedida.
         */
        fun hasCameraPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(context, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Verifica se permissão específica está concedida
         */
        fun hasPermission(context: Context, permission: String): Boolean {
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        /**
         * Verifica se deve mostrar explicação para permissão
         */
        fun shouldShowRationale(activity: Activity, permission: String): Boolean {
            return activity.shouldShowRequestPermissionRationale(permission)
        }
        
        /**
         * Verifica se permissão foi negada permanentemente
         */
        fun isPermissionPermanentlyDenied(activity: Activity, permission: String): Boolean {
            return !activity.shouldShowRequestPermissionRationale(permission) &&
                   !hasPermission(activity, permission)
        }
        
        /**
         * Mostra diálogo explicativo sobre permissões
         */
        fun showPermissionRationaleDialog(
            context: Context,
            onPositiveClick: () -> Unit,
            onNegativeClick: () -> Unit = {}
        ) {
            MaterialAlertDialogBuilder(context)
                .setTitle("Permissões Necessárias")
                .setMessage("Este aplicativo precisa de acesso à câmera e galeria para que você possa adicionar fotos aos seus pedidos e perfil.")
                .setPositiveButton("Entendi") { _, _ -> onPositiveClick() }
                .setNegativeButton("Cancelar") { _, _ -> onNegativeClick() }
                .setCancelable(false)
                .show()
        }
        
        /**
         * Mostra diálogo para ir às configurações
         */
        fun showSettingsDialog(
            context: Context,
            onPositiveClick: () -> Unit,
            onNegativeClick: () -> Unit = {}
        ) {
            MaterialAlertDialogBuilder(context)
                .setTitle("Permissões Negadas")
                .setMessage("As permissões foram negadas. Para usar esta funcionalidade, você precisa habilitar as permissões nas configurações do aplicativo.")
                .setPositiveButton("Configurações") { _, _ -> onPositiveClick() }
                .setNegativeButton("Cancelar") { _, _ -> onNegativeClick() }
                .setCancelable(false)
                .show()
        }
        
        /**
         * Abre configurações do aplicativo
         */
        fun openAppSettings(context: Context) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        }
    }
}

/**
 * Extension para Activity com gerenciamento de permissões
 */
class ActivityPermissionManager(private val activity: ComponentActivity) {
    
    private var permissionCallback: ((Boolean) -> Unit)? = null
    
    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { granted -> granted }
            permissionCallback?.invoke(allGranted)
        }
    
    /**
     * Solicita permissões com callback
     */
    fun requestPermissions(
        permissions: Array<String>,
        onResult: (Boolean) -> Unit
    ) {
        permissionCallback = onResult
        permissionLauncher.launch(permissions)
    }
    
    /**
     * Verifica e solicita a permissão de CÂMERA se necessário.
     * A galeria não passa por aqui: usa o Android Photo Picker, que dispensa permissão.
     */
    fun checkAndRequestCameraPermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit = {}
    ) {
        if (ImagePermissionHelper.hasCameraPermission(activity)) {
            onGranted()
        } else {
            requestPermissions(arrayOf(ImagePermissionHelper.CAMERA_PERMISSION)) { granted ->
                if (granted) onGranted() else onDenied()
            }
        }
    }
}

/**
 * Extension para Fragment com gerenciamento de permissões
 */
class FragmentPermissionManager(private val fragment: Fragment) {
    
    private var permissionCallback: ((Boolean) -> Unit)? = null
    
    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            permissionCallback?.invoke(allGranted)
        }
    
    /**
     * Solicita permissões com callback
     */
    fun requestPermissions(
        permissions: Array<String>,
        onResult: (Boolean) -> Unit
    ) {
        permissionCallback = onResult
        permissionLauncher.launch(permissions)
    }
    
    /**
     * Verifica e solicita a permissão de CÂMERA se necessário.
     * A galeria não passa por aqui: usa o Android Photo Picker, que dispensa permissão.
     */
    fun checkAndRequestCameraPermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit = {}
    ) {
        if (ImagePermissionHelper.hasCameraPermission(fragment.requireContext())) {
            onGranted()
        } else {
            requestPermissions(arrayOf(ImagePermissionHelper.CAMERA_PERMISSION)) { granted ->
                if (granted) onGranted() else onDenied()
            }
        }
    }
}
