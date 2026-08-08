package com.foodfusionai.app.ui.profile

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
import com.foodfusionai.app.databinding.FragmentProfileBinding
import com.foodfusionai.app.ui.base.BaseFragment
import kotlinx.coroutines.launch

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    private val viewModel: ProfileViewModel by viewModels { ProfileViewModel.Factory() }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentProfileBinding.inflate(inflater, container, false)

    override fun setupUI() {
        binding.btnMyAddresses.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_addressListFragment)
        }
        
        binding.btnMyOrders.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_ordersFragment)
        }
        
        binding.btnAccountSettings.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_accountSettingsFragment)
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
        
        binding.btnEditProfile.setOnClickListener {
            // Edit profile dialog
            showEditProfileDialog()
        }
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Logout") { _, _ ->
                viewModel.logout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEditProfileDialog() {
        val user = viewModel.uiState.value.user ?: return
        
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_profile, null)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etName)
        val etPhone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPhone)
        
        etName.setText(user.displayName)
        etPhone.setText(user.phoneNumber)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Profile")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                viewModel.updateProfile(etName.text.toString(), etPhone.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) android.view.View.VISIBLE else android.view.View.GONE
                    
                    state.user?.let { user ->
                        binding.tvDisplayName.text = user.displayName.ifEmpty { "User" }
                        binding.tvEmail.text = user.email
                        binding.tvPhone.text = user.phoneNumber.ifEmpty { "No phone number added" }
                        binding.tvRewardBalance.text = "Rewards: ${user.rewardBalance} pts"
                    }
                    
                    state.error?.let { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                        viewModel.resetError()
                    }
                    
                    if (state.isLoggedOut) {
                        findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
                    }
                }
            }
        }
    }
}
