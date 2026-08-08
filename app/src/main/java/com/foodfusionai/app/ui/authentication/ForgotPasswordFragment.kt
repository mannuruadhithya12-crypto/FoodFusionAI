package com.foodfusionai.app.ui.authentication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentForgotPasswordBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.utils.Resource

class ForgotPasswordFragment : BaseFragment<FragmentForgotPasswordBinding>() {

    private val viewModel: AuthViewModel by viewModels { AuthViewModel.Factory() }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentForgotPasswordBinding.inflate(inflater, container, false)

    override fun setupUI() {
        binding.btnReset.setOnClickListener {
            val email = binding.etEmail.text.toString()
            viewModel.sendPasswordResetEmail(email)
        }
        binding.tvBackToLogin.setOnClickListener {
            navigateBack()
        }
    }

    override fun observeData() {
        viewModel.emailError.observe(viewLifecycleOwner) { error ->
            binding.tilEmail.error = error
        }
        viewModel.forgotPasswordState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnReset.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnReset.isEnabled = true
                    viewModel.resetStates()
                    showSnackbar(getString(R.string.reset_email_sent))
                    navigateBack()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnReset.isEnabled = true
                    showSnackbar(state.message)
                }
                is Resource.Empty -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnReset.isEnabled = true
                }
                null -> {}
            }
        }
    }
}
