package com.foodfusionai.app.ui.restaurant.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foodfusionai.app.data.models.Food
import com.foodfusionai.app.databinding.ItemMenuFoodBinding
import com.foodfusionai.app.utils.loadImage
import com.foodfusionai.app.utils.toCurrencyFormat

class MenuFoodAdapter(
    private val onFoodClick: (Food) -> Unit,
    private val onAddClick: (Food) -> Unit
) : ListAdapter<Food, MenuFoodAdapter.MenuFoodViewHolder>(FoodDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuFoodViewHolder {
        val binding = ItemMenuFoodBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MenuFoodViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuFoodViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MenuFoodViewHolder(
        private val binding: ItemMenuFoodBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onFoodClick(getItem(position))
                }
            }
            binding.btnMenuAdd.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAddClick(getItem(position))
                }
            }
        }

        fun bind(food: Food) {
            binding.tvMenuFoodName.text = food.name
            binding.tvMenuFoodDescription.text = food.description
            binding.tvMenuFoodPrice.text = food.price.toCurrencyFormat()
            binding.tvMenuFoodRating.text = String.format("%.1f ★", food.rating)

            if (food.imageUrl.isNotEmpty()) {
                binding.ivMenuFoodImage.loadImage(food.imageUrl)
            }

            // Handle availability status
            if (food.isAvailable) {
                binding.btnMenuAdd.visibility = View.VISIBLE
                binding.btnMenuAdd.isEnabled = true
                binding.tvUnavailableBadge.visibility = View.GONE
            } else {
                binding.btnMenuAdd.visibility = View.GONE
                binding.btnMenuAdd.isEnabled = false
                binding.tvUnavailableBadge.visibility = View.VISIBLE
            }
        }
    }

    class FoodDiffCallback : DiffUtil.ItemCallback<Food>() {
        override fun areItemsTheSame(oldItem: Food, newItem: Food): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Food, newItem: Food): Boolean =
            oldItem == newItem
    }
}
