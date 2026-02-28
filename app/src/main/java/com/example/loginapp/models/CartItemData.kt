package com.example.loginapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName

/**
 * Item de pedido adicionado ao carrinho do cliente.
 */
data class CartItemData(
    @PropertyName("id")
    val id: String = "",
    @PropertyName("clientId")
    val clientId: String = "",
    @PropertyName("serviceType")
    val serviceType: String = "",
    @PropertyName("serviceNiche")
    val serviceNiche: String = "",
    @PropertyName("description")
    val description: String = "",
    @PropertyName("address")
    val address: String = "",
    @PropertyName("zipCode")
    val zipCode: String = "",
    @PropertyName("complement")
    val complement: String = "",
    @PropertyName("city")
    val city: String = "",
    @PropertyName("state")
    val state: String = "",
    @PropertyName("coordinates")
    val coordinates: GeoPoint? = null,
    @PropertyName("imageUrls")
    val imageUrls: List<String> = emptyList(),
    @PropertyName("preferredDate")
    val preferredDate: Timestamp? = null,
    @PropertyName("preferredTime")
    val preferredTime: String? = null,
    @PropertyName("estimatedPrice")
    val estimatedPrice: Double = 0.0,
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),
    @PropertyName("updatedAt")
    val updatedAt: Timestamp = Timestamp.now()
)
