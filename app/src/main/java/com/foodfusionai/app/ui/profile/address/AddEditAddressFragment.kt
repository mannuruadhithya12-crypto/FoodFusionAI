package com.foodfusionai.app.ui.profile.address

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentAddEditAddressBinding
import com.foodfusionai.app.ui.base.BaseFragment
import kotlinx.coroutines.launch

class AddEditAddressFragment : BaseFragment<FragmentAddEditAddressBinding>() {

    private val viewModel: AddressViewModel by viewModels { AddressViewModel.Factory() }
    private val args: AddEditAddressFragmentArgs by navArgs()

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAddEditAddressBinding.inflate(inflater, container, false)

    override fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.toolbar.title = if (args.addressId == null) "Add Address" else "Edit Address"

        binding.btnSaveAddress.setOnClickListener {
            saveAddress()
        }

        // Pre-fill if editing
        args.addressId?.let { id ->
            val existingAddress = viewModel.uiState.value.addresses.find { it.id == id }
            existingAddress?.let { address ->
                binding.etRecipientName.setText(address.recipientName)
                binding.etPhoneNumber.setText(address.phoneNumber)
                binding.etStreet.setText(address.street)
                binding.etCity.setText(address.city)
                binding.etState.setText(address.state)
                binding.etZipCode.setText(address.zipCode)
                binding.etLandmark.setText(address.landmark)
                binding.etInstructions.setText(address.instructions)
                
                when (address.type.lowercase()) {
                    "home" -> binding.tgAddressType.check(R.id.btnTypeHome)
                    "work" -> binding.tgAddressType.check(R.id.btnTypeWork)
                    else -> binding.tgAddressType.check(R.id.btnTypeOther)
                }
            }
        }
    }

    private fun saveAddress() {
        val type = when (binding.tgAddressType.checkedButtonId) {
            R.id.btnTypeHome -> "Home"
            R.id.btnTypeWork -> "Work"
            else -> "Other"
        }

        viewModel.saveAddress(
            id = args.addressId,
            recipientName = binding.etRecipientName.text.toString(),
            phoneNumber = binding.etPhoneNumber.text.toString(),
            type = type,
            street = binding.etStreet.text.toString(),
            city = binding.etCity.text.toString(),
            state = binding.etState.text.toString(),
            zipCode = binding.etZipCode.text.toString(),
            landmark = binding.etLandmark.text.toString(),
            instructions = binding.etInstructions.text.toString()
        )
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) android.view.View.VISIBLE else android.view.View.GONE
                    binding.btnSaveAddress.isEnabled = !state.isLoading

                    // Clear previous errors
                    binding.tilRecipientName.error = null
                    binding.tilPhoneNumber.error = null
                    binding.tilStreet.error = null
                    binding.tilCity.error = null
                    binding.tilState.error = null
                    binding.tilZipCode.error = null

                    // Set validation errors
                    state.validationError.forEach { (field, message) ->
                        when (field) {
                            "recipientName" -> binding.tilRecipientName.error = message
                            "phoneNumber" -> binding.tilPhoneNumber.error = message
                            "street" -> binding.tilStreet.error = message
                            "city" -> binding.tilCity.error = message
                            "state" -> binding.tilState.error = message
                            "zipCode" -> binding.tilZipCode.error = message
                        }
                    }

                    if (state.isSuccess) {
                        Toast.makeText(requireContext(), "Address saved successfully", Toast.LENGTH_SHORT).show()
                        viewModel.resetState()
                        findNavController().navigateUp()
                    }

                    state.error?.let { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                        viewModel.resetState()
                    }
                }
            }
        }
    }
}
