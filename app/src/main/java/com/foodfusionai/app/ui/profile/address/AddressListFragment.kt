package com.foodfusionai.app.ui.profile.address

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.foodfusionai.app.R
import com.foodfusionai.app.databinding.FragmentAddressListBinding
import com.foodfusionai.app.ui.base.BaseFragment
import kotlinx.coroutines.launch

class AddressListFragment : BaseFragment<FragmentAddressListBinding>() {

    private val viewModel: AddressViewModel by viewModels { AddressViewModel.Factory() }
    private lateinit var addressAdapter: AddressAdapter

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAddressListBinding.inflate(inflater, container, false)

    override fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        addressAdapter = AddressAdapter(
            onEditClick = { address ->
                val action = AddressListFragmentDirections.actionAddressListFragmentToAddEditAddressFragment(address.id)
                findNavController().navigate(action)
            },
            onDeleteClick = { address ->
                viewModel.deleteAddress(address.id)
            },
            onSetDefaultClick = { address ->
                if (!address.isDefault) {
                    viewModel.setDefaultAddress(address.id)
                }
            }
        )

        binding.rvAddresses.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = addressAdapter
        }

        binding.fabAddAddress.setOnClickListener {
            findNavController().navigate(R.id.action_addressListFragment_to_addEditAddressFragment)
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) android.view.View.VISIBLE else android.view.View.GONE
                    
                    if (!state.isLoading) {
                        addressAdapter.submitList(state.addresses)
                        binding.tvEmptyState.visibility = if (state.addresses.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
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
