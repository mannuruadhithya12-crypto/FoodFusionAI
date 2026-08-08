package com.foodfusionai.app.ui.profile.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentAccountSettingsBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.ui.profile.ProfileViewModel
import kotlinx.coroutines.launch

class AccountSettingsFragment : BaseFragment<FragmentAccountSettingsBinding>() {

    private val viewModel: ProfileViewModel by viewModels { ProfileViewModel.Factory() }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAccountSettingsBinding.inflate(inflater, container, false)

    override fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.btnChangePassword.setOnClickListener {
            findNavController().navigate(R.id.action_accountSettingsFragment_to_forgotPasswordFragment)
        }
        
        binding.btnDeleteAccount.setOnClickListener {
            showDeleteAccountConfirmationDialog()
        }
    }

    private fun showDeleteAccountConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Account")
            .setMessage("Are you absolutely sure you want to delete your account? This will permanently erase your profile, saved addresses, and active carts. You cannot undo this action.")
            .setPositiveButton("Delete Forever") { _, _ ->
                viewModel.deleteAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) android.view.View.VISIBLE else android.view.View.GONE
                    binding.btnDeleteAccount.isEnabled = !state.isLoading

                    state.error?.let { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                        viewModel.resetError()
                    }
                    
                    if (state.isAccountDeleted) {
                        Toast.makeText(requireContext(), "Account deleted successfully", Toast.LENGTH_LONG).show()
                        findNavController().navigate(R.id.action_accountSettingsFragment_to_loginFragment)
                    }
                }
            }
        }
    }
}
