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
import com.foodfusionai.driver.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val repository = DriverRepositoryImpl()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.loading.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false

            lifecycleScope.launch {
                repository.login(email, password).collect { result ->
                    binding.loading.visibility = View.GONE
                    binding.btnLogin.isEnabled = true

                    result.fold(
                        onSuccess = { driver ->
                            when (driver.status) {
                                "APPROVED" -> findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
                                "PENDING", "REJECTED" -> findNavController().navigate(R.id.action_loginFragment_to_pendingApprovalFragment)
                                else -> findNavController().navigate(R.id.action_loginFragment_to_pendingApprovalFragment)
                            }
                        },
                        onFailure = { error ->
                            Toast.makeText(context, error.message ?: "Authentication failed", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }

        binding.btnRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_onboardingFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
