package com.example.loginapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.loginapp.R
import com.example.loginapp.models.OrderData
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

/**
 * ProviderOrdersAdapter - Adapter para lista de pedidos do prestador
 * 
 * Exibe pedidos disponíveis para o prestador com opções de aceitar/recusar
 */
class ProviderOrdersAdapter(
    private val orders: List<OrderData>,
    private val onOrderClick: (OrderData) -> Unit,
    private val onAcceptOrder: (OrderData) -> Unit,
    private val onRejectOrder: (OrderData) -> Unit
) : RecyclerView.Adapter<ProviderOrdersAdapter.OrderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_provider_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]
        holder.bind(order)
    }

    override fun getItemCount(): Int = orders.size

    inner class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardOrder: MaterialCardView = itemView.findViewById(R.id.cardOrder)
        private val tvServiceType: TextView = itemView.findViewById(R.id.tvServiceType)
        private val tvClientName: TextView = itemView.findViewById(R.id.tvClientName)
        private val tvAddress: TextView = itemView.findViewById(R.id.tvAddress)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val btnAccept: MaterialButton = itemView.findViewById(R.id.btnAccept)
        private val btnReject: MaterialButton = itemView.findViewById(R.id.btnReject)

        fun bind(order: OrderData) {
            // Configurar dados do pedido
            tvServiceType.text = order.serviceType
            tvClientName.text = "Cliente: ${order.clientName}"
            tvAddress.text = order.address
            tvDescription.text = order.description
            
            // Mostrar APENAS a comissão do prestador (não o valor total que o cliente pagou)
            if (order.providerCommission > 0) {
                tvPrice.text = "💰 Você ganha: R$ ${String.format("%.2f", order.providerCommission)}"
            } else {
                tvPrice.text = "Valor não disponível"
            }
            
            // Formatar data
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            tvDate.text = dateFormat.format(order.createdAt.toDate())
            
            // Configurar status
            when (order.status) {
                "pending" -> {
                    tvStatus.text = "Pendente"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.warning_color))
                    btnAccept.visibility = View.VISIBLE
                    btnReject.visibility = View.VISIBLE
                }
                "available" -> {
                    tvStatus.text = "Disponível"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.info_color))
                    btnAccept.visibility = View.VISIBLE
                    btnReject.visibility = View.VISIBLE
                }
                "accepted" -> {
                    tvStatus.text = "Aceito"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.success_color))
                    btnAccept.visibility = View.GONE
                    btnReject.visibility = View.GONE
                }
                "rejected" -> {
                    tvStatus.text = "Recusado"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.error_color))
                    btnAccept.visibility = View.GONE
                    btnReject.visibility = View.GONE
                }
                else -> {
                    tvStatus.text = order.status
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                    btnAccept.visibility = View.GONE
                    btnReject.visibility = View.GONE
                }
            }
            
            // Configurar listeners
            cardOrder.setOnClickListener {
                onOrderClick(order)
            }
            
            btnAccept.setOnClickListener {
                onAcceptOrder(order)
            }
            
            btnReject.setOnClickListener {
                onRejectOrder(order)
            }
        }
    }
}
