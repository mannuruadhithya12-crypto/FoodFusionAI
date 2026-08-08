package com.foodfusionai.app.ui.food

import android.view.LayoutInflater
import android.view.ViewGroup
import com.foodfusionai.app.databinding.FragmentFoodDetailsBinding
import com.foodfusionai.app.ui.base.BaseFragment

class FoodDetailsFragment : BaseFragment<FragmentFoodDetailsBinding>() {

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentFoodDetailsBinding.inflate(inflater, container, false)

    override fun setupUI() {
        val foodId = arguments?.getString("foodId") ?: "unknown"
        binding.tvFoodId.text = "Food ID: $foodId"

        binding.btnBack.setOnClickListener {
            navigateBack()
        }
    }

    override fun observeData() {
        // No-op
    }
}
