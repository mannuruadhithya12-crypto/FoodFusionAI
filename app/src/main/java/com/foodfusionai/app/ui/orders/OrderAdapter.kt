package com.foodfusionai.app.ui.orders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foodfusionai.app.data.models.order.Order
import com.foodfusionai.app.databinding.ItemOrderBinding

class OrderAdapter(
    private val onItemClick: (Order) -> Unit
) : ListAdapter<Order, OrderAdapter.OrderViewHolder>(OrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(private val binding: ItemOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            binding.tvRestaurantName.text = order.restaurantName
            
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())
            val dateStr = sdf.format(java.util.Date(order.createdAt))
            
            // Reusing tvOrderId for date & id string
            binding.tvOrderId.text = "Order ID: ${order.orderId}\n$dateStr"
            binding.tvTotalAmount.text = "₹${order.totalAmount}"
            
            // Adjust color based on status
            val statusStr = order.orderStatus.name.replace("_", " ")
            binding.tvStatus.text = statusStr
            when (order.orderStatus) {
                com.foodfusionai.app.data.models.order.OrderStatus.CANCELLED,
                com.foodfusionai.app.data.models.order.OrderStatus.PAYMENT_FAILED -> {
                    binding.tvStatus.setTextColor(android.graphics.Color.RED)
                }
                com.foodfusionai.app.data.models.order.OrderStatus.DELIVERED -> {
                    binding.tvStatus.setTextColor(android.graphics.Color.DKGRAY)
                }
                else -> {
                    // default green
                    binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#388E3C"))
                }
            }

            binding.root.setOnClickListener {
                onItemClick(order)
            }
        }
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem.orderId == newItem.orderId
        }

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem == newItem
        }
    }
}
