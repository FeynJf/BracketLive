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

    var progress: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = stroke / 2f + 1f
        rect.set(pad, pad, w - pad, h - pad)
        paint.shader = SweepGradient(
            w / 2f, h / 2f,
            intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#7C4DFF"), Color.parseColor("#FF2E88"), Color.parseColor("#00E5FF")),
            null
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (progress <= 0f) return
        canvas.save()
        canvas.rotate(-90f, width / 2f, height / 2f)
        canvas.drawArc(rect, 0f, 360f * progress.coerceIn(0f, 1f), false, paint)
        canvas.restore()
    }
}
