package com.foodfusionai.app.ui.restaurant

import android.view.LayoutInflater
import android.view.ViewGroup
import com.foodfusionai.app.databinding.FragmentRestaurantDetailsBinding
import com.foodfusionai.app.ui.base.BaseFragment

class RestaurantDetailsFragment : BaseFragment<FragmentRestaurantDetailsBinding>() {

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentRestaurantDetailsBinding.inflate(inflater, container, false)

    override fun setupUI() {
        val restaurantId = arguments?.getString("restaurantId") ?: "unknown"
        binding.tvRestaurantId.text = "Restaurant ID: $restaurantId"

        binding.btnBack.setOnClickListener {
            navigateBack()
        }
    }

    override fun observeData() {
        // No-op
    }
}
