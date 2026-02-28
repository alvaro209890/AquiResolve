package com.example.loginapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.loginapp.R
import com.example.loginapp.databinding.ItemCartOrderBinding
import com.example.loginapp.models.CartItemData

class CartItemsAdapter(
    private val onRemoveClick: (CartItemData) -> Unit
) : RecyclerView.Adapter<CartItemsAdapter.CartViewHolder>() {

    private val items = mutableListOf<CartItemData>()

    fun updateItems(newItems: List<CartItemData>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemCartOrderBinding.inflate(inflater, parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CartViewHolder(private val binding: ItemCartOrderBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CartItemData) {
            binding.tvCartService.text = "${item.serviceNiche} • ${item.serviceType}"
            binding.tvCartAddress.text = item.address
            binding.tvCartDescription.text = item.description
            binding.tvCartPrice.text = String.format("R$ %.2f", item.estimatedPrice)

            val firstImage = item.imageUrls.firstOrNull()
            if (!firstImage.isNullOrBlank()) {
                Glide.with(binding.ivCartImage.context)
                    .load(firstImage)
                    .apply(
                        RequestOptions()
                            .centerCrop()
                            .placeholder(R.drawable.ic_services)
                            .error(R.drawable.ic_services)
                    )
                    .into(binding.ivCartImage)
            } else {
                binding.ivCartImage.setImageResource(R.drawable.ic_services)
            }

            binding.btnRemoveItem.setOnClickListener {
                onRemoveClick(item)
            }
        }
    }
}
