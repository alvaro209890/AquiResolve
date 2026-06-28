package com.aquiresolve.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.R
import com.aquiresolve.app.models.OrderData
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

/**
 * ProviderOrdersAdapter - Adapter para lista de pedidos do prestador
 *
 * Exibe pedidos disponíveis em cards: "Ver pedido" abre os detalhes (onde o
 * prestador aceita) e "Rejeitar" recusa o pedido só para ele.
 */
class ProviderOrdersAdapter(
    private val orders: List<OrderData>,
    private val onOrderClick: (OrderData) -> Unit,
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
        private val btnViewOrder: MaterialButton = itemView.findViewById(R.id.btnViewOrder)
        private val btnReject: MaterialButton = itemView.findViewById(R.id.btnReject)
        private val actionButtons: View = itemView.findViewById(R.id.actionButtons)

        fun bind(order: OrderData) {
            // Configurar dados do pedido
            tvServiceType.text = order.serviceType
            tvClientName.text = "Cliente: ${order.clientName}"
            tvAddress.text = order.address
            tvDescription.text = order.description
            
            // Mostrar APENAS a comissão do prestador (não o valor total que o cliente pagou)
            if (order.providerCommission > 0) {
                tvPrice.text = "💰 Você ganha: R$ ${String.format(java.util.Locale("pt", "BR"), "%.2f", order.providerCommission)}"
            } else {
                tvPrice.text = "Valor não disponível"
            }
            
            // Formatar data
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            tvDate.text = dateFormat.format(order.createdAt.toDate())
            
            // Status + se o pedido ainda pode ser recusado (ainda não atribuído)
            val rejectable: Boolean = when (order.status.lowercase(Locale.ROOT)) {
                "pending" -> {
                    tvStatus.text = "Pendente"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.warning_color))
                    true
                }
                "available", "distributing" -> {
                    tvStatus.text = "Disponível"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.info_color))
                    true
                }
                "accepted", "assigned", "in_progress" -> {
                    tvStatus.text = "Aceito"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.success_color))
                    false
                }
                "rejected" -> {
                    tvStatus.text = "Recusado"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.error_color))
                    false
                }
                else -> {
                    tvStatus.text = order.status
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                    false
                }
            }

            // "Ver pedido" sempre disponível; "Rejeitar" só enquanto o pedido pode ser recusado.
            btnReject.visibility = if (rejectable) View.VISIBLE else View.GONE
            actionButtons.visibility = View.VISIBLE

            // Listeners — tanto o card quanto "Ver pedido" abrem os detalhes
            cardOrder.setOnClickListener { onOrderClick(order) }
            btnViewOrder.setOnClickListener { onOrderClick(order) }
            btnReject.setOnClickListener { onRejectOrder(order) }
        }
    }
}
