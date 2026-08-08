package com.foodfusionai.app.ui.base

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * Base activity for all activities in the application.
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: VB
    private var loadingDialog: AlertDialog? = null

    abstract fun getViewBinding(): VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = getViewBinding()
        setContentView(binding.root)
    }

    protected open fun setupToolbar(toolbar: MaterialToolbar, showBack: Boolean = false) {
        setSupportActionBar(toolbar)
        if (showBack) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
    }

    protected fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(binding.root, message, duration).show()
    }

    protected fun showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = MaterialAlertDialogBuilder(this)
                .setTitle("Loading")
                .setMessage("Please wait...")
                .setCancelable(false)
                .create()
        }
        loadingDialog?.show()
    }

    protected fun hideLoadingDialog() {
        loadingDialog?.dismiss()
    }
}
