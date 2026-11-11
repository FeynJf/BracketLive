package com.gamebracket.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View

/** Cincin progres yang terisi saat ikon overlay ditahan. */
class HoldRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val stroke = 3f * resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private var c1 = Color.parseColor("#00E5FF")
    private var c2 = Color.parseColor("#7C4DFF")
    private var c3 = Color.parseColor("#FF2E88")

    fun setColors(a: Int, b: Int, c: Int) {
        c1 = a; c2 = b; c3 = c
        if (width > 0) applyShader(width, height)
        invalidate()
    }

    private fun applyShader(w: Int, h: Int) {
        paint.shader = SweepGradient(w / 2f, h / 2f, intArrayOf(c1, c2, c3, c1), null)
    }

    var progress: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = stroke / 2f + 1f
        rect.set(pad, pad, w - pad, h - pad)
        applyShader(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        if (progress <= 0f) return
        canvas.save()
        canvas.rotate(-90f, width / 2f, height / 2f)
        canvas.drawArc(rect, 0f, 360f * progress.coerceIn(0f, 1f), false, paint)
        canvas.restore()
    }
}
