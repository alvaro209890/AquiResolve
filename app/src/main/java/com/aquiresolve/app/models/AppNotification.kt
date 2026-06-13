package com.aquiresolve.app.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class AppNotification(
    @PropertyName("id") val id: String = "",
    @PropertyName("userId") val userId: String = "",
    @PropertyName("title") val title: String = "",
    @PropertyName("message") val message: String = "",
    @PropertyName("isRead") val isRead: Boolean = false,
    @PropertyName("type") val type: String = "general",
    @PropertyName("timestamp") val timestamp: Timestamp = Timestamp.now()
)
