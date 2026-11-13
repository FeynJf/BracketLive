package com.gamebracket.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

/** Desain latar panel overlay. [ctx] harus ber-tema (untuk warna aksen/permukaan). */
object PanelDesign {

    val names = listOf(
        "Kaca Hitam", "Neon", "Solid Tema", "Gradien Aksen", "Minimal", "Kaca Blur",
        "Biru Malam", "Emas Premium", "Terminal Hijau", "Merah Api", "Ungu Galaxy", "Garis Ganda", "Sudut Potong"
    )
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

        fun dark(c: Int) = ThemeManager.withAlpha(c, a)
        fun ring(c1: Int, c2: Int, inner: Drawable, ringDp: Float = 2f): Drawable {
            val w = (ringDp * d).toInt().coerceAtLeast(2)
            return LayerDrawable(arrayOf(box(intArrayOf(c1, c2)), inner)).apply { setLayerInset(1, w, w, w, w) }
        }
        fun innerBox(colors: IntArray, ringDp: Float = 2f) = box(colors, radius = (r - ringDp * d).coerceAtLeast(0f))

        return when (style) {
            6 -> box( // Biru Malam
                intArrayOf(dark(Color.parseColor("#0B1030")), dark(Color.parseColor("#04050C"))),
                ThemeManager.withAlpha(Color.parseColor("#4D6BFF"), 150)
            )
            7 -> ring( // Emas Premium
                Color.parseColor("#FFC83D"), Color.parseColor("#C68A00"),
                innerBox(intArrayOf(dark(Color.parseColor("#1A1405")), dark(Color.parseColor("#050400"))))
            )
            8 -> box( // Terminal Hijau
                intArrayOf(dark(Color.parseColor("#020A05")), dark(Color.parseColor("#000000"))),
                Color.parseColor("#00E676"), 1.5f
            )
            9 -> ring( // Merah Api
                Color.parseColor("#FF8A00"), Color.parseColor("#FF2E4D"),
                innerBox(intArrayOf(dark(Color.parseColor("#3A0A12")), dark(Color.parseColor("#0F0306"))))
            )
            10 -> box( // Ungu Galaxy
                intArrayOf(dark(Color.parseColor("#2A1566")), dark(Color.parseColor("#0B0620"))),
                ThemeManager.withAlpha(Color.parseColor("#B388FF"), 170)
            )
            11 -> LayerDrawable(arrayOf( // Garis Ganda
                GradientDrawable().apply { cornerRadius = r; setColor(Color.TRANSPARENT); setStroke((2f * d).toInt().coerceAtLeast(2), a1) },
                box(intArrayOf(dark(Color.BLACK), dark(Color.BLACK)), Color.argb(80, 255, 255, 255), 1f, (r - 5f * d).coerceAtLeast(0f))
            )).apply { val w = (5f * d).toInt(); setLayerInset(1, w, w, w, w) }
            12 -> CutCornerDrawable(dark(Color.BLACK), a1, (maxOf(radiusDp, 10) * d), 2f * d) // Sudut Potong
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

/** Kotak dengan dua sudut dipotong miring (kiri-atas & kanan-bawah), ala UI gamer. */
class CutCornerDrawable(
    private val fill: Int, private val stroke: Int, private val cut: Float, private val strokePx: Float
) : Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        val pad = strokePx / 2f
        val l = b.left + pad; val t = b.top + pad; val r = b.right - pad; val bt = b.bottom - pad
        val c = minOf(cut, (r - l) / 3f, (bt - t) / 3f)
        val path = android.graphics.Path().apply {
            moveTo(l + c, t); lineTo(r, t); lineTo(r, bt - c); lineTo(r - c, bt); lineTo(l, bt); lineTo(l, t + c); close()
        }
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = fill
        canvas.drawPath(path, paint)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = strokePx
        paint.strokeJoin = android.graphics.Paint.Join.MITER
        paint.color = stroke
        canvas.drawPath(path, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
