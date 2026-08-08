package com.foodfusionai.app.ui.authentication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentLoginBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.utils.Resource

class LoginFragment : BaseFragment<FragmentLoginBinding>() {

    private val viewModel: AuthViewModel by viewModels { AuthViewModel.Factory() }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentLoginBinding.inflate(inflater, container, false)

    override fun setupUI() {
        if (viewModel.isLoggedIn()) {
            navigateTo(R.id.action_loginFragment_to_homeFragment)
            return
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            viewModel.login(email, password)
        }
        binding.tvForgotPassword.setOnClickListener {
            navigateTo(R.id.action_loginFragment_to_forgotPasswordFragment)
        }
        binding.tvNoAccount.setOnClickListener {
            navigateTo(R.id.action_loginFragment_to_registerFragment)
        }
    }

    override fun observeData() {
        viewModel.emailError.observe(viewLifecycleOwner) { error ->
            binding.tilEmail.error = error
        }
        viewModel.passwordError.observe(viewLifecycleOwner) { error ->
            binding.tilPassword.error = error
        }
        viewModel.loginState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    viewModel.resetStates()
                    showSnackbar(getString(R.string.login_successful))
                    navigateTo(R.id.action_loginFragment_to_homeFragment)
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    showSnackbar(state.message)
                }
                is Resource.Empty -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                }
                null -> {}
            }
        }
    }
}
