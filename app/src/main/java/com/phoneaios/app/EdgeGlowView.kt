package com.phoneaios.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class EdgeGlowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var amplitude = 0f

    init {
        paint.style = Paint.Style.FILL
    }

    fun setAmplitude(value: Float) {
        amplitude = value.coerceIn(0f, 1f)
        invalidate()
    }

    fun flashSuccess() {
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f, 0f)
        animator.duration = 500
        animator.addUpdateListener {
            amplitude = it.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val glowHeight = height * (0.4f + amplitude * 0.4f)

        // Minimalist Cursor-inspired gradient
        val gradient = LinearGradient(
            0f, height, 0f, height - glowHeight,
            intArrayOf(
                Color.parseColor("#332D5CFE"), // More subtle blue
                Color.parseColor("#662D5CFE"), // Main blue
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )
        
        paint.shader = gradient
        canvas.drawRect(0f, height - glowHeight, width, height, paint)
        
        // Single 2dp line for success/active state
        paint.shader = null
        paint.color = Color.parseColor("#AA2D5CFE")
        canvas.drawRect(0f, height - 2, width, height, paint)
    }
}
