package com.foodfusionai.app.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import com.foodfusionai.app.databinding.FragmentProfileBinding
import com.foodfusionai.app.ui.base.BaseFragment

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentProfileBinding.inflate(inflater, container, false)

    override fun setupUI() {
        // Placeholder setup
    }

    override fun observeData() {
        // Placeholder observe
    }
}
