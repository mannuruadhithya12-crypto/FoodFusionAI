package com.foodfusionai.app.ui.authentication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentRegisterBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.utils.Resource

class RegisterFragment : BaseFragment<FragmentRegisterBinding>() {

    private val viewModel: AuthViewModel by viewModels { AuthViewModel.Factory() }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentRegisterBinding.inflate(inflater, container, false)

    override fun setupUI() {
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString()
            val email = binding.etEmail.text.toString()
            val phone = binding.etPhone.text.toString()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            viewModel.register(name, email, phone, password, confirmPassword)
        }
        binding.tvHaveAccount.setOnClickListener {
            navigateBack()
        }
    }

    override fun observeData() {
        viewModel.nameError.observe(viewLifecycleOwner) { error ->
            binding.tilName.error = error
        }
        viewModel.emailError.observe(viewLifecycleOwner) { error ->
            binding.tilEmail.error = error
        }
        viewModel.phoneError.observe(viewLifecycleOwner) { error ->
            binding.tilPhone.error = error
        }
        viewModel.passwordError.observe(viewLifecycleOwner) { error ->
            binding.tilPassword.error = error
        }
        viewModel.confirmPasswordError.observe(viewLifecycleOwner) { error ->
            binding.tilConfirmPassword.error = error
        }
        viewModel.registerState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnRegister.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    viewModel.resetStates()
                    showSnackbar(getString(R.string.register_successful))
                    navigateTo(R.id.action_registerFragment_to_homeFragment)
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                    showSnackbar(state.message)
                }
                is Resource.Empty -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRegister.isEnabled = true
                }
                null -> {}
            }
        }
    }
}
