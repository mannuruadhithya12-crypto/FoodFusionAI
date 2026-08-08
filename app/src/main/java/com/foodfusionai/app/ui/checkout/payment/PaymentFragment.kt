package com.foodfusionai.app.ui.checkout.payment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.foodfusionai.app.R
import com.foodfusionai.app.data.models.order.Order
import com.foodfusionai.app.data.models.order.PaymentMethod
import com.foodfusionai.app.data.payment.PaymentRequest
import com.foodfusionai.app.data.payment.TestPaymentAdapter
import com.foodfusionai.app.data.repository.CartRepositoryImpl
import com.foodfusionai.app.data.repository.OrderRepositoryImpl
import com.foodfusionai.app.data.repository.PaymentRepositoryImpl
import com.foodfusionai.app.databinding.FragmentPaymentBinding
import com.foodfusionai.app.ui.checkout.CheckoutViewModel
import kotlinx.coroutines.launch
import java.util.UUID

class PaymentFragment : Fragment() {

    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!

    // Shared with Checkout to get the final snapshot/cart
    private val checkoutViewModel: CheckoutViewModel by activityViewModels { CheckoutViewModel.Factory() }

    private val paymentViewModel: PaymentViewModel by activityViewModels {
        PaymentViewModel.Factory(
            paymentRepository = PaymentRepositoryImpl(TestPaymentAdapter()),
            orderRepository = OrderRepositoryImpl(),
            cartRepository = CartRepositoryImpl(requireContext())
        )
    }

    private var pendingAmount: Double = 0.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pendingAmount = checkoutViewModel.uiState.value.payableTotal
        binding.tvAmount.text = "Amount to pay: ₹$pendingAmount"

        setupClickListeners()
        observePaymentState()
    }

    private fun setupClickListeners() {
        binding.btnPaySuccess.setOnClickListener { startPayment(pendingAmount) }
        binding.btnPayFail.setOnClickListener { startPayment(9999.0) } // magic amount for fail
        binding.btnPayCancel.setOnClickListener { startPayment(8888.0) } // magic amount for cancel
        binding.btnRetry.setOnClickListener {
            binding.btnRetry.visibility = View.GONE
            startPayment(pendingAmount)
        }
    }

    private fun startPayment(amountToProcess: Double) {
        val uiState = checkoutViewModel.uiState.value
        
        // Ensure snapshot
        val address = uiState.selectedAddress
        val addressSnapshot = if (address != null) com.foodfusionai.app.data.models.order.AddressSnapshot.fromAddress(address) else null

        val orderItems = uiState.cartItems.map {
            com.foodfusionai.app.data.models.order.OrderItem(
                foodId = it.foodId,
                foodName = it.foodName,
                quantity = it.quantity,
                unitPrice = it.price,
                subtotal = it.price * it.quantity,
                imageUrl = it.imageUrl,
                customizationsJson = it.customizationsJson,
                size = null,
                spice = null
            )
        }

        // Dummy user Id, normally from AuthRepository
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "test_user_id"
        val restaurantId = uiState.cartItems.firstOrNull()?.restaurantId ?: ""

        val orderSnapshot = Order(
            userId = userId,
            restaurantId = restaurantId,
            items = orderItems,
            subtotal = uiState.subtotal,
            deliveryFee = uiState.deliveryFee,
            discount = uiState.discount,
            couponDiscount = uiState.appliedCoupon?.discountPercentage ?: 0.0,
            totalAmount = pendingAmount,
            addressSnapshot = addressSnapshot,
            deliveryInstructions = uiState.deliveryInstructions
        )

        val request = PaymentRequest(
            amount = amountToProcess,
            paymentMethod = PaymentMethod.UPI,
            referenceId = "REF_${UUID.randomUUID()}"
        )

        paymentViewModel.startPaymentFlow(request, orderSnapshot)
    }

    private fun observePaymentState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                paymentViewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isProcessing) View.VISIBLE else View.GONE
                    
                    binding.btnPaySuccess.isEnabled = !state.isProcessing
                    binding.btnPayFail.isEnabled = !state.isProcessing
                    binding.btnPayCancel.isEnabled = !state.isProcessing
                    
                    if (state.error != null) {
                        binding.tvStatus.text = state.error
                        binding.btnRetry.visibility = View.VISIBLE
                    } else if (state.paymentResult is com.foodfusionai.app.data.payment.PaymentResult.Failed ||
                               state.paymentResult is com.foodfusionai.app.data.payment.PaymentResult.Cancelled) {
                        binding.tvStatus.text = "Payment didn't succeed. Please try again."
                        binding.btnRetry.visibility = View.VISIBLE
                    } else {
                        binding.tvStatus.text = ""
                        binding.btnRetry.visibility = View.GONE
                    }

                    if (state.orderCreated != null) {
                        // Order successful! Navigate to confirmation
                        findNavController().navigate(R.id.action_paymentFragment_to_orderConfirmationFragment)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
