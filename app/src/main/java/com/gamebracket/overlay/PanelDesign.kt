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
        "Biru Malam", "Emas Premium", "Terminal Hijau", "Merah Api", "Ungu Galaxy", "Garis Ganda", "Sudut Potong",
        "Karbon", "Hologram", "Sunset", "Samudra", "Sakura", "Matrix", "Es Kristal", "Lava", "Neon Tebal", "Pixel Retro"
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
        fun grad3(colors: IntArray, stroke: Int, radius: Float, density: Float) =
            GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                cornerRadius = radius
                setStroke((1.2f * density).toInt().coerceAtLeast(1), ThemeManager.withAlpha(stroke, 150))
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
            13 -> PatternDrawable( // Karbon: serat diagonal
                box(intArrayOf(dark(Color.parseColor("#0B0B0F")), dark(Color.parseColor("#1A1A20"))), Color.argb(70, 255, 255, 255)),
                true, Color.argb(30, 255, 255, 255), 5f * d, r
            )
            14 -> box( // Hologram: kaca berwarna aksen
                intArrayOf(ThemeManager.withAlpha(a1, (a * 0.35f).toInt()), ThemeManager.withAlpha(a2, (a * 0.35f).toInt())),
                ThemeManager.withAlpha(a1, 220), 1.5f
            )
            15 -> grad3( // Sunset
                intArrayOf(dark(Color.parseColor("#FF6A00")), dark(Color.parseColor("#7A1FA2")), dark(Color.parseColor("#1A0B3D"))),
                Color.parseColor("#FFB070"), r, d
            )
            16 -> grad3( // Samudra
                intArrayOf(dark(Color.parseColor("#00A9C7")), dark(Color.parseColor("#02566F")), dark(Color.parseColor("#01131F"))),
                Color.parseColor("#4DD0E1"), r, d
            )
            17 -> grad3( // Sakura
                intArrayOf(dark(Color.parseColor("#B0386F")), dark(Color.parseColor("#4A1233")), dark(Color.parseColor("#14070F"))),
                Color.parseColor("#FF9ACB"), r, d
            )
            18 -> PatternDrawable( // Matrix: garis pindai hijau
                box(intArrayOf(dark(Color.parseColor("#000A03")), dark(Color.parseColor("#000000"))), Color.parseColor("#00FF66"), 1f),
                false, Color.argb(34, 0, 255, 102), 4f * d, r
            )
            19 -> grad3( // Es Kristal
                intArrayOf(ThemeManager.withAlpha(Color.parseColor("#3C7BB0"), (a * 0.85f).toInt()), dark(Color.parseColor("#12304D")), dark(Color.parseColor("#06121F"))),
                Color.parseColor("#BFE9FF"), r, d
            )
            20 -> GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf( // Lava: menyala dari bawah
                dark(Color.parseColor("#120403")), dark(Color.parseColor("#3A0E05")), ThemeManager.withAlpha(Color.parseColor("#B23A00"), a)
            )).apply { cornerRadius = r; setStroke((1.2f * d).toInt().coerceAtLeast(1), Color.parseColor("#FF6A1A")) }
            21 -> ring( // Neon Tebal
                a1, a2,
                innerBox(intArrayOf(Color.argb(a, 0, 0, 0), Color.argb(a, 0, 0, 0)), 3.5f), 3.5f
            )
            22 -> PixelDrawable(dark(Color.parseColor("#0A0A1A")), a1, 4f * d, 2f * d) // Pixel Retro
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

/** Membungkus latar [base] lalu menggambar garis pola (diagonal / mendatar) di atasnya. */
class PatternDrawable(
    private val base: Drawable, private val diagonal: Boolean,
    private val lineColor: Int, private val gap: Float, private val radius: Float
) : Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f }
    private val clip = android.graphics.Path()

    override fun onBoundsChange(bounds: android.graphics.Rect) { base.bounds = bounds }

    override fun draw(canvas: android.graphics.Canvas) {
        base.draw(canvas)
        val b = bounds
        clip.reset()
        clip.addRoundRect(android.graphics.RectF(b), radius, radius, android.graphics.Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        paint.color = lineColor
        if (diagonal) {
            var x = b.left - b.height().toFloat()
            while (x < b.right) {
                canvas.drawLine(x, b.bottom.toFloat(), x + b.height(), b.top.toFloat(), paint)
                x += gap
            }
        } else {
            var y = b.top + gap
            while (y < b.bottom) {
                canvas.drawLine(b.left.toFloat(), y, b.right.toFloat(), y, paint)
                y += gap
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { base.alpha = alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { base.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** Kotak dengan sudut bergerigi seperti piksel (gaya retro). */
class PixelDrawable(
    private val fill: Int, private val stroke: Int, private val step: Float, private val strokePx: Float
) : Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        val l = b.left + strokePx / 2f; val t = b.top + strokePx / 2f
        val r = b.right - strokePx / 2f; val bt = b.bottom - strokePx / 2f
        val s = minOf(step, (r - l) / 8f, (bt - t) / 8f)
        val p = android.graphics.Path().apply {
            moveTo(l + 2 * s, t); lineTo(r - 2 * s, t); lineTo(r - 2 * s, t + s); lineTo(r - s, t + s); lineTo(r - s, t + 2 * s)
            lineTo(r, t + 2 * s); lineTo(r, bt - 2 * s); lineTo(r - s, bt - 2 * s); lineTo(r - s, bt - s); lineTo(r - 2 * s, bt - s)
            lineTo(r - 2 * s, bt); lineTo(l + 2 * s, bt); lineTo(l + 2 * s, bt - s); lineTo(l + s, bt - s); lineTo(l + s, bt - 2 * s)
            lineTo(l, bt - 2 * s); lineTo(l, t + 2 * s); lineTo(l + s, t + 2 * s); lineTo(l + s, t + s); lineTo(l + 2 * s, t + s); close()
        }
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = fill
        canvas.drawPath(p, paint)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = strokePx
        paint.strokeJoin = android.graphics.Paint.Join.MITER
        paint.color = stroke
        canvas.drawPath(p, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
