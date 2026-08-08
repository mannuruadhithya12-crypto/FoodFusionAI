package com.foodfusionai.app.ui.cart

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentCartBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.ui.cart.adapters.CartFoodAdapter
import com.foodfusionai.app.utils.hide
import com.foodfusionai.app.utils.show
import com.foodfusionai.app.utils.showToast
import com.foodfusionai.app.utils.toCurrencyFormat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class CartFragment : BaseFragment<FragmentCartBinding>() {

    private val viewModel: CartViewModel by viewModels { CartViewModel.Factory() }
    private lateinit var cartFoodAdapter: CartFoodAdapter

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentCartBinding.inflate(inflater, container, false)

    override fun setupUI() {
        setupAdapter()
        setupListeners()
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun setupAdapter() {
        cartFoodAdapter = CartFoodAdapter(
            onIncreaseClick = { item ->
                viewModel.increaseQuantity(item)
            },
            onDecreaseClick = { item ->
                viewModel.decreaseQuantity(item)
            },
            onRemoveClick = { item ->
                viewModel.removeItem(item)
            }
        )
        binding.rvCartItems.adapter = cartFoodAdapter
    }

    private fun setupListeners() {
        binding.tvClearCart.setOnClickListener {
            showClearCartConfirmation()
        }

        binding.btnApplyCoupon.setOnClickListener {
            val code = binding.etCouponInput.text.toString().trim()
            if (code.isNotEmpty()) {
                viewModel.applyCoupon(code)
            } else {
                requireContext().showToast("Please enter a coupon code.")
            }
        }

        binding.btnRemoveCoupon.setOnClickListener {
            viewModel.removeCoupon()
            binding.etCouponInput.text.clear()
        }

        binding.btnCheckoutProceed.setOnClickListener {
            if (viewModel.uiState.value.canCheckout) {
                navigateTo(R.id.action_cartFragment_to_checkoutFragment)
            }
        }

        binding.btnBrowseFood.setOnClickListener {
            // Navigate back to Home screen
            navigateTo(R.id.nav_home)
        }
    }

    private fun showClearCartConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear Cart?")
            .setMessage("Remove all items from your cart?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear Cart") { _, _ ->
                viewModel.clearCart()
            }
            .show()
    }

    private fun renderState(state: CartUiState) {
        if (state.isLoading) {
            binding.progressBar.show()
            binding.scrollViewCart.hide()
            binding.layoutCartFooter.hide()
            binding.layoutEmptyCart.hide()
            binding.tvClearCart.hide()
            return
        }

        binding.progressBar.hide()

        // Handle error toast messages
        if (state.error != null) {
            requireContext().showToast(state.error)
        }

        if (state.isEmpty) {
            binding.scrollViewCart.hide()
            binding.layoutCartFooter.hide()
            binding.tvClearCart.hide()
            binding.layoutEmptyCart.show()
            return
        }

        binding.layoutEmptyCart.hide()
        binding.scrollViewCart.show()
        binding.layoutCartFooter.show()
        binding.tvClearCart.show()

        // Renders adapter lists
        cartFoodAdapter.submitList(state.items)

        // Renders invoice calculations
        binding.tvSubtotal.text = state.subtotal.toCurrencyFormat()
        binding.tvDeliveryFee.text = if (state.deliveryFee == 0.0) "FREE" else state.deliveryFee.toCurrencyFormat()
        binding.tvGrandTotal.text = state.grandTotal.toCurrencyFormat()

        // Renders coupon indicators
        if (state.appliedCoupon != null) {
            binding.layoutAppliedCoupon.show()
            binding.tvAppliedCouponLabel.text = "Coupon ${state.appliedCoupon.code} applied successfully! (₹${state.couponDiscount.toCurrencyFormat()} saved)"
            binding.layoutDiscountRow.show()
            binding.tvDiscount.text = "-${state.couponDiscount.toCurrencyFormat()}"
            binding.etCouponInput.visibility = View.GONE
            binding.btnApplyCoupon.visibility = View.GONE
        } else {
            binding.layoutAppliedCoupon.hide()
            binding.layoutDiscountRow.hide()
            binding.etCouponInput.visibility = View.VISIBLE
            binding.btnApplyCoupon.visibility = View.VISIBLE
        }

        binding.btnCheckoutProceed.isEnabled = state.canCheckout
    }
}
