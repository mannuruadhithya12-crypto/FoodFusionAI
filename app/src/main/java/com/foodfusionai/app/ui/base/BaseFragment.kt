package com.foodfusionai.app.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar

/**
 * Base fragment for all fragments in the application.
 *
 * Architecture: Uses ViewBinding with proper lifecycle management (_binding nulled in onDestroyView
 * to prevent memory leaks). Provides optional hooks for loading/error/empty state views —
 * subclasses override the view provider methods to enable state management.
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null
    protected val binding get() = _binding!!

    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = getViewBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    abstract fun setupUI()
    abstract fun observeData()

    // -- State view providers: override in subclasses to enable state management --

    /** Override to return the ProgressBar or loading view from your layout */
    protected open fun getProgressBar(): ProgressBar? = null

    /** Override to return the error state container from your layout */
    protected open fun getErrorLayout(): View? = null

    /** Override to return the empty state container from your layout */
    protected open fun getEmptyLayout(): View? = null

    /** Override to return the main content container from your layout */
    protected open fun getContentLayout(): View? = null

    protected fun showLoading() {
        getProgressBar()?.visibility = View.VISIBLE
        getContentLayout()?.visibility = View.GONE
        getErrorLayout()?.visibility = View.GONE
        getEmptyLayout()?.visibility = View.GONE
    }

    protected fun hideLoading() {
        getProgressBar()?.visibility = View.GONE
    }

    protected fun showError(message: String, retryAction: (() -> Unit)? = null) {
        getErrorLayout()?.visibility = View.VISIBLE
        getContentLayout()?.visibility = View.GONE
        getProgressBar()?.visibility = View.GONE
        getEmptyLayout()?.visibility = View.GONE
    }

    protected fun showEmpty(message: String) {
        getEmptyLayout()?.visibility = View.VISIBLE
        getContentLayout()?.visibility = View.GONE
        getProgressBar()?.visibility = View.GONE
        getErrorLayout()?.visibility = View.GONE
    }

    protected fun showContent() {
        getContentLayout()?.visibility = View.VISIBLE
        hideLoading()
        getErrorLayout()?.visibility = View.GONE
        getEmptyLayout()?.visibility = View.GONE
    }

    protected fun showSnackbar(message: String, actionText: String? = null, action: (() -> Unit)? = null) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        if (actionText != null && action != null) {
            snackbar.setAction(actionText) { action.invoke() }
        }
        snackbar.show()
    }

    /**
     * Safe navigation helper — catches IllegalArgumentException that can occur
     * when a user double-taps a nav action before the first navigation completes.
     */
    protected fun navigateTo(actionId: Int, bundle: Bundle? = null) {
        try {
            findNavController().navigate(actionId, bundle)
        } catch (e: IllegalArgumentException) {
            // Occurs when action is not valid from current destination (e.g., double-tap race)
            android.util.Log.w("BaseFragment", "Navigation action $actionId ignored: ${e.message}")
        }
    }

    protected fun navigateBack() {
        findNavController().navigateUp()
    }
}
