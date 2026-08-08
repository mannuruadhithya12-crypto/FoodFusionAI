package com.foodfusionai.app.ui

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.ActivityMainBinding
import com.foodfusionai.app.ui.base.BaseActivity
import com.foodfusionai.app.utils.hide
import com.foodfusionai.app.utils.show

import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.activity.viewModels

class MainActivity : BaseActivity<ActivityMainBinding>(), com.razorpay.PaymentResultWithDataListener {

    override fun getViewBinding(): ActivityMainBinding =
        ActivityMainBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setupNavigation()
        observeCartBadge()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        // Hide bottom nav on certain destinations (auth, food detail, checkout, etc.)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment, R.id.registerFragment, R.id.forgotPasswordFragment -> binding.bottomNavigation.hide()
                else -> binding.bottomNavigation.show()
            }
        }
    }

    private fun observeCartBadge() {
        val database = com.foodfusionai.app.data.local.room.FoodFusionDatabase.getDatabase(this)
        val cartDao = database.cartDao()
        
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                cartDao.getAllCartItems().collect { cartItems ->
                    val totalCount = cartItems.sumOf { it.quantity }
                    if (totalCount > 0) {
                        val badge = binding.bottomNavigation.getOrCreateBadge(R.id.nav_cart)
                        badge.number = totalCount
                        badge.isVisible = true
                    } else {
                        binding.bottomNavigation.removeBadge(R.id.nav_cart)
                    }
                }
            }
        }
    }

    override fun onPaymentSuccess(paymentId: String?, paymentData: com.razorpay.PaymentData?) {
        val sharedPaymentViewModel: com.foodfusionai.app.ui.checkout.payment.SharedPaymentViewModel by viewModels()
        lifecycleScope.launch {
            if (paymentId != null && paymentData != null) {
                sharedPaymentViewModel.emitSuccess(
                    paymentId = paymentId,
                    signature = paymentData.signature ?: "",
                    orderId = paymentData.orderId ?: ""
                )
            } else {
                sharedPaymentViewModel.emitError(0, "Invalid payment data received on success")
            }
        }
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: com.razorpay.PaymentData?) {
        val sharedPaymentViewModel: com.foodfusionai.app.ui.checkout.payment.SharedPaymentViewModel by viewModels()
        lifecycleScope.launch {
            sharedPaymentViewModel.emitError(code, response ?: "Unknown error")
        }
    }
}
