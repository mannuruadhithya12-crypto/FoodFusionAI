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

import com.razorpay.Checkout
import org.json.JSONObject

class PaymentFragment : Fragment() {

    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!

    private val checkoutViewModel: CheckoutViewModel by activityViewModels { CheckoutViewModel.Factory() }
    
    private val sharedPaymentViewModel: SharedPaymentViewModel by activityViewModels()

    private val paymentViewModel: PaymentViewModel by activityViewModels {
        PaymentViewModel.Factory(
            paymentRepository = PaymentRepositoryImpl(com.foodfusionai.app.data.payment.RazorpayPaymentAdapter()),
            orderRepository = OrderRepositoryImpl(),
            cartRepository = CartRepositoryImpl(requireContext())
        )
    }

    private var pendingAmount: Double = 0.0
    private var currentReferenceId: String? = null

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
        observeSharedRazorpayResult()
    }

    private fun setupClickListeners() {
        binding.btnPayNow.setOnClickListener { startPayment(pendingAmount) }
        binding.btnRetry.setOnClickListener {
            binding.btnRetry.visibility = View.GONE
            startPayment(pendingAmount)
        }
    }

    private fun startPayment(amountToProcess: Double) {
        val uiState = checkoutViewModel.uiState.value
        
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

        currentReferenceId = "REF_${UUID.randomUUID()}"
        val request = PaymentRequest(
            amount = amountToProcess,
            paymentMethod = PaymentMethod.UPI,
            referenceId = currentReferenceId!!
        )

        paymentViewModel.startPaymentFlow(request, orderSnapshot)
    }

    private fun observePaymentState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                paymentViewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isProcessing) View.VISIBLE else View.GONE
                    
                    binding.btnPayNow.isEnabled = !state.isProcessing
                    
                    if (state.error != null) {
                        binding.tvStatus.text = state.error
                        binding.btnRetry.visibility = View.VISIBLE
                    } else if (state.paymentResult is com.foodfusionai.app.data.payment.PaymentResult.Failed ||
                               state.paymentResult is com.foodfusionai.app.data.payment.PaymentResult.Cancelled) {
                        binding.tvStatus.text = "Payment didn't succeed. Please try again."
                        binding.btnRetry.visibility = View.VISIBLE
                    } else if (state.paymentResult is com.foodfusionai.app.data.payment.PaymentResult.RequiresAction) {
                        val action = (state.paymentResult as com.foodfusionai.app.data.payment.PaymentResult.RequiresAction).action
                        if (action is com.foodfusionai.app.data.payment.PaymentAction.OpenCheckout) {
                            launchRazorpayCheckout(action.options as JSONObject)
                        }
                    } else {
                        binding.tvStatus.text = ""
                        binding.btnRetry.visibility = View.GONE
                    }

                    if (state.orderCreated != null) {
                        findNavController().navigate(R.id.action_paymentFragment_to_orderConfirmationFragment)
                    }
                }
            }
        }
    }

    private fun observeSharedRazorpayResult() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedPaymentViewModel.razorpayResult.collect { result ->
                    val refId = currentReferenceId ?: return@collect
                    paymentViewModel.submitRazorpayResult(result, pendingAmount, refId)
                }
            }
        }
    }

    private fun launchRazorpayCheckout(options: JSONObject) {
        try {
            val checkout = Checkout()
            checkout.setKeyID(getString(R.string.razorpay_key_id))
            checkout.open(requireActivity(), options)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error initializing Razorpay", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
