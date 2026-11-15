package com.gamebracket.overlay

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Kurva gerak: standar Material + pegas teredam. Tanpa pantulan berlebihan supaya terasa natural. */
object Ease {
    /** Mulai cepat, berhenti halus: untuk perpindahan umum. */
    val standard: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    /** Muncul: sangat cepat di awal, melambat panjang. */
    val decel: Interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    /** Menghilang: pelan di awal lalu cepat. */
    val accel: Interpolator = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    /** Pegas teredam: sedikit melewati target (~3%) lalu mengendap. */
    val spring: Interpolator = SpringInterpolator()
}

class SpringInterpolator(private val zeta: Double = 0.75, private val omega: Double = 16.0) : Interpolator {
    private val wd = omega * sqrt(1 - zeta * zeta)
    override fun getInterpolation(t: Float): Float {
        if (t >= 1f) return 1f
        val x = t.toDouble()
        val v = 1 - exp(-zeta * omega * x) * (cos(wd * x) + (zeta * omega / wd) * sin(wd * x))
        return v.toFloat()
    }
}
