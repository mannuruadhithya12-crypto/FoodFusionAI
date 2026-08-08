package com.foodfusionai.app.ui.orders

import android.view.LayoutInflater
import android.view.ViewGroup
import com.foodfusionai.app.databinding.FragmentOrdersBinding
import com.foodfusionai.app.ui.base.BaseFragment

class OrdersFragment : BaseFragment<FragmentOrdersBinding>() {

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentOrdersBinding.inflate(inflater, container, false)

    override fun setupUI() {
        // Placeholder setup
    }

    override fun observeData() {
        // Placeholder observe
    }
}
