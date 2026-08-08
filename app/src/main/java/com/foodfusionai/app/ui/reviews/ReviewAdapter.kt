package com.foodfusionai.app.ui.reviews

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foodfusionai.app.R
import com.foodfusionai.app.data.models.Review
import com.foodfusionai.app.databinding.ItemReviewBinding
import com.google.firebase.auth.FirebaseAuth

class ReviewAdapter(
    private val onHelpfulClick: (Review) -> Unit,
    private val onEditClick: (Review) -> Unit,
    private val onDeleteClick: (Review) -> Unit,
    private val onReportClick: (Review) -> Unit
) : ListAdapter<Review, ReviewAdapter.ReviewViewHolder>(ReviewDiffCallback()) {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReviewViewHolder(private val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.btnHelpful.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onHelpfulClick(getItem(adapterPosition))
                }
            }
        }

        fun bind(review: Review) {
            binding.tvUserName.text = review.userName
            binding.ratingBar.rating = review.rating.toFloat()
            binding.tvComment.text = review.comment
            binding.tvComment.visibility = if (review.comment.isNotBlank()) View.VISIBLE else View.GONE
            binding.tvHelpfulCount.text = review.helpfulCount.toString()
            binding.tvEdited.visibility = if (review.isEdited) View.VISIBLE else View.GONE
            
            val timeString = DateUtils.getRelativeTimeSpanString(
                review.createdAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            binding.tvDate.text = timeString

            binding.btnMenu.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menuInflater.inflate(R.menu.menu_review_item, popup.menu)
                
                val isOwner = currentUserId == review.userId
                popup.menu.findItem(R.id.action_edit_review)?.isVisible = isOwner
                popup.menu.findItem(R.id.action_delete_review)?.isVisible = isOwner
                popup.menu.findItem(R.id.action_report_review)?.isVisible = !isOwner
                
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit_review -> {
                            onEditClick(review)
                            true
                        }
                        R.id.action_delete_review -> {
                            onDeleteClick(review)
                            true
                        }
                        R.id.action_report_review -> {
                            onReportClick(review)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    class ReviewDiffCallback : DiffUtil.ItemCallback<Review>() {
        override fun areItemsTheSame(oldItem: Review, newItem: Review) = oldItem.reviewId == newItem.reviewId
        override fun areContentsTheSame(oldItem: Review, newItem: Review) = oldItem == newItem
    }
}
