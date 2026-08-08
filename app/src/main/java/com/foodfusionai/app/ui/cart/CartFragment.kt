package com.foodfusionai.app.ui.cart

import android.view.LayoutInflater
import android.view.ViewGroup
import com.foodfusionai.app.databinding.FragmentCartBinding
import com.foodfusionai.app.ui.base.BaseFragment

class CartFragment : BaseFragment<FragmentCartBinding>() {

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentCartBinding.inflate(inflater, container, false)

    override fun setupUI() {
        // Placeholder setup
    }

    override fun observeData() {
        // Placeholder observe
    }
}
