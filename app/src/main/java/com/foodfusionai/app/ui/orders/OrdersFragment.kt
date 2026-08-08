package com.foodfusionai.app.ui.orders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.foodfusionai.app.R
import com.foodfusionai.app.data.repository.OrderRepositoryImpl
import com.foodfusionai.app.databinding.FragmentOrdersBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.ui.order.OrderViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class OrdersFragment : BaseFragment<FragmentOrdersBinding>() {

    private val orderViewModel: OrderViewModel by viewModels {
        OrderViewModel.Factory(OrderRepositoryImpl())
    }
    
    private lateinit var orderAdapter: OrderAdapter

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentOrdersBinding.inflate(inflater, container, false)

    override fun setupUI() {
        orderAdapter = OrderAdapter { order ->
            // In a real app we'd navigate to OrderDetailsFragment.
            // findNavController().navigate(R.id.action_ordersFragment_to_orderDetailsFragment, Bundle().apply { putString("orderId", order.orderId) })
        }
        
        binding.rvOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = orderAdapter
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            orderViewModel.loadUserOrders(userId)
        } else {
            // For testing without login
            orderViewModel.loadUserOrders("test_user_id")
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                orderViewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    
                    if (!state.isLoading) {
                        if (state.orders.isEmpty()) {
                            binding.tvEmptyState.visibility = View.VISIBLE
                            binding.rvOrders.visibility = View.GONE
                        } else {
                            binding.tvEmptyState.visibility = View.GONE
                            binding.rvOrders.visibility = View.VISIBLE
                            orderAdapter.submitList(state.orders)
                        }
                    }
                }
            }
        }
    }
}

