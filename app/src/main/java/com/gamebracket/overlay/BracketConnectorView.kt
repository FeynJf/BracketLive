package com.gamebracket.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Garis penghubung antar-babak (bentuk bracket): dua match → satu match di babak berikutnya. */
class BracketConnectorView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private var pairs = 0
    private var slotH = 0
    private var top = 0
    private var decided = BooleanArray(0)
    var onColor: Int = Color.parseColor("#00E5FF")
    var offColor: Int = Color.parseColor("#2C3860")

    fun setData(pairCount: Int, slotHeight: Int, topOffset: Int, decidedMatches: BooleanArray) {
        pairs = pairCount
        slotH = slotHeight
        top = topOffset
        decided = decidedMatches
        invalidate()
    }

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, on: Boolean) {
        paint.color = if (on) onColor else offColor
        c.drawLine(x1, y1, x2, y2, paint)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val xm = w / 2f
        for (i in 0 until pairs) {
            val a = decided.getOrElse(2 * i) { false }
            val b = decided.getOrElse(2 * i + 1) { false }
            val y1 = top + (2 * i + 0.5f) * slotH
            val y2 = top + (2 * i + 1.5f) * slotH
            val ym = (y1 + y2) / 2f
            line(canvas, 0f, y1, xm, y1, a)
            line(canvas, 0f, y2, xm, y2, b)
            line(canvas, xm, y1, xm, ym, a)
            line(canvas, xm, ym, xm, y2, b)
            line(canvas, xm, ym, w, ym, a && b)
        }
    }
}
