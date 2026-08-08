package com.foodfusionai.app.ui.home.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foodfusionai.app.data.models.Offer
import com.foodfusionai.app.databinding.ItemPromoBannerBinding
import com.foodfusionai.app.utils.loadImage

class BannerAdapter(
    private val onBannerClick: (Offer) -> Unit
) : ListAdapter<Offer, BannerAdapter.BannerViewHolder>(BannerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val binding = ItemPromoBannerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BannerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BannerViewHolder(
        private val binding: ItemPromoBannerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onBannerClick(getItem(position))
                }
            }
        }

        fun bind(offer: Offer) {
            binding.tvBannerTitle.text = offer.title
            binding.tvBannerSubtitle.text = offer.description
            if (offer.imageUrl.isNotEmpty()) {
                binding.ivBannerImage.loadImage(offer.imageUrl)
            }
        }
    }

    class BannerDiffCallback : DiffUtil.ItemCallback<Offer>() {
        override fun areItemsTheSame(oldItem: Offer, newItem: Offer): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Offer, newItem: Offer): Boolean =
            oldItem == newItem
    }
}
