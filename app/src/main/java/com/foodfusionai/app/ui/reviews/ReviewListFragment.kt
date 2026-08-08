package com.foodfusionai.app.ui.reviews

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.foodfusionai.app.databinding.FragmentReviewListBinding
import com.foodfusionai.app.ui.base.BaseFragment
import kotlinx.coroutines.launch

class ReviewListFragment : BaseFragment<FragmentReviewListBinding>() {

    private val viewModel: ReviewViewModel by viewModels { ReviewViewModel.Factory() }
    private val args: ReviewListFragmentArgs by navArgs()
    private lateinit var reviewAdapter: ReviewAdapter

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentReviewListBinding.inflate(inflater, container, false)

    override fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        reviewAdapter = ReviewAdapter(
            onHelpfulClick = { review ->
                viewModel.interactReview(review.reviewId, "HELPFUL")
            },
            onEditClick = { review ->
                val action = ReviewListFragmentDirections.actionReviewListFragmentToWriteReviewFragment(
                    orderId = review.orderId,
                    restaurantId = review.restaurantId,
                    foodId = review.foodId,
                    isEdit = true,
                    reviewId = review.reviewId,
                    existingRating = review.rating,
                    existingComment = review.comment
                )
                findNavController().navigate(action)
            },
            onDeleteClick = { review ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Review")
                    .setMessage("Are you sure you want to delete this review?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteReview(review.reviewId)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onReportClick = { review ->
                val reasons = arrayOf("Spam", "Offensive content", "Fake review", "Irrelevant", "Other")
                AlertDialog.Builder(requireContext())
                    .setTitle("Report Review")
                    .setItems(reasons) { _, which ->
                        viewModel.interactReview(review.reviewId, "REPORT", reasons[which])
                        Toast.makeText(requireContext(), "Review reported", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvReviews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reviewAdapter
        }

        viewModel.loadReviews(args.targetId)
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) android.view.View.VISIBLE else android.view.View.GONE
                    
                    if (!state.isLoading) {
                        reviewAdapter.submitList(state.reviews)
                        binding.layoutEmpty.visibility = if (state.reviews.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
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
