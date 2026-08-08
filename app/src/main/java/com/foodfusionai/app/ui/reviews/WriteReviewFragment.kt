package com.foodfusionai.app.ui.reviews

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
import com.foodfusionai.app.databinding.FragmentWriteReviewBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.ui.profile.ProfileViewModel
import kotlinx.coroutines.launch

class WriteReviewFragment : BaseFragment<FragmentWriteReviewBinding>() {

    private val viewModel: ReviewViewModel by viewModels { ReviewViewModel.Factory() }
    private val profileViewModel: ProfileViewModel by viewModels { ProfileViewModel.Factory() }
    private val args: WriteReviewFragmentArgs by navArgs()

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentWriteReviewBinding.inflate(inflater, container, false)

    override fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        if (args.isEdit && args.reviewId != null) {
            binding.toolbar.title = "Edit Review"
            binding.ratingBar.rating = args.existingRating.toFloat()
            binding.etComment.setText(args.existingComment)
        }

        binding.btnSubmit.setOnClickListener {
            submitReview()
        }
    }

    private fun submitReview() {
        val rating = binding.ratingBar.rating.toInt()
        val comment = binding.etComment.text.toString().trim()

        if (rating == 0) {
            Toast.makeText(requireContext(), "Please select a rating", Toast.LENGTH_SHORT).show()
            return
        }

        if (comment.isNotEmpty() && (comment.length < 5 || comment.length > 1000)) {
            binding.tilComment.error = "Comment must be between 5 and 1000 characters"
            return
        }
        binding.tilComment.error = null

        if (args.isEdit && args.reviewId != null) {
            viewModel.editReview(args.reviewId!!, rating, comment)
        } else {
            val userName = profileViewModel.uiState.value.user?.displayName ?: "User"
            viewModel.submitReview(
                orderId = args.orderId ?: "",
                restaurantId = args.restaurantId ?: "",
                foodId = args.foodId?.takeIf { it.isNotEmpty() },
                rating = rating,
                comment = comment,
                userName = userName
            )
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isSubmitting) android.view.View.VISIBLE else android.view.View.GONE
                    binding.btnSubmit.isEnabled = !state.isSubmitting
                    binding.ratingBar.isEnabled = !state.isSubmitting
                    binding.etComment.isEnabled = !state.isSubmitting

                    if (state.isSuccess) {
                        val msg = if (args.isEdit) "Review updated" else "Review submitted"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        viewModel.resetState()
                        findNavController().navigateUp()
                    }

                    state.error?.let { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                        viewModel.resetState()
                    }
                }
            }
        }
    }
}
