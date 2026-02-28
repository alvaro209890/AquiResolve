package com.example.loginapp.models.payment

import com.google.gson.annotations.SerializedName

/**
 * Modelo para dados do cartão de crédito
 */
data class CardData(
    @SerializedName("card_number")
    val cardNumber: String,
    
    @SerializedName("card_holder_name")
    val cardHolderName: String,
    
    @SerializedName("card_expiration_date")
    val cardExpirationDate: String, // MMYY
    
    @SerializedName("card_cvv")
    val cardCvv: String
)

/**
 * Modelo para informações de cobrança
 */
data class BillingAddress(
    @SerializedName("line_1")
    val line1: String,
    
    @SerializedName("line_2")
    val line2: String? = null,
    
    @SerializedName("zip_code")
    val zipCode: String,
    
    @SerializedName("city")
    val city: String,
    
    @SerializedName("state")
    val state: String,
    
    @SerializedName("country")
    val country: String = "BR"
)

/**
 * Modelo para informações do cliente
 */
data class CustomerInfo(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("document")
    val document: String, // CPF
    
    @SerializedName("document_type")
    val documentType: String = "cpf",
    
    @SerializedName("type")
    val type: String = "individual",
    
    @SerializedName("phones")
    val phones: PhoneInfo
)

/**
 * Modelo para informações de telefone
 */
data class PhoneInfo(
    @SerializedName("mobile_phone")
    val mobilePhone: PhoneDetails
)

data class PhoneDetails(
    @SerializedName("country_code")
    val countryCode: String = "55",
    
    @SerializedName("area_code")
    val areaCode: String,
    
    @SerializedName("number")
    val number: String
)









































