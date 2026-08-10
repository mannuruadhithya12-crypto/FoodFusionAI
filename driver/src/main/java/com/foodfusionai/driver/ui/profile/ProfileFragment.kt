package com.foodfusionai.driver.ui.profile

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
import com.foodfusionai.driver.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val repository = DriverRepositoryImpl()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        fetchProfile(uid)

        binding.btnLogout.setOnClickListener {
            repository.logout()
            findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
        }
    }

    private fun fetchProfile(uid: String) {
        lifecycleScope.launch {
            repository.getDriverProfile(uid).collect { result ->
                result.fold(
                    onSuccess = { driver ->
                        binding.tvProfileName.text = driver.name
                        binding.tvProfileEmail.text = driver.email
                        binding.tvProfilePhone.text = "Phone: ${driver.phone}"
                        binding.tvProfileVehicle.text = "Vehicle: ${driver.vehicleType} (${driver.vehicleNumber})"
                        binding.tvProfileLicense.text = "License No: ${driver.licenseNumber}"
                    },
                    onFailure = { error ->
                        Toast.makeText(context, "Error fetching profile: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
