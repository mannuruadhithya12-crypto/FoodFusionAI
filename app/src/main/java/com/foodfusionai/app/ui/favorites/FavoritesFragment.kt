package com.foodfusionai.app.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.foodfusionai.app.databinding.FragmentFavoritesBinding
import com.foodfusionai.app.ui.base.BaseFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class FavoritesFragment : BaseFragment<FragmentFavoritesBinding>() {

    private val viewModel: FavoriteViewModel by viewModels { FavoriteViewModel.Factory() }
    private lateinit var favoriteAdapter: FavoriteAdapter
    private var currentTab = 0 // 0 = Restaurants, 1 = Foods

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentFavoritesBinding.inflate(inflater, container, false)

    override fun setupUI() {
        favoriteAdapter = FavoriteAdapter(
            onItemClick = { favorite ->
                // Navigation to details would happen here
                // e.g. findNavController().navigate(...)
            },
            onRemoveClick = { favorite ->
                viewModel.toggleFavorite(
                    favorite.targetId,
                    favorite.targetType,
                    favorite.targetName,
                    favorite.imageUrl,
                    favorite.restaurantId
                )
            }
        )

        binding.rvFavorites.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = favoriteAdapter
        }

        binding.tabLayout.apply {
            addTab(newTab().setText("Restaurants"))
            addTab(newTab().setText("Foods"))
            
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    currentTab = tab?.position ?: 0
                    updateListForCurrentTab()
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }

    private fun updateListForCurrentTab() {
        val state = viewModel.uiState.value
        val list = if (currentTab == 0) state.favoriteRestaurants else state.favoriteFoods
        favoriteAdapter.submitList(list)
        
        binding.layoutEmpty.visibility = if (list.isEmpty() && !state.isLoading) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading || state.isUpdating) android.view.View.VISIBLE else android.view.View.GONE
                    
                    if (!state.isLoading) {
                        updateListForCurrentTab()
                    }

                    state.error?.let { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                        viewModel.resetError()
                    }
                }
            }
        }
    }
}
