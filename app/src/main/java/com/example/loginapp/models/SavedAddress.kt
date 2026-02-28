package com.example.loginapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName

/**
 * Modelo para endereços salvos do cliente
 */
data class SavedAddress(
    @PropertyName("id")
    val id: String = "",
    @PropertyName("clientId")
    val clientId: String = "",
    @PropertyName("userType")
    val userType: String = USER_TYPE_CLIENT,
    @PropertyName("name")
    val name: String = "", // Nome personalizado do endereço (ex: "Casa", "Trabalho")
    @PropertyName("address")
    val address: String = "",
    @PropertyName("complement")
    val complement: String = "",
    @PropertyName("neighborhood")
    val neighborhood: String = "",
    @PropertyName("city")
    val city: String = "",
    @PropertyName("state")
    val state: String = "",
    @PropertyName("zipCode")
    val zipCode: String = "",
    @PropertyName("coordinates")
    val coordinates: GeoPoint? = null,
    @PropertyName("isDefault")
    val isDefault: Boolean = false,
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),
    @PropertyName("updatedAt")
    val updatedAt: Timestamp = Timestamp.now()
) {
    /**
     * Retorna o endereço completo formatado
     */
    fun getFullAddress(): String {
        val parts = mutableListOf<String>()
        
        if (address.isNotEmpty()) parts.add(address)
        if (complement.isNotEmpty()) parts.add(complement)
        if (neighborhood.isNotEmpty()) parts.add(neighborhood)
        if (city.isNotEmpty()) parts.add(city)
        if (state.isNotEmpty()) parts.add(state)
        if (zipCode.isNotEmpty()) parts.add(zipCode)
        
        return parts.joinToString(", ")
    }
    
    /**
     * Retorna o endereço resumido para exibição em listas
     */
    fun getShortAddress(): String {
        val parts = mutableListOf<String>()
        
        if (address.isNotEmpty()) parts.add(address)
        if (neighborhood.isNotEmpty()) parts.add(neighborhood)
        if (city.isNotEmpty()) parts.add(city)
        
        return parts.joinToString(", ")
    }
    
    companion object {
        const val COLLECTION_NAME = "saved_addresses"
        const val USER_TYPE_CLIENT = "CLIENT"
        const val USER_TYPE_PROVIDER = "PROVIDER"
    }
}


