package com.gamebracket.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.ImageView

/** Desain tombol ikon overlay (bentuk) dan pilihan gambar ikon. */
object BubbleFactory {

    val styleNames = listOf("Neon Ring", "Hexagon", "Kotak Bulat", "Kaca", "Solid", "Diamond", "Perisai")
    val iconNames = listOf("Logo B", "Piala", "Pedang", "Gamepad", "Api", "Mahkota", "Petir", "Target", "Foto Sendiri")
    const val CUSTOM_ICON = 8

    private val iconRes = intArrayOf(
        R.drawable.ic_logo_mark, R.drawable.ic_ov_trophy, R.drawable.ic_ov_sword, R.drawable.ic_ov_pad,
        R.drawable.ic_ov_flame, R.drawable.ic_ov_crown, R.drawable.ic_ov_bolt, R.drawable.ic_ov_target
    )

    private val hex = floatArrayOf(0.5f, 0.02f, 0.95f, 0.26f, 0.95f, 0.74f, 0.5f, 0.98f, 0.05f, 0.74f, 0.05f, 0.26f)
    private val diamond = floatArrayOf(0.5f, 0.02f, 0.98f, 0.5f, 0.5f, 0.98f, 0.02f, 0.5f)
    private val shield = floatArrayOf(0.08f, 0.12f, 0.5f, 0.02f, 0.92f, 0.12f, 0.92f, 0.55f, 0.5f, 0.98f, 0.08f, 0.55f)

    /** Polygon dengan isi gelap + garis tepi gradien neon. */
    private class PolyDrawable(
        private val pts: FloatArray, private val a1: Int, private val a2: Int,
        private val fill1: Int, private val fill2: Int, private val strokePx: Float
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val pad = strokePx / 2f
            val path = Path()
            for (i in pts.indices step 2) {
                val x = b.left + pad + pts[i] * (w - 2 * pad)
                val y = b.top + pad + pts[i + 1] * (h - 2 * pad)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), fill1, fill2, Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokePx
            paint.strokeJoin = Paint.Join.ROUND
            paint.shader = LinearGradient(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), a1, a2, Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** Latar tombol sesuai desain [style]. [ctx] harus ber-tema (untuk warna permukaan). */
    fun background(ctx: Context, style: Int, sizePx: Int): Drawable {
        val d = ctx.resources.displayMetrics.density
        val a1 = ThemeManager.accent1(ctx)
        val a2 = ThemeManager.accent2(ctx)
        val dark1 = ThemeManager.color(ctx, R.attr.blSurface)
        val dark2 = ThemeManager.color(ctx, R.attr.blBg2)
        val ring = (2f * d).toInt().coerceAtLeast(2)

        fun grad(shape: Int, c1: Int, c2: Int, radius: Float = 0f) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).apply {
            this.shape = shape
            if (shape == GradientDrawable.RECTANGLE) cornerRadius = radius
        }

        return when (style) {
            1 -> PolyDrawable(hex, a1, a2, dark1, dark2, 2.2f * d)
            5 -> PolyDrawable(diamond, a1, a2, dark1, dark2, 2.2f * d)
            6 -> PolyDrawable(shield, a1, a2, dark1, dark2, 2.2f * d)
            2 -> {
                val r = sizePx * 0.3f
                LayerDrawable(arrayOf(grad(GradientDrawable.RECTANGLE, a1, a2, r), grad(GradientDrawable.RECTANGLE, dark1, dark2, r - ring))).apply {
                    setLayerInset(1, ring, ring, ring, ring)
                }
            }
            3 -> GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(170, 0, 0, 0))
                setStroke((1.5f * d).toInt().coerceAtLeast(1), Color.argb(110, 255, 255, 255))
            }
            4 -> grad(GradientDrawable.OVAL, a1, a2)
            else -> LayerDrawable(arrayOf(grad(GradientDrawable.OVAL, a1, a2), grad(GradientDrawable.OVAL, dark1, dark2))).apply {
                setLayerInset(1, ring, ring, ring, ring)
            }
        }
    }

    /** Pasang gambar ikon ke [img]. Ikon "Foto Sendiri" memakai gambar dari galeri. */
    fun applyIcon(ctx: Context, img: ImageView, iconIdx: Int, customPath: String?, sizePx: Int) {
        if (iconIdx == CUSTOM_ICON && customPath != null) {
            img.setImageDrawable(LogoHelper.drawable(ctx, "", customPath, sizePx))
        } else {
            img.setImageResource(iconRes[iconIdx.coerceIn(0, iconRes.size - 1)])
        }
    }
}
