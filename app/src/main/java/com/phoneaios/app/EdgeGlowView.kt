package com.phoneaios.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class EdgeGlowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
    }

    private var amplitude = 0f
    private val glowColor = Color.parseColor("#2D5CFE")

    fun setAmplitude(amp: Float) {
        amplitude = amp.coerceIn(0f, 1f)
        invalidate()
    }

    fun flashSuccess() {
        // Simple success flash
        amplitude = 1f
        postDelayed({
            amplitude = 0f
            invalidate()
        }, 500)
    }

    override fun onDraw(canvas: Canvas) {
        if (amplitude <= 0) return
        
        paint.color = glowColor
        paint.alpha = (amplitude * 255).toInt()
        
        // Draw glow at the bottom edge
        canvas.drawRect(0f, height - 20f, width.toFloat(), height.toFloat(), paint)
    }
}
