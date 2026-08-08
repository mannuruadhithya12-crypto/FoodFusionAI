package com.foodfusionai.app.utils

import android.os.SystemClock
import android.view.View

/**
 * A View.OnClickListener that prevents multiple rapid clicks.
 */
class SafeClickListener(
    private val interval: Long = 600,
    private val onSafeClick: (View) -> Unit
) : View.OnClickListener {

    private var lastClickTime: Long = 0

    override fun onClick(v: View) {
        if (SystemClock.elapsedRealtime() - lastClickTime < interval) {
            return
        }
        lastClickTime = SystemClock.elapsedRealtime()
        onSafeClick(v)
    }
}
