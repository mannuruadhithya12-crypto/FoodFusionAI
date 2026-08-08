package com.foodfusionai.app.ui.home.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.foodfusionai.app.data.models.RecommendationItem
import com.foodfusionai.app.databinding.ItemTrendingFoodBinding
import com.foodfusionai.app.utils.toCurrencyFormat

class RecommendationAdapter(
    private val onItemClick: (RecommendationItem) -> Unit
) : ListAdapter<RecommendationItem, RecommendationAdapter.RecommendationViewHolder>(RecommendationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val binding = ItemTrendingFoodBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecommendationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecommendationViewHolder(private val binding: ItemTrendingFoodBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecommendationItem) {
            val food = item.food
            binding.tvFoodName.text = food.name
            binding.tvFoodPrice.text = food.price.toCurrencyFormat()
            binding.tvFoodRating.text = food.rating.toString()
            binding.ivFoodImage.load(food.imageUrl) {
                crossfade(true)
                transformations(RoundedCornersTransformation(16f))
            }

            // We can re-use the description field for the recommendation reason
            binding.tvRecommendationReason.text = item.reason.text

            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(item)
                }
            }
        }
    }

    class RecommendationDiffCallback : DiffUtil.ItemCallback<RecommendationItem>() {
        override fun areItemsTheSame(oldItem: RecommendationItem, newItem: RecommendationItem): Boolean {
            return oldItem.food.id == newItem.food.id
        }

        override fun areContentsTheSame(oldItem: RecommendationItem, newItem: RecommendationItem): Boolean {
            return oldItem == newItem
        }
    }
}
