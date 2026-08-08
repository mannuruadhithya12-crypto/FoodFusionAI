package com.foodfusionai.app.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.foodfusionai.app.databinding.FragmentNotificationsBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.foodfusionai.app.utils.hide
import com.foodfusionai.app.utils.show
import com.foodfusionai.app.utils.showToast
import kotlinx.coroutines.launch

class NotificationsFragment : BaseFragment<FragmentNotificationsBinding>() {

    private val viewModel: NotificationViewModel by viewModels { NotificationViewModel.Factory() }
    private lateinit var adapter: NotificationAdapter

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNotificationsBinding.inflate(inflater, container, false)

    override fun setupUI() {
        adapter = NotificationAdapter { notification ->
            viewModel.markAsRead(notification.id)
            handleNotificationClick(notification)
        }
        binding.rvNotifications.adapter = adapter
        
        binding.toolbar.setNavigationOnClickListener {
            navigateBack()
        }
        
        binding.toolbar.inflateMenu(com.foodfusionai.app.R.menu.menu_notifications)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                com.foodfusionai.app.R.id.action_mark_all_read -> {
                    viewModel.markAllAsRead()
                    true
                }
                else -> false
            }
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: NotificationUiState) {
        if (state.isLoading) {
            binding.progressBar.show()
            binding.rvNotifications.hide()
            binding.layoutEmpty.hide()
            return
        }

        binding.progressBar.hide()

        if (state.error != null) {
            requireContext().showToast(state.error)
        }

        if (state.notifications.isEmpty()) {
            binding.layoutEmpty.show()
            binding.rvNotifications.hide()
        } else {
            binding.layoutEmpty.hide()
            binding.rvNotifications.show()
            adapter.submitList(state.notifications)
        }
    }

    private fun handleNotificationClick(notification: com.foodfusionai.app.data.models.Notification) {
        try {
            when (notification.type) {
                "ORDER_UPDATE" -> {
                    val orderId = notification.data["orderId"]
                    if (orderId != null) {
                        val action = NotificationsFragmentDirections.actionNotificationsFragmentToOrderDetailsFragment(orderId)
                        findNavController().navigate(action)
                    }
                }
                "COUPON_AVAILABLE", "PROMO" -> {
                    // Navigate to offers
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
