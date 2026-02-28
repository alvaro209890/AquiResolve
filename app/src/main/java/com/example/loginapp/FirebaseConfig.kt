package com.example.loginapp

import android.content.Context
import android.util.Log
// import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
// Firebase App Check temporariamente desabilitado
// import com.google.firebase.appcheck.FirebaseAppCheck
// import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
// import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import android.content.pm.ApplicationInfo
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.messaging.FirebaseMessaging

object FirebaseConfig {
    
    private const val TAG = "FirebaseConfig"
    
    private var firebaseApp: FirebaseApp? = null
    private var firebaseAuth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var storage: FirebaseStorage? = null
    private var analytics: FirebaseAnalytics? = null
    private var messaging: FirebaseMessaging? = null
    
    fun initialize(context: Context) {
        try {
            Log.d(TAG, "Initializing Firebase...")
            
            // Inicialização padrão; Analytics opcional, Messaging habilitado
            
            // Verificar se o Firebase já foi inicializado
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                firebaseApp = FirebaseApp.getInstance()
                Log.d(TAG, "FirebaseApp already initialized")
            } else {
                firebaseApp = FirebaseApp.initializeApp(context)
                Log.d(TAG, "FirebaseApp initialized successfully")
            }
            
            if (firebaseAuth == null) {
                firebaseAuth = FirebaseAuth.getInstance()
                Log.d(TAG, "FirebaseAuth initialized successfully")
            }
            
            if (firestore == null) {
                firestore = FirebaseFirestore.getInstance()
                Log.d(TAG, "Firestore initialized successfully")
            }
            
            if (storage == null) {
                storage = FirebaseStorage.getInstance()
                Log.d(TAG, "FirebaseStorage initialized successfully")
            }
            
            if (analytics == null) {
                analytics = FirebaseAnalytics.getInstance(context)
                Log.d(TAG, "FirebaseAnalytics initialized successfully")
            }
            if (messaging == null) {
                messaging = FirebaseMessaging.getInstance()
                Log.d(TAG, "FirebaseMessaging initialized successfully")
            }
            
            Log.d(TAG, "Firebase initialization completed successfully")
            
            // Firebase App Check desabilitado para evitar problemas com Google Play Services
            // em builds de debug. Pode ser habilitado em produção se necessário.
            Log.d(TAG, "Firebase App Check disabled for debug builds")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}", e)
            throw e
        }
    }
    
    fun getAuth(): FirebaseAuth {
        return firebaseAuth ?: throw IllegalStateException("Firebase not initialized. Call initialize() first.")
    }
    
    fun getFirestore(): FirebaseFirestore {
        return firestore ?: throw IllegalStateException("Firebase not initialized. Call initialize() first.")
    }
    
    fun getStorage(): FirebaseStorage {
        return storage ?: throw IllegalStateException("Firebase not initialized. Call initialize() first.")
    }
    
    fun getAnalytics(): FirebaseAnalytics? {
        return analytics
    }
    
    fun getMessaging(): FirebaseMessaging? {
        return messaging
    }
    
    fun isInitialized(): Boolean {
        return firebaseApp != null && firebaseAuth != null && firestore != null
    }
    
    // Método temporariamente desabilitado
    // fun isGooglePlayServicesAvailable(context: Context): Boolean {
    //     val googleApiAvailability = GoogleApiAvailability.getInstance()
    //     val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
    //     return resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS
    // }
} 