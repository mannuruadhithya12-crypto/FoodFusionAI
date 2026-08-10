package com.foodfusionai.driver.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.foodfusionai.driver.R
import com.foodfusionai.driver.data.models.Driver
import com.foodfusionai.driver.data.repository.DriverRepositoryImpl
import com.foodfusionai.driver.databinding.FragmentOnboardingBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!
    private val repository = DriverRepositoryImpl()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Populate Spinner
        val vehicleTypes = arrayOf("BIKE", "SCOOTER", "CAR", "BICYCLE")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, vehicleTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerVehicleType.adapter = adapter

        val isAlreadyLoggedIn = FirebaseAuth.getInstance().currentUser != null

        if (isAlreadyLoggedIn) {
            binding.btnSubmit.text = "COMPLETE PROFILE"
        }

        binding.btnSubmit.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val emergency = binding.etEmergency.text.toString().trim()
            val vehicleNumber = binding.etVehicleNumber.text.toString().trim()
            val licenseNumber = binding.etLicenseNumber.text.toString().trim()
            val vehicleType = binding.spinnerVehicleType.selectedItem.toString()

            if (name.isEmpty() || phone.isEmpty() || emergency.isEmpty() || vehicleNumber.isEmpty() || licenseNumber.isEmpty()) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.loading.visibility = View.VISIBLE
            binding.btnSubmit.isEnabled = false

            val driverProfile = Driver(
                name = name,
                phone = phone,
                emergencyContact = emergency,
                vehicleType = vehicleType,
                vehicleNumber = vehicleNumber,
                licenseNumber = licenseNumber,
                status = "PENDING",
                availability = "OFFLINE"
            )

            lifecycleScope.launch {
                if (isAlreadyLoggedIn) {
                    // Update existing driver profile
                    val uid = FirebaseAuth.getInstance().currentUser!!.uid
                    val driverRef = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("drivers").document(uid)
                    try {
                        driverRef.set(driverProfile.copy(uid = uid)).await()
                        binding.loading.visibility = View.GONE
                        findNavController().navigate(R.id.action_onboardingFragment_to_pendingApprovalFragment)
                    } catch (e: Exception) {
                        binding.loading.visibility = View.GONE
                        binding.btnSubmit.isEnabled = true
                        Toast.makeText(context, e.message ?: "Failed to update profile", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Sign up completely new user
                    // We can generate a dummy email/password or use standard.
                    // For the onboarding flow, if they are not logged in, we assume they register using email.
                    // Let's prompt or use dummy credentials or standard signup form.
                    // Wait, let's create a popup/dialog or use name.toLowerCase() + "@driver.foodfusion.com" as email, and phone as password.
                    // This is exceptionally smooth for simple operational onboarding!
                    val email = "${name.replace("\\s".toRegex(), "").lowercase()}@driver.foodfusion.com"
                    val password = phone.takeLast(6).ifEmpty { "123456" }
                    
                    repository.register(email, password, driverProfile).collect { result ->
                        binding.loading.visibility = View.GONE
                        binding.btnSubmit.isEnabled = true

                        result.fold(
                            onSuccess = {
                                Toast.makeText(context, "Registration successful! Email: $email", Toast.LENGTH_LONG).show()
                                findNavController().navigate(R.id.action_onboardingFragment_to_pendingApprovalFragment)
                            },
                            onFailure = { error ->
                                Toast.makeText(context, error.message ?: "Registration failed", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Task await helper
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result, null)
            } else {
                cont.resumeWith(Result.failure(task.exception ?: Exception("Task failed")))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
