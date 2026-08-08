package com.foodfusionai.app.ui.checkout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.foodfusionai.app.databinding.FragmentCheckoutBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.utils.hide
import com.foodfusionai.app.utils.show
import com.foodfusionai.app.utils.showToast
import com.foodfusionai.app.utils.toCurrencyFormat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class CheckoutFragment : BaseFragment<FragmentCheckoutBinding>() {

    private val viewModel: CheckoutViewModel by viewModels { CheckoutViewModel.Factory() }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentCheckoutBinding.inflate(inflater, container, false)

    override fun setupUI() {
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

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            navigateBack()
        }

        binding.etInstructionsInput.doAfterTextChanged { text ->
            val instructions = text.toString()
            binding.tvInstructionsCount.text = "${instructions.length}/120"
            viewModel.updateDeliveryInstructions(instructions)
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

        binding.btnPlaceOrder.setOnClickListener {
            viewModel.validateAndProceed()
        }

        binding.tvChangeAddress.setOnClickListener {
            showAddressSelectionDialog()
        }
    }

    private fun showAddressSelectionDialog() {
        // Retrieve addresses list from Repository using ViewModel base list or just fetch
        val addresses = listOf(
            com.foodfusionai.app.data.models.Address("addr_1", "Home", "123, Park Lane, Indiranagar", "Bangalore"),
            com.foodfusionai.app.data.models.Address("addr_2", "Work", "Global Tech Park, Outer Ring Road", "Bangalore")
        )
        val addressOptions = addresses.map { "${it.type}: ${it.street}, ${it.city}" }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Delivery Address")
            .setItems(addressOptions) { _, which ->
                viewModel.selectAddress(addresses[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderState(state: CheckoutUiState) {
        if (state.isLoading) {
            binding.progressBar.show()
            binding.scrollViewCheckout.hide()
            binding.layoutCheckoutFooter.hide()
            return
        }

        binding.progressBar.hide()
        binding.scrollViewCheckout.show()
        binding.layoutCheckoutFooter.show()

        // Bind delivery address details
        if (state.selectedAddress != null) {
            binding.tvAddressType.text = state.selectedAddress.type
            binding.tvAddressText.text = "${state.selectedAddress.street}, ${state.selectedAddress.city}"
            binding.layoutAddressDetails.show()
        } else {
            binding.tvAddressType.text = "No address selected"
            binding.tvAddressText.text = "Please add or select a delivery address."
            binding.layoutAddressDetails.hide()
        }

        // Render Invoice breakdown prices
        binding.tvSubtotal.text = state.subtotal.toCurrencyFormat()
        binding.tvDeliveryFee.text = if (state.deliveryFee == 0.0) "FREE" else state.deliveryFee.toCurrencyFormat()
        binding.tvGrandTotal.text = state.payableTotal.toCurrencyFormat()

        // Applied coupon indicators
        if (state.appliedCoupon != null) {
            binding.layoutAppliedCoupon.show()
            binding.tvAppliedCouponLabel.text = "Coupon ${state.appliedCoupon.code} applied successfully! (₹${state.discount.toCurrencyFormat()} saved)"
            binding.layoutDiscountRow.show()
            binding.tvDiscount.text = "-${state.discount.toCurrencyFormat()}"
            binding.etCouponInput.visibility = View.GONE
            binding.btnApplyCoupon.visibility = View.GONE
        } else {
            binding.layoutAppliedCoupon.hide()
            binding.layoutDiscountRow.hide()
            binding.etCouponInput.visibility = View.VISIBLE
            binding.btnApplyCoupon.visibility = View.VISIBLE
        }

        // Handle error toast messages
        if (state.error != null) {
            requireContext().showToast(state.error)
        }

        // Handle validation messages overlay popup
        if (state.validationMessage != null) {
            showValidationDialog(state.validationMessage, state.checkoutValidationPassed)
            viewModel.resetValidationMessage()
        }
    }

    private fun showValidationDialog(message: String, isSuccess: Boolean) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isSuccess) "Checkout Verified" else "Checkout Alert")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
