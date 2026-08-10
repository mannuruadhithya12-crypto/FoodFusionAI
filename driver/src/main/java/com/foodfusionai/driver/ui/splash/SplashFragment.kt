package com.foodfusionai.driver.ui.splash

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.foodfusionai.driver.R
import com.foodfusionai.driver.data.repository.DriverRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashFragment : Fragment() {

    private val repository = DriverRepositoryImpl()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Simple delay to show splash screen, then check login status
        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthAndStatus()
        }, 1500)
    }

    private fun checkAuthAndStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            findNavController().navigate(R.id.action_splashFragment_to_loginFragment)
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            repository.getDriverProfile(currentUser.uid).collect { result ->
                result.fold(
                    onSuccess = { driver ->
                        when (driver.status) {
                            "APPROVED" -> findNavController().navigate(R.id.action_splashFragment_to_dashboardFragment)
                            "PENDING", "REJECTED" -> findNavController().navigate(R.id.action_splashFragment_to_pendingApprovalFragment)
                            else -> findNavController().navigate(R.id.action_splashFragment_to_pendingApprovalFragment)
                        }
                    },
                    onFailure = {
                        // Profile does not exist yet
                        findNavController().navigate(R.id.action_splashFragment_to_onboardingFragment)
                    }
                )
            }
        }
    }
}
