package com.example.loginapp

import android.app.Application
import android.util.Log

class AppApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseConfig.initialize(this)
            NotificationManager.createNotificationChannels(this)
            ProviderNewOrderAlertManager.initialize(this)
            Log.d("AppApplication", "Firebase initialized in Application.onCreate")
        } catch (e: Exception) {
            Log.e("AppApplication", "Error initializing Firebase: ${e.message}", e)
        }
    }
}
