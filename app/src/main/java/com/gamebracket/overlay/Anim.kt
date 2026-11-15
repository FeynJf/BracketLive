package com.gamebracket.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar

/**
 * Animasi aplikasi. Otomatis nonaktif kalau "Animasi" dimatikan di Pengaturan atau animasi sistem
 * dimatikan; durasi dan jumlah elemen menyesuaikan kemampuan perangkat ([DeviceProfile]).
 */
object Anim {

    fun enabled(ctx: Context) = Prefs.animations(ctx) && !DeviceProfile.systemAnimationsOff(ctx)

    private fun dur(ctx: Context, base: Long): Long = (base * DeviceProfile.animScale(ctx)).toLong().coerceAtLeast(70L)

    /** Efek tekan: tombol mengecil saat ditekan lalu memantul kembali. Tidak mengganggu klik. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View) {
        if (!enabled(v.context)) return
        val c = v.context
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(dur(c, 70)).setInterpolator(Ease.standard).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(dur(c, 200))
                        .setInterpolator(Ease.spring).start()
            }
            false
        }
    }

    /** Pasang efek tekan ke semua view yang bisa diklik di dalam [root]. */
    fun pressAll(root: View) {
        if (!enabled(root.context)) return
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) pressAll(root.getChildAt(i))
        }
        if (root.isClickable && root !is EditText && root !is SeekBar && root !is android.widget.CompoundButton) {
            pressable(root)
        }
    }

    /** Masuk berurutan: geser naik + muncul. Elemen di atas batas perangkat langsung tampil (hemat). */
    fun enter(views: List<View>, startDelay: Long = 0, step: Long = 40) {
        if (views.isEmpty()) return
        val ctx = views[0].context
        if (!enabled(ctx)) return
        val dist = 10f * views[0].resources.displayMetrics.density
        val limit = DeviceProfile.maxStagger(ctx)
        views.forEachIndexed { i, v ->
            if (i >= limit) return@forEachIndexed
            v.animate().cancel()
            v.alpha = 0f
            v.translationY = dist
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(startDelay + i * step).setDuration(dur(ctx, 280))
                .setInterpolator(Ease.decel).withLayer().start()
        }
    }

    /** Anak-anak langsung dari [container] masuk berurutan. */
    fun enterChildren(container: ViewGroup, startDelay: Long = 0, step: Long = 40) {
        enter((0 until container.childCount).map { container.getChildAt(it) }, startDelay, step)
    }

    /** Memantul (angka skor berubah, juara muncul, dll). */
    fun pop(v: View) {
        if (!enabled(v.context)) return
        v.animate().cancel()
        v.scaleX = 0.88f
        v.scaleY = 0.88f
        v.animate().scaleX(1f).scaleY(1f).setDuration(dur(v.context, 320))
            .setInterpolator(Ease.spring).withLayer().start()
    }

    /** Panel muncul (fade + naik + sedikit membesar). */
    fun reveal(v: View) {
        if (!enabled(v.context)) return
        v.animate().cancel()
        v.alpha = 0f
        v.translationY = 8f * v.resources.displayMetrics.density
        v.scaleX = 0.985f
        v.scaleY = 0.985f
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(dur(v.context, 240)).setInterpolator(Ease.decel).withLayer().start()
    }

    /** Dialog masuk: kartu membesar dari bawah sambil muncul. */
    fun dialogIn(card: View) {
        if (!enabled(card.context)) return
        card.alpha = 0f
        card.scaleX = 0.96f
        card.scaleY = 0.96f
        card.translationY = 16f * card.resources.displayMetrics.density
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(dur(card.context, 280)).setInterpolator(Ease.decel).withLayer().start()
    }

    /** Buka lipatan (tinggi bertambah halus). */
    fun expand(v: View) {
        val parentW = (v.parent as? View)?.width ?: 0
        if (!enabled(v.context) || parentW <= 0) { v.visibility = View.VISIBLE; return }
        v.measure(
            View.MeasureSpec.makeMeasureSpec(parentW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val target = v.measuredHeight
        v.layoutParams.height = 0
        v.visibility = View.VISIBLE
        v.alpha = 0f
        ValueAnimator.ofInt(0, target).apply {
            duration = dur(v.context, 260)
            interpolator = Ease.standard
            addUpdateListener {
                v.layoutParams.height = it.animatedValue as Int
                v.alpha = it.animatedFraction
                v.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    v.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    v.alpha = 1f
                    v.requestLayout()
                }
            })
            start()
        }
    }

    /** Tutup lipatan (tinggi berkurang halus). */
    fun collapse(v: View) {
        if (!enabled(v.context) || v.height <= 0) { v.visibility = View.GONE; return }
        val start = v.height
        ValueAnimator.ofInt(start, 0).apply {
            duration = dur(v.context, 220)
            interpolator = Ease.standard
            addUpdateListener {
                v.layoutParams.height = it.animatedValue as Int
                v.alpha = 1f - it.animatedFraction
                v.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    v.visibility = View.GONE
                    v.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    v.alpha = 1f
                }
            })
            start()
        }
    }

    /** Putar ikon panah (lipatan). */
    fun rotate(v: View, to: Float) {
        if (!enabled(v.context)) { v.rotation = to; return }
        v.animate().rotation(to).setDuration(dur(v.context, 220)).setInterpolator(Ease.standard).start()
    }

    /** Melayang pelan terus-menerus (mis. logo di layar kosong). Mengembalikan animator untuk dibatalkan; null di HP hemat. */
    fun floatLoop(v: View): ObjectAnimator? {
        val c = v.context
        if (!enabled(c) || DeviceProfile.tier(c) == DeviceProfile.Tier.LOW) return null
        val d = 5f * v.resources.displayMetrics.density
        return ObjectAnimator.ofFloat(v, "translationY", 0f, -d, 0f).apply {
            duration = 2800
            repeatCount = ValueAnimator.INFINITE
            interpolator = Ease.standard
            start()
        }
    }
}
