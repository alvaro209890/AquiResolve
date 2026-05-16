package com.aquiresolve.app.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.R
import com.aquiresolve.app.adapters.ImageAdapter
import com.aquiresolve.app.databinding.ItemOrderDetailedBinding
import com.aquiresolve.app.models.OrderData
import com.aquiresolve.app.utils.PriceFormatter
import com.aquiresolve.app.utils.ProtocolGenerator
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter para exibir pedidos com detalhes completos
 */
class DetailedOrdersAdapter(
    private val context: Context,
    private var orders: List<OrderData>,
    private val onOrderClick: (OrderData) -> Unit,
    private val onPrimaryActionClick: (OrderData) -> Unit,
    private val onSecondaryActionClick: (OrderData) -> Unit,
    private val isProviderContext: Boolean = false
) : RecyclerView.Adapter<DetailedOrdersAdapter.OrderViewHolder>() {

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
    private val dateOnlyFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    inner class OrderViewHolder(private val binding: ItemOrderDetailedBinding) : RecyclerView.ViewHolder(binding.root) {

        private val imageAdapter: ImageAdapter by lazy {
            ImageAdapter(
                context = context,
                imageUrls = emptyList(),
                onImageClick = { imageUrl, position -> },
                onImageLongClick = { imageUrl, position -> }
            ).also {
                binding.rvOrderImages.layoutManager = GridLayoutManager(context, 3)
                binding.rvOrderImages.adapter = it
            }
        }
        
        fun bind(order: OrderData) {
            // ID e Data do Pedido
            binding.tvOrderId.text = "Pedido #${order.id.takeLast(8).uppercase()}"
            
            // Protocolo
            if (order.protocol.isNotEmpty()) {
                binding.tvOrderProtocol.text = "Protocolo: ${ProtocolGenerator.formatProtocolForDisplay(order.protocol)}"
                binding.tvOrderProtocol.visibility = View.VISIBLE
            } else {
                binding.tvOrderProtocol.visibility = View.GONE
            }
            
            val createdAt = order.createdAt?.toDate()
            if (createdAt != null) {
                binding.tvOrderDate.text = "Criado em ${dateFormatter.format(createdAt)}"
            } else {
                binding.tvOrderDate.text = "Data não disponível"
            }

            // Status do Pedido
            setupOrderStatus(order.status)

            // Preço do Pedido
            setupOrderPrice(order)

            // Detalhes do Serviço
            binding.tvServiceType.text = order.serviceType.ifEmpty { "Não especificado" }
            binding.tvServiceNiche.text = order.serviceName.ifEmpty { "Não especificado" }
            binding.tvServicePrice.text = formatOrderPrice(order)
            binding.tvDescription.text = order.description.ifEmpty { "Descrição não fornecida" }

            // Localização
            binding.tvAddress.text = order.address.ifEmpty { "Endereço não fornecido" }
            binding.tvZipCode.text = order.zipCode.ifEmpty { "CEP não fornecido" }
            
            // Complemento (se existir)
            if (!order.complement.isNullOrEmpty()) {
                binding.layoutComplement.visibility = View.VISIBLE
                binding.tvComplement.text = order.complement
            } else {
                binding.layoutComplement.visibility = View.GONE
            }

            // Informações de Distribuição
            setupDistributionInfo(order)

            // Informações Adicionais
            binding.tvEmergency.text = "Normal"
            
            // Data e Horário Preferidos (se existirem)
            if (order.scheduledDate != null) {
                binding.tvPreferredDate.text = dateOnlyFormatter.format(order.scheduledDate.toDate())
            } else {
                binding.tvPreferredDate.text = "Não especificada"
            }
            
            binding.tvPreferredTime.text = order.preferredTimeSlot.ifEmpty { "Não especificado" }

            // Configurar imagens
            setupOrderImages(order)

            // Configurar Ações
            setupActions(order)

            // Click listeners
            itemView.setOnClickListener { onOrderClick(order) }
            binding.btnPrimaryAction.setOnClickListener { onPrimaryActionClick(order) }
            binding.btnSecondaryAction.setOnClickListener { onSecondaryActionClick(order) }
        }

        private fun setupOrderStatus(status: String?) {
            val statusText = when (status) {
                OrderData.STATUS_AWAITING_PAYMENT -> "AGUARDANDO PAGAMENTO"
                "distributing" -> "EM DISTRIBUIÇÃO"
                "pending" -> "AGUARDANDO PRESTADOR"
                "quotes_received" -> "COTAÇÕES RECEBIDAS"
                "assigned" -> "ATRIBUIDO"
                "in_progress" -> "EM ANDAMENTO"
                "completed" -> "CONCLUÍDO"
                "cancelled" -> "CANCELADO"
                "expired" -> "EXPIRADO"
                else -> "EM DISTRIBUIÇÃO"
            }

            val backgroundColor = when (status) {
                OrderData.STATUS_AWAITING_PAYMENT -> R.drawable.status_pending_improved_background
                "distributing" -> R.drawable.status_pending_improved_background
                "pending" -> R.drawable.status_pending_improved_background
                "quotes_received" -> R.drawable.status_pending_improved_background
                "assigned" -> R.drawable.status_pending_improved_background
                "in_progress" -> R.drawable.status_pending_improved_background
                "completed" -> R.drawable.status_completed_background
                "cancelled" -> R.drawable.status_cancelled_background
                "expired" -> R.drawable.status_cancelled_background
                else -> R.drawable.status_pending_improved_background
            }

            binding.tvOrderStatus.text = statusText
            binding.tvOrderStatus.setBackgroundResource(backgroundColor)
            
            // Definir cor do texto baseada no status
            val textColor = when (status) {
                "cancelled", "expired" -> R.color.white
                "completed" -> R.color.white
                OrderData.STATUS_AWAITING_PAYMENT, "distributing", "pending", "quotes_received", "assigned", "in_progress" -> R.color.white
                else -> R.color.white
            }
            
            binding.tvOrderStatus.setTextColor(ContextCompat.getColor(context, textColor))
        }

        @Suppress("DEPRECATION")
        private fun setupOrderPrice(order: OrderData) {
            // Preço estimado para o cliente
            if (order.estimatedPrice > 0) {
                binding.tvOrderPrice.text = PriceFormatter.format(order.estimatedPrice)
            } else if (order.finalPrice != null && order.finalPrice > 0) {
                binding.tvOrderPrice.text = PriceFormatter.format(order.finalPrice)
            } else {
                binding.tvOrderPrice.text = "A consultar"
            }

            // Comissão do prestador
            if (order.providerCommission > 0) {
                binding.tvProviderCommission.text = PriceFormatter.format(order.providerCommission)
            } else {
                binding.tvProviderCommission.text = "—"
            }
        }

        private fun setupDistributionInfo(order: OrderData) {
            // Status de distribuição
            val distributionStatus = when (order.status) {
                OrderData.STATUS_AWAITING_PAYMENT -> "Aguardando confirmação do pagamento"
                "distributing" -> "Em distribuição"
                "pending" -> "Aguardando resposta do prestador"
                "assigned" -> "Atribuído a um prestador"
                "in_progress" -> "Em andamento"
                "completed" -> "Concluído"
                "cancelled" -> "Cancelado"
                "expired" -> "Expirado"
                else -> "Em distribuição"
            }
            binding.tvDistributionStatus.text = distributionStatus

            // Prestador atribuído
            if (!order.assignedProviderName.isNullOrEmpty()) {
                binding.layoutAssignedProvider.visibility = View.VISIBLE
                binding.tvAssignedProvider.text = order.assignedProviderName
            } else {
                binding.layoutAssignedProvider.visibility = View.VISIBLE
                binding.tvAssignedProvider.text = when (order.status) {
                    OrderData.STATUS_AWAITING_PAYMENT -> "Aguardando pagamento"
                    "distributing" -> "Não atribuído"
                    "pending" -> "Não atribuído"
                    "cancelled" -> "Cancelado"
                    "expired" -> "Expirado"
                    else -> "Não atribuído"
                }
            }

            // Data de início da distribuição
            val distributionDate = order.distributionStartedAt.toDate()
            binding.tvDistributionDate.text = dateFormatter.format(distributionDate)
        }
        
        private fun setupOrderImages(order: OrderData) {
            val imageUrls = order.images ?: emptyList()
            if (imageUrls.isNotEmpty()) {
                binding.layoutImages.visibility = View.VISIBLE
                imageAdapter.updateImages(imageUrls)
            } else {
                binding.layoutImages.visibility = View.GONE
            }
        }
        
        private fun setupActions(order: OrderData) {
            if (isProviderContext) {
                when (order.status) {
                    "distributing", "pending" -> {
                        binding.btnPrimaryAction.text = "Aceitar Pedido"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_color))
                        binding.btnSecondaryAction.text = "Ver Detalhes"
                    }
                    "assigned", "in_progress" -> {
                        binding.btnPrimaryAction.text = "Chat"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_color))
                        binding.btnSecondaryAction.text = "Ver Detalhes"
                    }
                    "completed" -> {
                        binding.btnPrimaryAction.text = "Ver Detalhes"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.secondary_color))
                        binding.btnSecondaryAction.text = "—"
                    }
                    else -> {
                        binding.btnPrimaryAction.text = "Ver Detalhes"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_color))
                        binding.btnSecondaryAction.text = "—"
                    }
                }
            } else {
                when (order.status) {
                    OrderData.STATUS_AWAITING_PAYMENT -> {
                        binding.btnPrimaryAction.text = "Aguardando Pagamento"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_color))
                        binding.btnSecondaryAction.text = "Cancelar Pedido"
                    }
                    "distributing" -> {
                        binding.btnPrimaryAction.text = "Cancelar Pedido"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.error_color))
                        binding.btnSecondaryAction.text = "Ver Detalhes"
                    }
                    "pending" -> {
                        binding.btnPrimaryAction.text = "Cancelar Pedido"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.error_color))
                        binding.btnSecondaryAction.text = "Ver Detalhes"
                    }
                    "quotes_received" -> {
                        binding.btnPrimaryAction.text = "Ver Cotações"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_color))
                        binding.btnSecondaryAction.text = "Ver Detalhes"
                    }
                    "assigned" -> {
                        binding.btnPrimaryAction.text = "Chat com Prestador"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_color))
                        binding.btnSecondaryAction.text = "Ver Detalhes"
                    }
                    "in_progress" -> {
                        binding.btnPrimaryAction.text = "Acompanhar"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_color))
                        binding.btnSecondaryAction.text = "Chat"
                    }
                    "completed" -> {
                        binding.btnPrimaryAction.text = "Avaliar"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.secondary_color))
                        binding.btnSecondaryAction.text = "Ver Detalhes"
                    }
                    "cancelled" -> {
                        binding.btnPrimaryAction.text = "Novo Pedido"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_color))
                        binding.btnSecondaryAction.text = "Ver Detalhes"
                    }
                    "expired" -> {
                        binding.btnPrimaryAction.text = "Novo Pedido"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_color))
                        binding.btnSecondaryAction.text = "Ver Detalhes"
                    }
                    else -> {
                        binding.btnPrimaryAction.text = "Ver Detalhes"
                        binding.btnPrimaryAction.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_color))
                        binding.btnSecondaryAction.text = "Ações"
                    }
                }
            }
        }

        private fun formatOrderPrice(order: OrderData): String {
            return PriceFormatter.formatOrderPrice(order)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderDetailedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    fun updateOrders(newOrders: List<OrderData>) {
        android.util.Log.d("DetailedOrdersAdapter", "🔄 Atualizando adapter")
        android.util.Log.d("DetailedOrdersAdapter", "📊 Pedidos anteriores: ${orders.size}")
        android.util.Log.d("DetailedOrdersAdapter", "📊 Novos pedidos: ${newOrders.size}")
        android.util.Log.d("DetailedOrdersAdapter", "📋 Status dos novos pedidos: ${newOrders.map { it.status }}")
        
        orders = newOrders
        
        android.util.Log.d("DetailedOrdersAdapter", "🔄 Chamando notifyDataSetChanged()")
        notifyDataSetChanged()
        
        android.util.Log.d("DetailedOrdersAdapter", "✅ Adapter atualizado. Item count: ${itemCount}")
    }
}
