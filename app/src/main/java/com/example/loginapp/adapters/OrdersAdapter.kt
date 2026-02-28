// TEMPORARIAMENTE COMENTADO PARA PERMITIR BUILD
/*
package com.example.loginapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.loginapp.databinding.ItemOrderBinding
import com.example.loginapp.models.OrderData
import com.example.loginapp.models.OrderStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter para lista de pedidos
 */
class OrdersAdapter(
    private val orders: List<OrderData>,
    private val onOrderClick: (OrderData) -> Unit,
    private val onPrimaryActionClick: (OrderData) -> Unit
) : RecyclerView.Adapter<OrdersAdapter.OrderViewHolder>() {

    class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivServiceIcon: ImageView = itemView.findViewById(R.id.ivServiceIcon)
        val tvServiceNiche: TextView = itemView.findViewById(R.id.tvServiceNiche)
        val tvOrderDate: TextView = itemView.findViewById(R.id.tvOrderDate)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        val tvAddress: TextView = itemView.findViewById(R.id.tvAddress)
        val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        val tvProvider: TextView = itemView.findViewById(R.id.tvProvider)
        val layoutQuotes: LinearLayout = itemView.findViewById(R.id.layoutQuotes)
        val tvQuotesCount: TextView = itemView.findViewById(R.id.tvQuotesCount)
        val tvEmergency: TextView = itemView.findViewById(R.id.tvEmergency)
        val tvServiceType: TextView = itemView.findViewById(R.id.tvServiceType)
        val btnViewDetails: TextView = itemView.findViewById(R.id.btnViewDetails)
        val btnPrimaryAction: TextView = itemView.findViewById(R.id.btnPrimaryAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]
        
        // Configurar dados básicos
        holder.tvServiceNiche.text = order.serviceNiche
        holder.tvDescription.text = order.description
        holder.tvAddress.text = order.address
        
        // Configurar data
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        holder.tvOrderDate.text = dateFormat.format(order.createdAt)
        
        // Configurar ícone do serviço
        setServiceIcon(holder.ivServiceIcon, order.serviceNiche)
        
        // Configurar status
        setStatusInfo(holder.tvStatus, order.status)
        
        // Configurar preço
        setPriceInfo(holder.tvPrice, order)
        
        // Configurar prestador
        setProviderInfo(holder.tvProvider, order)
        
        // Configurar cotações (para serviços complexos)
        setQuotesInfo(holder.layoutQuotes, holder.tvQuotesCount, order)
        
        // Configurar badges
        setBadges(holder.tvEmergency, holder.tvServiceType, order)
        
        // Configurar botões de ação
        setActionButtons(holder.btnViewDetails, holder.btnPrimaryAction, order)
        
        // Configurar click listeners
        holder.itemView.setOnClickListener { onOrderClick(order) }
        holder.btnViewDetails.setOnClickListener { onOrderClick(order) }
        holder.btnPrimaryAction.setOnClickListener { onPrimaryActionClick(order) }
    }

    override fun getItemCount(): Int = orders.size

    /**
     * Define o ícone baseado no nicho de serviço
     */
    private fun setServiceIcon(imageView: ImageView, serviceNiche: String) {
        val iconRes = when (serviceNiche.lowercase()) {
            "elétrica" -> R.drawable.ic_electrician
            "encanador", "hidráulica" -> R.drawable.ic_plumber
            "pintura" -> R.drawable.ic_painter
            "limpeza" -> R.drawable.ic_cleaning
            "jardinagem" -> R.drawable.ic_gardening
            "marcenaria" -> R.drawable.ic_carpentry
            "informática" -> R.drawable.ic_it
            "mudanças" -> R.drawable.ic_moving
            else -> R.drawable.ic_services
        }
        imageView.setImageResource(iconRes)
    }

    /**
     * Define as informações de status
     */
    private fun setStatusInfo(textView: TextView, status: OrderStatus) {
        val (text, backgroundRes) = when (status) {
            OrderStatus.PENDING -> "PENDENTE" to R.drawable.status_pending_background
            OrderStatus.QUOTES_RECEIVED -> "COTAÇÕES" to R.drawable.status_pending_background
            OrderStatus.ASSIGNED -> "ATRIBUIDO" to R.drawable.status_pending_background
            OrderStatus.IN_PROGRESS -> "EM ANDAMENTO" to R.drawable.status_pending_background
            OrderStatus.COMPLETED -> "CONCLUÍDO" to R.drawable.status_pending_background
            OrderStatus.CANCELLED -> "CANCELADO" to R.drawable.status_pending_background
            OrderStatus.EXPIRED -> "EXPIRADO" to R.drawable.status_pending_background
        }
        
        textView.text = text
        textView.setBackgroundResource(backgroundRes)
    }

    /**
     * Define as informações de preço
     */
    private fun setPriceInfo(textView: TextView, order: OrderData) {
        when {
            order.fixedPrice != null -> {
                val totalPrice = order.fixedPrice + (order.distanceFee ?: 0.0)
                textView.text = "R$ %.2f".format(totalPrice).replace(".", ",")
            }
            order.quotes.isNotEmpty() -> {
                val minPrice = order.quotes.minOfOrNull { it.price } ?: 0.0
                textView.text = "A partir de R$ %.2f".format(minPrice).replace(".", ",")
            }
            else -> {
                textView.text = "Aguardando"
            }
        }
    }

    /**
     * Define as informações do prestador
     */
    private fun setProviderInfo(textView: TextView, order: OrderData) {
        when {
            order.assignedProviderName != null -> {
                textView.text = order.assignedProviderName
            }
            order.quotes.isNotEmpty() -> {
                textView.text = "${order.quotes.size} prestador(es)"
            }
            else -> {
                textView.text = "Aguardando"
            }
        }
    }

    /**
     * Define as informações de cotações
     */
    private fun setQuotesInfo(layout: LinearLayout, textView: TextView, order: OrderData) {
        if (order.serviceType == "COMPLEX" && order.quotes.isNotEmpty()) {
            layout.visibility = View.VISIBLE
            textView.text = "${order.quotes.size} proposta(s)"
        } else {
            layout.visibility = View.GONE
        }
    }

    /**
     * Define os badges
     */
    private fun setBadges(emergencyView: TextView, serviceTypeView: TextView, order: OrderData) {
        // Badge de emergência
                        emergencyView.visibility = View.GONE
        
        // Badge de tipo de serviço
        serviceTypeView.text = if (order.serviceType == "SIMPLE") "💰 PREÇO FIXO" else "📋 ORÇAMENTO"
    }

    /**
     * Define os botões de ação
     */
    private fun setActionButtons(viewDetailsBtn: TextView, primaryActionBtn: TextView, order: OrderData) {
        // Botão de ação principal
        val (text, enabled) = when (order.status) {
            OrderStatus.PENDING -> "Aguardando" to false
            OrderStatus.QUOTES_RECEIVED -> "Ver Cotações" to true
            OrderStatus.ASSIGNED -> "Ver Detalhes" to true
            OrderStatus.IN_PROGRESS -> "Acompanhar" to true
            OrderStatus.COMPLETED -> "Avaliar" to true
            OrderStatus.CANCELLED -> "Ver Detalhes" to true
            OrderStatus.EXPIRED -> "Ver Detalhes" to true
        }
        
        primaryActionBtn.text = text
        primaryActionBtn.isEnabled = enabled
        
        if (!enabled) {
            primaryActionBtn.setBackgroundResource(R.drawable.disabled_button_background)
            primaryActionBtn.setTextColor(primaryActionBtn.context.getColor(R.color.gray_400))
        } else {
            primaryActionBtn.setBackgroundResource(R.drawable.primary_button_background)
            primaryActionBtn.setTextColor(primaryActionBtn.context.getColor(R.color.white))
        }
    }
}
*/ 
