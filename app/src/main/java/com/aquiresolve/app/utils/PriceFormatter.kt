package com.aquiresolve.app.utils

import com.aquiresolve.app.models.OrderData
import com.aquiresolve.app.models.ServicePricing
import java.text.NumberFormat
import java.util.Locale

object PriceFormatter {
    private val CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    fun format(value: Double): String {
        return CURRENCY_FORMAT.format(value)
    }

    fun formatOrderPrice(order: OrderData): String {
        val price = when {
            order.finalPrice != null && order.finalPrice > 0 -> order.finalPrice
            order.estimatedPrice > 0 -> order.estimatedPrice
            else -> ServicePricing.getPrice(
                category = order.serviceName,
                serviceType = order.serviceType
            ) ?: ServicePricing.getDefaultPrice(order.serviceName)
        }
        return format(price)
    }
}
