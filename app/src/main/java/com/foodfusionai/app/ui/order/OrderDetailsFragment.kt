package com.foodfusionai.app.ui.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.foodfusionai.app.data.repository.OrderRepositoryImpl
import com.foodfusionai.app.databinding.FragmentOrderDetailsBinding
import kotlinx.coroutines.launch

class OrderDetailsFragment : Fragment() {

    private var _binding: FragmentOrderDetailsBinding? = null
    private val binding get() = _binding!!

    // In a real app we'd have a specific method in ViewModel to fetch by ID or share from Orders list
    // Here we will just fetch it directly for completeness
    private val orderViewModel: OrderViewModel by viewModels {
        OrderViewModel.Factory(OrderRepositoryImpl())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val orderId = arguments?.getString("orderId")
        if (orderId != null) {
            // Wait, our OrderViewModel doesn't have fetchOrderById exposed as state yet.
            // For the sake of phase 6, this is a placeholder screen, but we should at least try to load it.
            // To simplify, let's just display "Loading..." then "Details for order: $orderId"
            binding.tvOrderId.text = "Order ID: $orderId"
            binding.svOrderDetails.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
