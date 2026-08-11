package com.foodfusionai.app.ui.profile.address

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentAddEditAddressBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.ui.location.MapPickerViewModel
import kotlinx.coroutines.launch

class AddEditAddressFragment : BaseFragment<FragmentAddEditAddressBinding>() {

    private val viewModel: AddressViewModel by viewModels { AddressViewModel.Factory() }

    // Phase 16: shared MapPickerViewModel (scoped to Activity) to receive
    // the pin-confirmed coordinates back from MapPickerFragment
    private val mapPickerViewModel: MapPickerViewModel by activityViewModels {
        MapPickerViewModel.Factory(requireContext())
    }

    private val args: AddEditAddressFragmentArgs by navArgs()

    // Holds coordinates obtained from the map picker
    private var pickedLatitude: Double = 0.0
    private var pickedLongitude: Double = 0.0
    private var pickedGeohash: String = ""
    private var pickedPlaceId: String = ""

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

        // Phase 16: "Pick on Map" button
        binding.btnPickOnMap.setOnClickListener {
            findNavController().navigate(R.id.action_addEditAddressFragment_to_mapPickerFragment)
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

                // Restore coordinates if already set
                pickedLatitude  = address.latitude
                pickedLongitude = address.longitude
                pickedGeohash   = address.geohash
                pickedPlaceId   = address.placeId

                if (address.hasCoordinates) {
                    binding.tvPickedLocation.text = "📍 Location on map confirmed"
                    binding.tvPickedLocation.visibility = android.view.View.VISIBLE
                }

                when (address.type.lowercase()) {
                    "home" -> binding.tgAddressType.check(R.id.btnTypeHome)
                    "work" -> binding.tgAddressType.check(R.id.btnTypeWork)
                    else   -> binding.tgAddressType.check(R.id.btnTypeOther)
                }
            }
        }
    }

    private fun saveAddress() {
        val type = when (binding.tgAddressType.checkedButtonId) {
            R.id.btnTypeHome -> "Home"
            R.id.btnTypeWork -> "Work"
            else             -> "Other"
        }

        viewModel.saveAddress(
            id              = args.addressId,
            recipientName   = binding.etRecipientName.text.toString(),
            phoneNumber     = binding.etPhoneNumber.text.toString(),
            type            = type,
            street          = binding.etStreet.text.toString(),
            city            = binding.etCity.text.toString(),
            state           = binding.etState.text.toString(),
            zipCode         = binding.etZipCode.text.toString(),
            landmark        = binding.etLandmark.text.toString(),
            instructions    = binding.etInstructions.text.toString(),
            latitude        = pickedLatitude,
            longitude       = pickedLongitude,
            geohash         = pickedGeohash,
            placeId         = pickedPlaceId
        )
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeAddressState() }
                // Phase 16: observe map picker result
                launch { observeMapPickerResult() }
            }
        }
    }

    private suspend fun observeAddressState() {
        viewModel.uiState.collect { state ->
            binding.progressBar.visibility =
                if (state.isLoading) android.view.View.VISIBLE else android.view.View.GONE
            binding.btnSaveAddress.isEnabled = !state.isLoading

            // Clear previous errors
            listOf(
                binding.tilRecipientName to "recipientName",
                binding.tilPhoneNumber   to "phoneNumber",
                binding.tilStreet        to "street",
                binding.tilCity          to "city",
                binding.tilState         to "state",
                binding.tilZipCode       to "zipCode"
            ).forEach { (til, key) ->
                til.error = state.validationError[key]
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

    private suspend fun observeMapPickerResult() {
        mapPickerViewModel.confirmedPoint.collect { point ->
            if (point != null) {
                pickedLatitude  = point.latitude
                pickedLongitude = point.longitude
                pickedGeohash   = com.foodfusionai.app.data.location.GeoHashUtil.encode(point)
                pickedPlaceId   = "" // not using Places SDK directly here

                // Fill in address fields from reverse-geocoded result
                mapPickerViewModel.confirmedAddress.value?.let { resolved ->
                    if (binding.etStreet.text.isNullOrBlank()) {
                        binding.etStreet.setText(resolved.shortName)
                    }
                    if (binding.etCity.text.isNullOrBlank()) {
                        binding.etCity.setText(resolved.city)
                    }
                    if (binding.etState.text.isNullOrBlank()) {
                        binding.etState.setText(resolved.state)
                    }
                    if (binding.etZipCode.text.isNullOrBlank()) {
                        binding.etZipCode.setText(resolved.postalCode)
                    }
                }

                binding.tvPickedLocation.text = "📍 Location on map confirmed"
                binding.tvPickedLocation.visibility = android.view.View.VISIBLE

                mapPickerViewModel.clearConfirmation()
            }
        }
    }
}
