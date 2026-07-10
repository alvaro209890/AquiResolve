package com.aquiresolve.app

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Aplica a preferência de tema (claro/escuro/sistema) antes de qualquer UI.
        ThemeManager.applyStored(this)
        // Insets globais para o edge-to-edge forçado do Android 15+ (targetSdk 35):
        // impede que as barras do sistema cubram topo/rodapé de qualquer tela.
        EdgeToEdgeInsets.install(this)
        try {
            FirebaseConfig.initialize(this)
            NotificationManager.createNotificationChannels(this)
            ProviderNewOrderAlertManager.initialize(this)
            // Carrega o catálogo de serviços (nichos) do painel admin para o matching e os spinners.
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    CatalogRepository.load()
                } catch (e: Exception) {
                    Log.w("AppApplication", "Falha ao pré-carregar catálogo: ${e.message}")
                }
            }
            // Pré-aquece o carrossel de banners da Home (fallback silencioso se vazio/offline).
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    BannerRepository.load()
                } catch (e: Exception) {
                    Log.w("AppApplication", "Falha ao pré-carregar banners: ${e.message}")
                }
            }
            // Pré-aquece o catálogo de serviços inteiro para a Busca Inteligente da Home.
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    CatalogServiceRepository.loadAll()
                } catch (e: Exception) {
                    Log.w("AppApplication", "Falha ao pré-carregar serviços p/ busca: ${e.message}")
                }
            }
            // Pré-aquece a vitrine de combos promocionais da Home (fallback silencioso se vazio/offline).
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    ComboRepository.load()
                } catch (e: Exception) {
                    Log.w("AppApplication", "Falha ao pré-carregar combos: ${e.message}")
                }
            }
            // Pré-aquece a seção de parceiros patrocinadores da Home (fallback silencioso se vazio/offline).
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    PartnerRepository.load()
                } catch (e: Exception) {
                    Log.w("AppApplication", "Falha ao pré-carregar parceiros: ${e.message}")
                }
            }
            Log.d("AppApplication", "Firebase initialized in Application.onCreate")
        } catch (e: Exception) {
            Log.e("AppApplication", "Error initializing Firebase: ${e.message}", e)
            throw IllegalStateException(
                "Critical startup failure. Verify app/google-services.json for package com.aquiresolve.app " +
                    "and download the correct file from Firebase Console.",
                e
            )
        }
    }
}
