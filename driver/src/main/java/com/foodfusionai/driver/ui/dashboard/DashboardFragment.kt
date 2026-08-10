package com.foodfusionai.driver.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.foodfusionai.driver.R
import com.foodfusionai.driver.data.repository.DriverRepositoryImpl
import com.foodfusionai.driver.databinding.FragmentDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val repository = DriverRepositoryImpl()
    
    private var timerJob: Job? = null
    private var activeOfferId: String? = null
    private var isUpdatingStatus = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        setupNavigation()
        observeDriverProfile(uid)
        observeActiveOffers(uid)
        observeActiveOrder(uid)
        fetchEarnings(uid)
    }

    private fun setupNavigation() {
        binding.navEarnings.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_earningsFragment)
        }
        binding.navHistory.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_historyFragment)
        }
        binding.navProfile.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_profileFragment)
        }
    }

    private fun observeDriverProfile(uid: String) {
        lifecycleScope.launch {
            repository.observeDriverProfile(uid).collect { result ->
                result.fold(
                    onSuccess = { driver ->
                        binding.tvGreeting.text = "Hello, ${driver.name}"
                        
                        // Prevent infinite loop by disabling listener trigger
                        isUpdatingStatus = true
                        binding.switchStatus.isChecked = driver.availability == "ONLINE" || driver.availability == "BUSY"
                        binding.switchStatus.text = if (driver.availability == "OFFLINE") "OFFLINE" else driver.availability
                        isUpdatingStatus = false

                        // If suspended, navigate away
                        if (driver.status == "SUSPENDED") {
                            Toast.makeText(context, "Your account has been suspended", Toast.LENGTH_LONG).show()
                            repository.logout()
                            findNavController().navigate(R.id.action_dashboardFragment_to_loginFragment)
                        }
                    },
                    onFailure = { error ->
                        Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        binding.switchStatus.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingStatus) return@setOnCheckedChangeListener
            val newStatus = if (isChecked) "ONLINE" else "OFFLINE"
            
            lifecycleScope.launch {
                repository.updateDriverAvailability(uid, newStatus).collect { result ->
                    result.onFailure {
                        Toast.makeText(context, "Failed to toggle status", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun observeActiveOffers(uid: String) {
        lifecycleScope.launch {
            repository.observeActiveOffers(uid).collect { result ->
                result.onSuccess { offers ->
                    val activeOffer = offers.firstOrNull()
                    if (activeOffer != null) {
                        val offerId = activeOffer["offerId"] as? String ?: ""
                        if (activeOfferId != offerId) {
                            activeOfferId = offerId
                            showOfferCard(activeOffer)
                        }
                    } else {
                        activeOfferId = null
                        timerJob?.cancel()
                        binding.cardOffer.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun showOfferCard(offer: Map<String, Any>) {
        val offerId = offer["offerId"] as? String ?: ""
        binding.tvOfferRestaurantName.text = offer["restaurantName"] as? String ?: "Restaurant"
        binding.tvOfferPickupAddress.text = offer["restaurantAddress"] as? String ?: "Pickup Address"
        binding.tvOfferDeliveryArea.text = "To: ${offer["deliveryArea"] as? String ?: "Delivery Area"}"
        
        binding.cardOffer.visibility = View.VISIBLE

        binding.btnAcceptOffer.setOnClickListener {
            timerJob?.cancel()
            binding.cardOffer.visibility = View.GONE
            binding.loading.visibility = View.VISIBLE
            
            lifecycleScope.launch {
                repository.acceptOffer(offerId).collect { result ->
                    binding.loading.visibility = View.GONE
                    result.fold(
                        onSuccess = { orderId ->
                            Toast.makeText(context, "Offer Accepted!", Toast.LENGTH_SHORT).show()
                            val bundle = Bundle().apply { putString("orderId", orderId) }
                            findNavController().navigate(R.id.action_dashboardFragment_to_activeDeliveryFragment, bundle)
                        },
                        onFailure = { err ->
                            Toast.makeText(context, err.message ?: "Failed to accept offer", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }

        binding.btnDeclineOffer.setOnClickListener {
            declineActiveOffer(offerId)
        }

        // Start 30s Countdown timer
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            var seconds = 30
            while (seconds > 0) {
                binding.tvOfferTimer.text = "Expires in ${seconds}s"
                delay(1000)
                seconds--
            }
            // Auto decline on expiry
            declineActiveOffer(offerId)
        }
    }

    private fun declineActiveOffer(offerId: String) {
        timerJob?.cancel()
        binding.cardOffer.visibility = View.GONE
        lifecycleScope.launch {
            repository.declineOffer(offerId).collect { result ->
                result.onSuccess {
                    Toast.makeText(context, "Offer declined", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeActiveOrder(uid: String) {
        lifecycleScope.launch {
            repository.observeActiveOrder(uid).collect { result ->
                result.onSuccess { order ->
                    if (order != null) {
                        binding.tvActiveOrderId.text = "Order ID: #${order.orderId.take(8).uppercase()}"
                        binding.cardActiveDelivery.visibility = View.VISIBLE
                        binding.btnResumeActive.setOnClickListener {
                            val bundle = Bundle().apply { putString("orderId", order.orderId) }
                            findNavController().navigate(R.id.action_dashboardFragment_to_activeDeliveryFragment, bundle)
                        }
                    } else {
                        binding.cardActiveDelivery.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun fetchEarnings(uid: String) {
        lifecycleScope.launch {
            repository.getEarningsSummary(uid).collect { result ->
                result.onSuccess { summary ->
                    val todayEarnings = summary["todayEarnings"] ?: 0.0
                    val totalEarnings = summary["totalEarnings"] ?: 0.0
                    binding.tvTodayEarnings.text = "₹${todayEarnings}"
                    
                    // We can query completed deliveries count from history
                    repository.getDeliveryHistory(uid).collect { histResult ->
                        histResult.onSuccess { list ->
                            binding.tvTodayDeliveries.text = list.size.toString()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerJob?.cancel()
        _binding = null
    }
}
