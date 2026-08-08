package com.foodfusionai.app.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Custom Shimmer View using LinearGradient and ObjectAnimator.
 */
class ShimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint()
    private var animator: ValueAnimator? = null
    private var gradientOffset = 0f
    
    private val baseColor = Color.parseColor("#E0E0E0")
    private val highlightColor = Color.parseColor("#F5F5F5")
    private val duration = 1500L

    init {
        paint.isAntiAlias = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupGradient()
    }

    private fun setupGradient() {
        if (width > 0 && height > 0) {
            val shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                intArrayOf(baseColor, highlightColor, baseColor),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = shader
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.translate(gradientOffset, 0f)
        canvas.drawRect(-gradientOffset, 0f, width - gradientOffset, height.toFloat(), paint)
    }

    fun startShimmer() {
        if (animator?.isRunning == true) return
        
        animator = ValueAnimator.ofFloat(-width.toFloat(), width.toFloat()).apply {
            this.duration = this@ShimmerView.duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                gradientOffset = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopShimmer() {
        animator?.cancel()
        animator = null
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startShimmer()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopShimmer()
    }
}
