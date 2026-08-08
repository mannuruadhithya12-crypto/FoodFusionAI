package com.foodfusionai.app.ui.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentOrderConfirmationBinding
import com.foodfusionai.app.ui.checkout.payment.PaymentViewModel

class OrderConfirmationFragment : Fragment() {

    private var _binding: FragmentOrderConfirmationBinding? = null
    private val binding get() = _binding!!

    // Retrieve order details from PaymentViewModel state
    private val paymentViewModel: PaymentViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderConfirmationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val order = paymentViewModel.uiState.value.orderCreated
        
        if (order != null) {
            binding.tvOrderId.text = "Order ID: ${order.orderId}"
            binding.tvAmount.text = "Total Amount: ₹${order.totalAmount}"
        }

        binding.btnViewOrders.setOnClickListener {
            // Navigate to Order History
            findNavController().navigate(R.id.action_orderConfirmationFragment_to_ordersFragment)
        }

        binding.btnContinueShopping.setOnClickListener {
            // Navigate back to Home
            findNavController().navigate(R.id.action_orderConfirmationFragment_to_homeFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
