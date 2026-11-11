package com.gamebracket.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import kotlin.random.Random

/** Hujan confetti singkat saat juara ditentukan. */
class ConfettiView(context: Context) : View(context) {

    private class P(val x0: Float, val vx: Float, val vy: Float, val size: Float, val rot: Float, val color: Int)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var particles = emptyList<P>()
    private var t = 0f

    fun start(onEnd: () -> Unit) {
        val d = resources.displayMetrics.density
        val w = resources.displayMetrics.widthPixels.toFloat()
        val colors = intArrayOf(
            ThemeManager.accent1(context), ThemeManager.accent2(context), ThemeManager.accent3(context),
            Color.parseColor("#FFC83D"), Color.WHITE
        )
        particles = List(90) {
            P(
                x0 = Random.nextFloat() * w,
                vx = (Random.nextFloat() - 0.5f) * 140f * d,
                vy = (260f + Random.nextFloat() * 380f) * d,
                size = (5f + Random.nextFloat() * 6f) * d,
                rot = Random.nextFloat() * 360f,
                color = colors[Random.nextInt(colors.size)]
            )
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400
            interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onEnd() }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val secs = t * 2.4f
        val top = -40f
        for (p in particles) {
            val x = p.x0 + p.vx * secs
            val y = top + p.vy * secs
            if (y > height + 40) continue
            paint.color = p.color
            paint.alpha = ((1f - t * t) * 255).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.rotate(p.rot + secs * 280f, x, y)
            canvas.drawRect(x - p.size / 2, y - p.size / 4, x + p.size / 2, y + p.size / 4, paint)
            canvas.restore()
        }
    }

    companion object {
        fun play(activity: Activity) {
            if (!Anim.enabled(activity)) return
            val root = activity.window.decorView as ViewGroup
            val v = ConfettiView(activity)
            v.isClickable = false
            root.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            v.start { root.removeView(v) }
        }
    }
}
