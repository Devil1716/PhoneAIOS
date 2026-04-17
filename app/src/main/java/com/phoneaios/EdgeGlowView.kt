package com.phoneaios

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class EdgeGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 18f
        color = Color.parseColor("#2D5CFE")
        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    }

    private var amplitude = 0f

    fun setAmplitude(value: Float) {
        amplitude = value.coerceIn(0f, 1f)
        invalidate()
    }

    fun flashSuccess() {
        setAmplitude(1f)
        postDelayed({ setAmplitude(0f) }, 500L)
    }

    override fun onDraw(canvas: Canvas) {
        if (amplitude <= 0f) return
        paint.alpha = (255 * amplitude).toInt()
        canvas.drawRect(0f, height - 18f, width.toFloat(), height.toFloat(), paint)
    }
}
