package com.aquiresolve.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.databinding.ItemNotificationBinding
import com.aquiresolve.app.models.AppNotification
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationHistoryAdapter(
    private val items: List<AppNotification>
) : RecyclerView.Adapter<NotificationHistoryAdapter.VH>() {

    private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(private val b: ItemNotificationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(n: AppNotification) {
            b.tvTitle.text = n.title
            b.tvMessage.text = n.message
            b.tvDate.text = dateFmt.format(n.timestamp.toDate())
            b.tvIcon.text = when (n.type) {
                "order" -> "📦"
                "payment" -> "💰"
                "cashback" -> "💸"
                "promo" -> "🎉"
                else -> "🔔"
            }
            b.root.alpha = if (n.isRead) 0.6f else 1f
        }
    }
}
