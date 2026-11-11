package com.gamebracket.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.SeekBar

/** Kumpulan animasi aplikasi. Semua otomatis nonaktif kalau "Animasi" dimatikan di Pengaturan. */
object Anim {

    fun enabled(ctx: Context) = Prefs.animations(ctx)

    /** Efek tekan: tombol mengecil saat ditekan lalu memantul kembali. Tidak mengganggu klik. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View) {
        if (!enabled(v.context)) return
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(240)
                        .setInterpolator(OvershootInterpolator(3f)).start()
            }
            false
        }
    }

    /** Pasang efek tekan ke semua view yang bisa diklik di dalam [root]. */
    fun pressAll(root: View) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) pressAll(root.getChildAt(i))
        }
        if (root.isClickable && root !is EditText && root !is SeekBar && root !is android.widget.CompoundButton) {
            pressable(root)
        }
    }

    /** Masuk berurutan: geser naik + muncul. */
    fun enter(views: List<View>, startDelay: Long = 0, step: Long = 60) {
        if (views.isEmpty() || !enabled(views[0].context)) return
        val dist = 20f * views[0].resources.displayMetrics.density
        views.forEachIndexed { i, v ->
            v.animate().cancel()
            v.alpha = 0f
            v.translationY = dist
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(startDelay + i * step).setDuration(340)
                .setInterpolator(DecelerateInterpolator(1.8f)).start()
        }
    }

    /** Anak-anak langsung dari [container] masuk berurutan. */
    fun enterChildren(container: ViewGroup, startDelay: Long = 0, step: Long = 55) {
        enter((0 until container.childCount).map { container.getChildAt(it) }, startDelay, step)
    }

    /** Memantul (angka skor berubah, juara muncul, dll). */
    fun pop(v: View) {
        if (!enabled(v.context)) return
        v.animate().cancel()
        v.scaleX = 0.55f
        v.scaleY = 0.55f
        v.animate().scaleX(1f).scaleY(1f).setDuration(380)
            .setInterpolator(OvershootInterpolator(3.2f)).start()
    }

    /** Panel muncul (fade + naik + sedikit membesar). */
    fun reveal(v: View) {
        if (!enabled(v.context)) return
        v.animate().cancel()
        v.alpha = 0f
        v.translationY = 12f * v.resources.displayMetrics.density
        v.scaleX = 0.97f
        v.scaleY = 0.97f
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(260).setInterpolator(DecelerateInterpolator(1.6f)).start()
    }
}
