package com.foodfusionai.app.ui.cart.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foodfusionai.app.data.local.room.entity.CartEntity
import com.foodfusionai.app.databinding.ItemCartFoodBinding
import com.foodfusionai.app.utils.loadImage
import com.foodfusionai.app.utils.toCurrencyFormat

class CartFoodAdapter(
    private val onIncreaseClick: (CartEntity) -> Unit,
    private val onDecreaseClick: (CartEntity) -> Unit,
    private val onRemoveClick: (CartEntity) -> Unit
) : ListAdapter<CartEntity, CartFoodAdapter.CartViewHolder>(CartDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartFoodBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CartViewHolder(
        private val binding: ItemCartFoodBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnCartPlus.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onIncreaseClick(getItem(position))
                }
            }
            binding.btnCartMinus.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDecreaseClick(getItem(position))
                }
            }
            binding.btnCartRemove.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onRemoveClick(getItem(position))
                }
            }
        }

        fun bind(item: CartEntity) {
            binding.tvCartFoodName.text = item.foodName
            binding.tvCartQuantity.text = item.quantity.toString()
            
            val totalItemPrice = item.price * item.quantity
            binding.tvCartFoodPrice.text = totalItemPrice.toCurrencyFormat()

            // Display customizations if present
            if (!item.customizationsJson.isNullOrBlank()) {
                binding.tvCartFoodCustomizations.text = item.customizationsJson
                binding.tvCartFoodCustomizations.visibility = android.view.View.VISIBLE
            } else {
                binding.tvCartFoodCustomizations.visibility = android.view.View.GONE
            }

            if (item.imageUrl.isNotEmpty()) {
                binding.ivCartFoodImage.loadImage(item.imageUrl)
            }
        }
    }

    class CartDiffCallback : DiffUtil.ItemCallback<CartEntity>() {
        override fun areItemsTheSame(oldItem: CartEntity, newItem: CartEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: CartEntity, newItem: CartEntity): Boolean =
            oldItem == newItem
    }
}
