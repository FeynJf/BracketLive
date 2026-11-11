package com.gamebracket.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

/** Desain latar panel overlay. [ctx] harus ber-tema (untuk warna aksen/permukaan). */
object PanelDesign {

    val names = listOf("Kaca Hitam", "Neon", "Solid Tema", "Gradien Aksen", "Minimal", "Kaca Blur")
    const val BLUR = 5

    fun background(ctx: Context, style: Int, radiusDp: Int, opacity: Int): Drawable {
        val d = ctx.resources.displayMetrics.density
        val r = radiusDp * d
        val a = (opacity.coerceIn(0, 100) * 255 / 100)
        val a1 = ThemeManager.accent1(ctx)
        val a2 = ThemeManager.accent2(ctx)

        fun box(colors: IntArray, strokeColor: Int? = null, strokeDp: Float = 1f, radius: Float = r) =
            GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                cornerRadius = radius
                if (strokeColor != null) setStroke((strokeDp * d).toInt().coerceAtLeast(1), strokeColor)
            }

        return when (style) {
            1 -> { // Neon: tepi gradien aksen
                val ring = (2f * d).toInt().coerceAtLeast(2)
                LayerDrawable(arrayOf(
                    box(intArrayOf(a1, a2)),
                    box(intArrayOf(Color.argb(a, 0, 0, 0), Color.argb(a, 0, 0, 0)), radius = (r - ring).coerceAtLeast(0f))
                )).apply { setLayerInset(1, ring, ring, ring, ring) }
            }
            2 -> box( // Solid Tema: warna permukaan tema
                intArrayOf(
                    ThemeManager.withAlpha(ThemeManager.color(ctx, R.attr.blSurface), a),
                    ThemeManager.withAlpha(ThemeManager.color(ctx, R.attr.blBg2), a)
                ),
                ThemeManager.color(ctx, R.attr.blStroke)
            )
            3 -> box( // Gradien Aksen
                intArrayOf(ThemeManager.withAlpha(a1, (a * 0.45f).toInt()), Color.argb(a, 0, 0, 0)),
                ThemeManager.withAlpha(a1, 140)
            )
            4 -> box(intArrayOf(Color.argb((a * 0.8f).toInt(), 0, 0, 0), Color.argb((a * 0.8f).toInt(), 0, 0, 0))) // Minimal
            5 -> box( // Kaca Blur: lebih tipis; blur dari sistem (Android 12+)
                intArrayOf(Color.argb(minOf(a, 120), 0, 0, 0), Color.argb(minOf(a, 120), 0, 0, 0)),
                Color.argb(70, 255, 255, 255)
            )
            else -> box( // Kaca Hitam
                intArrayOf(Color.argb(a, 0, 0, 0), Color.argb(a, 0, 0, 0)),
                Color.argb(70, 255, 255, 255)
            )
        }
    }
}
