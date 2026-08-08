package com.foodfusionai.app.ui.food

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentFoodDetailsBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.utils.hide
import com.foodfusionai.app.utils.loadImage
import com.foodfusionai.app.utils.show
import com.foodfusionai.app.utils.showToast
import com.foodfusionai.app.utils.toCurrencyFormat
import kotlinx.coroutines.launch

class FoodDetailsFragment : BaseFragment<FragmentFoodDetailsBinding>() {

    private val viewModel: FoodDetailsViewModel by viewModels { FoodDetailsViewModel.Factory() }
    private var foodId: String = "unknown"

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentFoodDetailsBinding.inflate(inflater, container, false)

    override fun setupUI() {
        foodId = arguments?.let {
            try {
                FoodDetailsFragmentArgs.fromBundle(it).foodId
            } catch (_: Throwable) {
                it.getString("foodId")
            }
        } ?: "unknown"

        setupListeners()
        viewModel.loadFoodData(foodId)
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                    }
                }
                launch {
                    viewModel.cartInsertionSuccess.collect { success ->
                        if (success == true) {
                            requireContext().showToast("Added to cart successfully!")
                            viewModel.resetCartSuccess()
                        } else if (success == false) {
                            requireContext().showToast("Unable to add item to cart. Please try again.")
                            viewModel.resetCartSuccess()
                        }
                    }
                }
                launch {
                    viewModel.cartConflictState.collect { item ->
                        if (item != null) {
                            showConflictDialog(item)
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            navigateBack()
        }

        binding.btnQtyMinus.setOnClickListener {
            viewModel.decreaseQuantity()
        }

        binding.btnQtyPlus.setOnClickListener {
            viewModel.increaseQuantity()
        }

        binding.btnAddToCart.setOnClickListener {
            viewModel.addToCart()
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadFoodData(foodId)
        }

        // Customization listener chip checks
        binding.chipSizeSmall.setOnClickListener { viewModel.selectSize("Small") }
        binding.chipSizeMedium.setOnClickListener { viewModel.selectSize("Medium") }
        binding.chipSizeLarge.setOnClickListener { viewModel.selectSize("Large") }

        binding.chipSpiceMild.setOnClickListener { viewModel.selectSpiceLevel("Mild") }
        binding.chipSpiceMedium.setOnClickListener { viewModel.selectSpiceLevel("Medium") }
        binding.chipSpiceHot.setOnClickListener { viewModel.selectSpiceLevel("Hot") }
    }

    private fun renderState(state: FoodDetailsUiState) {
        if (state.isLoading) {
            binding.progressBar.show()
            binding.scrollViewFood.hide()
            binding.layoutBottomActions.hide()
            binding.layoutError.hide()
            return
        }

        if (state.error != null) {
            binding.progressBar.hide()
            binding.scrollViewFood.hide()
            binding.layoutBottomActions.hide()
            binding.layoutError.show()
            binding.tvErrorMessage.text = state.error
            return
        }

        binding.progressBar.hide()
        binding.layoutError.hide()
        binding.scrollViewFood.show()
        binding.layoutBottomActions.show()

        val food = state.food ?: return

        // Bind main profiles
        binding.tvFoodTitle.text = food.name
        binding.tvFoodDescription.text = food.description
        binding.tvFoodPrice.text = food.price.toCurrencyFormat()
        binding.tvFoodRating.text = String.format("%.1f ★", food.rating)
        binding.tvFoodRestaurantName.text = "by ${state.restaurant?.name ?: "Partner Restaurant"}"

        if (food.imageUrl.isNotEmpty()) {
            binding.ivFoodHero.loadImage(food.imageUrl)
        }

        // Render quantity adjuster values
        binding.tvQuantityText.text = state.quantity.toString()
        binding.btnAddToCart.text = "Add to Cart • ${state.subtotal.toCurrencyFormat()}"

        // Handle item availability state
        if (state.isAvailable) {
            binding.btnAddToCart.isEnabled = true
            binding.btnQtyMinus.isEnabled = true
            binding.btnQtyPlus.isEnabled = true
        } else {
            binding.btnAddToCart.isEnabled = false
            binding.btnAddToCart.text = "Currently unavailable"
            binding.btnQtyMinus.isEnabled = false
            binding.btnQtyPlus.isEnabled = false
        }
    }

    private fun showConflictDialog(item: com.foodfusionai.app.data.local.room.entity.CartEntity) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Replace Cart Items?")
            .setMessage("Your cart contains items from another restaurant. Do you want to clear your cart and add this item instead?")
            .setNegativeButton("Cancel") { _, _ ->
                viewModel.resetConflictState()
            }
            .setPositiveButton("Clear Cart & Add") { _, _ ->
                viewModel.addToCart(forceClear = true)
            }
            .setOnDismissListener {
                viewModel.resetConflictState()
            }
            .show()
    }
}
