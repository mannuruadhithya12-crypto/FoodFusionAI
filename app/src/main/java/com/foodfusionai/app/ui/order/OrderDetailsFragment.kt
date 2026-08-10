package com.foodfusionai.app.ui.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.foodfusionai.app.data.models.order.OrderStatus
import com.foodfusionai.app.data.repository.OrderRepositoryImpl
import com.foodfusionai.app.databinding.FragmentOrderDetailsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderDetailsFragment : Fragment() {

    private var _binding: FragmentOrderDetailsBinding? = null
    private val binding get() = _binding!!

    private val trackingViewModel: OrderTrackingViewModel by viewModels {
        OrderTrackingViewModel.Factory(OrderRepositoryImpl())
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
            trackingViewModel.startTracking(orderId)
        } else {
            Toast.makeText(context, "No Order ID provided", Toast.LENGTH_SHORT).show()
        }

        binding.btnCancelOrder.setOnClickListener {
            trackingViewModel.cancelOrder()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                trackingViewModel.uiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: OrderTrackingUiState) {
        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.tvOfflineBanner.visibility = if (state.isOffline) View.VISIBLE else View.GONE
        
        if (state.error != null) {
            Toast.makeText(context, state.error, Toast.LENGTH_SHORT).show()
        }
        
        if (state.cancelError != null) {
            Toast.makeText(context, state.cancelError, Toast.LENGTH_LONG).show()
        }
        
        val order = state.order
        if (order != null) {
            binding.svOrderDetails.visibility = View.VISIBLE
            binding.tvOrderId.text = "Order ID: ${order.orderId}"
            binding.tvRestaurant.text = "Restaurant: ${order.restaurantName}"
            binding.tvStatus.text = "Status: ${order.orderStatus.name}"
            binding.tvTotal.text = "Total: ₹${order.totalAmount}"
            
            // ETA
            if (order.estimatedDeliveryAt != null) {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val etaStr = sdf.format(Date(order.estimatedDeliveryAt))
                binding.tvETA.text = "ETA: $etaStr"
            } else {
                binding.tvETA.text = "ETA: Pending..."
            }
            
            // Timeline
            val timelineText = StringBuilder()
            val history = order.statusHistory.sortedBy { it.timestamp }
            val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            for (step in history) {
                timelineText.append("• ${step.status.name} at ${sdf.format(Date(step.timestamp))}\n")
            }
            if (timelineText.isEmpty()) {
                timelineText.append("• ${order.orderStatus.name} at ${sdf.format(Date(order.createdAt))}")
            }
            binding.tvTimeline.text = timelineText.toString()
            
            // Delay Warning Banner
            val delayStatus = order.deliveryStatus
            if (delayStatus == "DELAYED" || delayStatus == "CRITICAL" || delayStatus == "AT_RISK") {
                binding.tvDelayWarning.visibility = View.VISIBLE
                if (delayStatus == "CRITICAL") {
                    binding.tvDelayWarning.text = "Your order is significantly delayed. Our team is working to resolve this."
                } else {
                    binding.tvDelayWarning.text = "Your order is taking a little longer than expected. We're on it!"
                }
            } else {
                binding.tvDelayWarning.visibility = View.GONE
            }
            
            // Delivery Partner
            if (order.deliveryPartner != null) {
                binding.cardDeliveryPartner.visibility = View.VISIBLE
                val partnerText = StringBuilder()
                partnerText.append("Name: ${order.deliveryPartner.name}\n")
                partnerText.append("Phone: ${order.deliveryPartner.phone}\n")
                partnerText.append("Vehicle: ${order.deliveryPartner.vehicleType} (${order.deliveryPartner.vehicleNumber})")
                
                val loc = state.driverLocation
                if (loc != null) {
                    partnerText.append("\nLive GPS: ${loc.latitude}, ${loc.longitude}")
                } else if (order.orderStatus.name == "OUT_FOR_DELIVERY") {
                    partnerText.append("\nLive GPS: Retrieving location...")
                }
                binding.tvDeliveryPartnerDetails.text = partnerText.toString()
            } else {
                binding.cardDeliveryPartner.visibility = View.GONE
            }

            // Items layout
            binding.layoutItems.removeAllViews()
            order.items.forEach { item ->
                val tv = TextView(context).apply {
                    text = "${item.quantity}x ${item.foodName} - ₹${item.unitPrice}"
                    setPadding(0, 4, 0, 4)
                }
                binding.layoutItems.addView(tv)
            }
            
            // Cancel button state
            binding.btnCancelOrder.visibility = if (state.canCancel) View.VISIBLE else View.GONE
            binding.btnCancelOrder.isEnabled = !state.isCancelling
            binding.btnCancelOrder.text = if (state.isCancelling) "Cancelling..." else "Cancel Order"

            // Review button state
            if (order.orderStatus == OrderStatus.DELIVERED) {
                binding.btnRateOrder.visibility = View.VISIBLE
                binding.btnRateOrder.setOnClickListener {
                    val action = OrderDetailsFragmentDirections.actionOrderDetailsFragmentToWriteReviewFragment(
                        orderId = order.orderId,
                        restaurantId = order.restaurantId,
                        foodId = null, // In a real app we might pass specific foods or a restaurant
                        isEdit = false
                    )
                    findNavController().navigate(action)
                }
            } else {
                binding.btnRateOrder.visibility = View.GONE
            }

            // Visual Progress indicator
            val colorPrimary = getThemeColor(com.google.android.material.R.attr.colorPrimary)
            val colorOutline = getThemeColor(com.google.android.material.R.attr.colorOutlineVariant)

            binding.stepPlaced.setBackgroundColor(colorPrimary) // Always placed
            binding.stepAccepted.setBackgroundColor(if (order.orderStatus >= OrderStatus.CONFIRMED) colorPrimary else colorOutline)
            binding.stepPreparing.setBackgroundColor(if (order.orderStatus >= OrderStatus.PREPARING) colorPrimary else colorOutline)
            binding.stepDelivered.setBackgroundColor(if (order.orderStatus >= OrderStatus.OUT_FOR_DELIVERY) colorPrimary else colorOutline)
        } else {
            binding.svOrderDetails.visibility = View.GONE
        }
    }

    private fun getThemeColor(attrId: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
