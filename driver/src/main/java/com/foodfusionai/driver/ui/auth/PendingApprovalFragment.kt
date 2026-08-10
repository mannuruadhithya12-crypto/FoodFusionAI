package com.foodfusionai.driver.ui.auth

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
import com.foodfusionai.driver.databinding.FragmentPendingApprovalBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class PendingApprovalFragment : Fragment() {

    private var _binding: FragmentPendingApprovalBinding? = null
    private val binding get() = _binding!!
    private val repository = DriverRepositoryImpl()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPendingApprovalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRefresh.setOnClickListener {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                findNavController().navigate(R.id.action_pendingApprovalFragment_to_loginFragment)
                return@setOnClickListener
            }

            binding.loading.visibility = View.VISIBLE
            binding.btnRefresh.isEnabled = false

            lifecycleScope.launch {
                repository.getDriverProfile(currentUser.uid).collect { result ->
                    binding.loading.visibility = View.GONE
                    binding.btnRefresh.isEnabled = true

                    result.fold(
                        onSuccess = { driver ->
                            when (driver.status) {
                                "APPROVED" -> {
                                    Toast.makeText(context, "Account Approved! Welcome aboard.", Toast.LENGTH_SHORT).show()
                                    findNavController().navigate(R.id.action_pendingApprovalFragment_to_dashboardFragment)
                                }
                                "PENDING" -> {
                                    Toast.makeText(context, "Still pending review. Check back later.", Toast.LENGTH_SHORT).show()
                                }
                                "REJECTED" -> {
                                    binding.tvTitle.text = "Application Rejected"
                                    binding.tvDescription.text = "Unfortunately, your application was not approved. Please contact support."
                                    Toast.makeText(context, "Your application was rejected.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onFailure = { error ->
                            Toast.makeText(context, "Error checking status: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        binding.btnLogout.setOnClickListener {
            repository.logout()
            findNavController().navigate(R.id.action_pendingApprovalFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
