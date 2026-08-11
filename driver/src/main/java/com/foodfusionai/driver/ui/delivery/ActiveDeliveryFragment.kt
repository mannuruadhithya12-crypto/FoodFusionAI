package com.foodfusionai.driver.ui.delivery

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.foodfusionai.driver.R
import com.foodfusionai.driver.data.models.Order
import com.foodfusionai.driver.data.models.OrderStatus
import com.foodfusionai.driver.data.repository.DriverRepositoryImpl
import com.foodfusionai.driver.databinding.FragmentActiveDeliveryBinding
import com.foodfusionai.driver.services.LocationTrackingService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ActiveDeliveryFragment : Fragment() {

    private var _binding: FragmentActiveDeliveryBinding? = null
    private val binding get() = _binding!!
    private val repository = DriverRepositoryImpl()
    
    private var orderId: String? = null
    private var currentOrder: Order? = null
    private var isArrivedAtRestaurant = false
    private var isArrivedAtCustomer = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActiveDeliveryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        orderId = arguments?.getString("orderId")
        if (orderId == null) {
            Toast.makeText(context, "Order ID not provided", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        observeOrder(orderId!!)
        setupListeners()
    }

    private fun observeOrder(id: String) {
        // We'll use firestore real-time query via repository
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        lifecycleScope.launch {
            binding.loading.visibility = View.VISIBLE
            repository.observeActiveOrder(uid).collect { result ->
                binding.loading.visibility = View.GONE
                result.onSuccess { order ->
                    if (order == null || order.orderId != id) {
                        // Order completed or cancelled elsewhere
                        LocationTrackingService.stop(requireContext())
                        Toast.makeText(context, "Order complete or unassigned", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                        return@collect
                    }
                    
                    currentOrder = order
                    updateUi(order)
                }
            }
        }
    }

    private fun updateUi(order: Order) {
        binding.tvOrderId.text = "Order ID: #${order.orderId.take(8).uppercase()}"
        binding.tvRestaurantName.text = order.restaurantName
        binding.tvRestaurantAddress.text = order.addressSnapshot?.street ?: "Restaurant Address"
        binding.tvCustomerAddress.text = "${order.addressSnapshot?.street}, ${order.addressSnapshot?.city}"
        binding.tvDeliveryInstructions.text = order.deliveryInstructions.ifEmpty { "None" }

        when (order.orderStatus) {
            OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP -> {
                binding.cardPickup.visibility = View.VISIBLE
                binding.cardDropoff.visibility = View.GONE
                binding.cardVerification.visibility = View.GONE
                
                if (!isArrivedAtRestaurant) {
                    binding.btnAction.text = "ARRIVED AT RESTAURANT"
                    binding.btnAction.isEnabled = true
                } else {
                    binding.btnAction.text = "COLLECT ORDER"
                    // Only enable collecting when restaurant says order is READY_FOR_PICKUP
                    binding.btnAction.isEnabled = order.orderStatus == OrderStatus.READY_FOR_PICKUP
                    if (order.orderStatus != OrderStatus.READY_FOR_PICKUP) {
                        Toast.makeText(context, "Waiting for restaurant to mark order Ready...", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            OrderStatus.OUT_FOR_DELIVERY -> {
                binding.cardPickup.visibility = View.GONE
                binding.cardDropoff.visibility = View.VISIBLE
                binding.cardVerification.visibility = View.VISIBLE

                // Start location streaming foreground service if not already started
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    LocationTrackingService.start(requireContext(), uid, order.orderId)
                }

                if (!isArrivedAtCustomer) {
                    binding.btnAction.text = "ARRIVED AT CUSTOMER"
                    binding.btnAction.isEnabled = true
                } else {
                    binding.btnAction.text = "COMPLETE DELIVERY"
                    binding.btnAction.isEnabled = true
                }
            }
            else -> {
                binding.btnAction.isEnabled = false
            }
        }
    }

    private fun setupListeners() {
        binding.btnNavigateRestaurant.setOnClickListener {
            val order = currentOrder ?: return@setOnClickListener
            // Try to navigate using restaurant name and address (or just name if address is empty)
            openGoogleMapsNavigation("${order.restaurantName}")
        }

        binding.btnNavigateCustomer.setOnClickListener {
            val order = currentOrder ?: return@setOnClickListener
            val snapshot = order.addressSnapshot
            if (snapshot != null) {
                openGoogleMapsNavigation("${snapshot.street}, ${snapshot.city}, ${snapshot.state}")
            } else {
                Toast.makeText(context, "Customer address not available", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAction.setOnClickListener {
            val order = currentOrder ?: return@setOnClickListener
            
            when (order.orderStatus) {
                OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP -> {
                    if (!isArrivedAtRestaurant) {
                        isArrivedAtRestaurant = true
                        binding.btnAction.text = "COLLECT ORDER"
                        binding.btnAction.isEnabled = order.orderStatus == OrderStatus.READY_FOR_PICKUP
                        Toast.makeText(context, "Arrived at Restaurant logged", Toast.LENGTH_SHORT).show()
                    } else {
                        // Collect order
                        binding.loading.visibility = View.VISIBLE
                        lifecycleScope.launch {
                            repository.collectOrder(order.orderId).collect { result ->
                                binding.loading.visibility = View.GONE
                                result.fold(
                                    onSuccess = {
                                        Toast.makeText(context, "Order Collected! Start Delivery.", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { err ->
                                        Toast.makeText(context, err.message ?: "Collection failed", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
                OrderStatus.OUT_FOR_DELIVERY -> {
                    if (!isArrivedAtCustomer) {
                        isArrivedAtCustomer = true
                        binding.btnAction.text = "COMPLETE DELIVERY"
                    } else {
                        // Complete delivery via OTP verification
                        val otp = binding.etOtp.text.toString().trim()
                        if (otp.length != 4) {
                            Toast.makeText(context, "Please enter 4-digit OTP", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        binding.loading.visibility = View.VISIBLE
                        lifecycleScope.launch {
                            repository.completeDelivery(order.orderId, otp).collect { result ->
                                binding.loading.visibility = View.GONE
                                result.fold(
                                    onSuccess = {
                                        LocationTrackingService.stop(requireContext())
                                        Toast.makeText(context, "Delivery completed successfully!", Toast.LENGTH_SHORT).show()
                                        findNavController().popBackStack()
                                    },
                                    onFailure = { err ->
                                        Toast.makeText(context, err.message ?: "Verification failed", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        binding.btnReportIssue.setOnClickListener {
            showIssueReportingDialog()
        }
    }

    private fun openGoogleMapsNavigation(query: String) {
        val encodedQuery = Uri.encode(query)
        val gmmIntentUri = Uri.parse("google.navigation:q=$encodedQuery")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (mapIntent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(mapIntent)
        } else {
            Toast.makeText(context, "Google Maps app is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showIssueReportingDialog() {
        val reasons = arrayOf("CUSTOMER_UNAVAILABLE", "WRONG_ADDRESS", "CUSTOMER_REFUSED", "ACCESS_ISSUE", "VEHICLE_ISSUE", "OTHER")
        var selectedReason = reasons[0]
        
        AlertDialog.Builder(requireContext())
            .setTitle("Report Delivery Issue")
            .setSingleChoiceItems(reasons, 0) { _, which ->
                selectedReason = reasons[which]
            }
            .setPositiveButton("Report") { dialog, _ ->
                val inputEditText = EditText(context).apply { hint = "Explain the issue details" }
                AlertDialog.Builder(requireContext())
                    .setTitle("Issue Description")
                    .setView(inputEditText)
                    .setPositiveButton("Submit") { _, _ ->
                        val desc = inputEditText.text.toString().trim()
                        submitDeliveryIssue(selectedReason, desc)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitDeliveryIssue(reason: String, description: String) {
        val order = currentOrder ?: return
        binding.loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            repository.reportIssue(order.orderId, reason, description).collect { result ->
                binding.loading.visibility = View.GONE
                result.fold(
                    onSuccess = {
                        LocationTrackingService.stop(requireContext())
                        Toast.makeText(context, "Issue reported to Operations. Return to dashboard.", Toast.LENGTH_LONG).show()
                        findNavController().popBackStack()
                    },
                    onFailure = { err ->
                        Toast.makeText(context, err.message ?: "Failed to report issue", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
