package com.foodfusionai.app.ui.favorites

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foodfusionai.app.data.models.Favorite
import com.foodfusionai.app.databinding.ItemFavoriteBinding

class FavoriteAdapter(
    private val onItemClick: (Favorite) -> Unit,
    private val onRemoveClick: (Favorite) -> Unit
) : ListAdapter<Favorite, FavoriteAdapter.FavoriteViewHolder>(FavoriteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FavoriteViewHolder(private val binding: ItemFavoriteBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(adapterPosition))
                }
            }
            binding.btnFavorite.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onRemoveClick(getItem(adapterPosition))
                }
            }
        }

        fun bind(favorite: Favorite) {
            binding.tvName.text = favorite.targetName
            binding.tvType.text = favorite.targetType.uppercase()
            // In a real app, use Coil to load imageUrl:
            // binding.ivImage.load(favorite.imageUrl)
        }
    }

    class FavoriteDiffCallback : DiffUtil.ItemCallback<Favorite>() {
        override fun areItemsTheSame(oldItem: Favorite, newItem: Favorite) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Favorite, newItem: Favorite) = oldItem == newItem
    }
}
