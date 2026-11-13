package com.gamebracket.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator

/** Bilah progres neon bergradien dengan kilau yang bergerak. Dipakai di dialog download update. */
class NeonProgressView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = ThemeManager.color(context, R.attr.blSurface2)
    private val a1 = ThemeManager.accent1(context)
    private val a2 = ThemeManager.accent2(context)
    private val rect = RectF()
    private val clip = Path()
    private var shown = 0f
    private var target = 0f
    private var shimmer = 0f
    private var shimmerAnim: ValueAnimator? = null
    private var progressAnim: ValueAnimator? = null

    /** Isi 0..1 (dianimasikan halus). */
    fun setProgress(p: Float) {
        target = p.coerceIn(0f, 1f)
        progressAnim?.cancel()
        if (!Anim.enabled(context)) { shown = target; invalidate(); return }
        progressAnim = ValueAnimator.ofFloat(shown, target).apply {
            duration = 220
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Anim.enabled(context)) {
            shimmerAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = if (DeviceProfile.tier(context) == DeviceProfile.Tier.LOW) 1800 else 1300
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { shimmer = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        shimmerAnim?.cancel()
        progressAnim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        rect.set(0f, 0f, w, h)
        paint.shader = null
        paint.color = track
        canvas.drawRoundRect(rect, r, r, paint)

        val fillW = w * shown
        if (fillW <= 0f) return
        clip.reset()
        clip.addRoundRect(RectF(0f, 0f, fillW, h), r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        paint.shader = LinearGradient(0f, 0f, maxOf(fillW, 1f), 0f, a1, a2, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, fillW, h, paint)
        // kilau bergerak
        val band = h * 4f
        val x = -band + (fillW + band * 2f) * shimmer
        paint.shader = LinearGradient(
            x, 0f, x + band, 0f,
            intArrayOf(0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, fillW, h, paint)
        canvas.restore()
        paint.shader = null
    }
}
