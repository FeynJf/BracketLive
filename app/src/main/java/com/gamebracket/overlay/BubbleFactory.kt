package com.gamebracket.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.widget.ImageView
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tombol ikon overlay: 66 desain (11 bentuk × 6 perlakuan) dan 44 gambar ikon (+ foto sendiri).
 * Indeks desain = perlakuan * 11 + bentuk.
 */
object BubbleFactory {

    val shapeNames = listOf("Lingkaran", "Kotak Bulat", "Squircle", "Heksagon", "Oktagon", "Diamond", "Perisai", "Jajar Genjang", "Pentagon", "Sudut Potong", "Lencana")
    val treatNames = listOf("Garis", "Solid", "Kaca", "Gradien", "Ganda", "Pendar")
    val styleNames: List<String> = List(shapeNames.size * treatNames.size) { "${shapeNames[it % shapeNames.size]} ${treatNames[it / shapeNames.size]}" }

    val iconNames = listOf(
        "Logo B",
        "Piala",
        "Pedang",
        "Gamepad",
        "Api",
        "Mahkota",
        "Petir",
        "Target",
        "Foto Sendiri",
        "Perisai",
        "Tengkorak",
        "Bintang",
        "Hati",
        "Permata",
        "Roket",
        "Hantu",
        "Headset",
        "Bom",
        "Medali",
        "Bendera",
        "Pedang Silang",
        "Bidikan",
        "Bulan",
        "Matahari",
        "Salju",
        "Mata",
        "Sekop Kartu",
        "Dadu",
        "Joystick",
        "Daya",
        "Putar",
        "Heksagon",
        "Medis",
        "Jangkar",
        "Kunci",
        "Gembok",
        "Lonceng",
        "Musik",
        "Mikrofon",
        "Kamera",
        "Grafik",
        "Denyut",
        "Tak Hingga",
        "Peringkat",
        "Siaran"
    )
    /** Posisi "Foto Sendiri" di daftar ikon. */
    const val CUSTOM_ICON = 8

    private val iconRes = intArrayOf(
        R.drawable.ic_logo_mark,
        R.drawable.ic_ov_trophy,
        R.drawable.ic_ov_sword,
        R.drawable.ic_ov_pad,
        R.drawable.ic_ov_flame,
        R.drawable.ic_ov_crown,
        R.drawable.ic_ov_bolt,
        R.drawable.ic_ov_target,
        R.drawable.ic_ov_target,
        R.drawable.ic_ov_shield,
        R.drawable.ic_ov_skull,
        R.drawable.ic_ov_star,
        R.drawable.ic_ov_heart,
        R.drawable.ic_ov_gem,
        R.drawable.ic_ov_rocket,
        R.drawable.ic_ov_ghost,
        R.drawable.ic_ov_headset,
        R.drawable.ic_ov_bomb,
        R.drawable.ic_ov_medal,
        R.drawable.ic_ov_flag,
        R.drawable.ic_ov_swords,
        R.drawable.ic_ov_crosshair,
        R.drawable.ic_ov_moon,
        R.drawable.ic_ov_sun,
        R.drawable.ic_ov_snow,
        R.drawable.ic_ov_eye,
        R.drawable.ic_ov_spade,
        R.drawable.ic_ov_dice,
        R.drawable.ic_ov_joystick,
        R.drawable.ic_ov_power,
        R.drawable.ic_ov_play,
        R.drawable.ic_ov_hex,
        R.drawable.ic_ov_medic,
        R.drawable.ic_ov_anchor,
        R.drawable.ic_ov_key,
        R.drawable.ic_ov_lock,
        R.drawable.ic_ov_bell,
        R.drawable.ic_ov_music,
        R.drawable.ic_ov_mic,
        R.drawable.ic_ov_camera,
        R.drawable.ic_ov_chart,
        R.drawable.ic_ov_pulse,
        R.drawable.ic_ov_infinity,
        R.drawable.ic_ov_rank,
        R.drawable.ic_ov_stream
    )

    private fun poly(vararg v: Float) = v
    private val hex = poly(0.5f, 0.02f, 0.95f, 0.26f, 0.95f, 0.74f, 0.5f, 0.98f, 0.05f, 0.74f, 0.05f, 0.26f)
    private val octagon = poly(0.3f, 0.02f, 0.7f, 0.02f, 0.98f, 0.3f, 0.98f, 0.7f, 0.7f, 0.98f, 0.3f, 0.98f, 0.02f, 0.7f, 0.02f, 0.3f)
    private val diamond = poly(0.5f, 0.02f, 0.98f, 0.5f, 0.5f, 0.98f, 0.02f, 0.5f)
    private val shield = poly(0.08f, 0.12f, 0.5f, 0.02f, 0.92f, 0.12f, 0.92f, 0.55f, 0.5f, 0.98f, 0.08f, 0.55f)
    private val skew = poly(0.22f, 0.08f, 0.98f, 0.08f, 0.78f, 0.92f, 0.02f, 0.92f)
    private val pentagon = poly(0.5f, 0.03f, 0.97f, 0.38f, 0.79f, 0.95f, 0.21f, 0.95f, 0.03f, 0.38f)
    private val cut = poly(0.24f, 0.04f, 0.96f, 0.04f, 0.96f, 0.76f, 0.76f, 0.96f, 0.04f, 0.96f, 0.04f, 0.24f)

    private fun seal(): FloatArray {
        val out = FloatArray(48)
        for (i in 0 until 24) {
            val a = Math.PI * 2 * i / 24 - Math.PI / 2
            val r = if (i % 2 == 0) 0.49f else 0.41f
            out[i * 2] = 0.5f + (r * cos(a)).toFloat()
            out[i * 2 + 1] = 0.5f + (r * sin(a)).toFloat()
        }
        return out
    }
    private val sealPts = seal()

    private fun shapePath(shape: Int, r: RectF): Path {
        val p = Path()
        fun polyTo(v: FloatArray) {
            for (i in v.indices step 2) {
                val x = r.left + v[i] * r.width()
                val y = r.top + v[i + 1] * r.height()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
        }
        when (shape) {
            0 -> p.addOval(r, Path.Direction.CW)
            1 -> p.addRoundRect(r, r.width() * 0.28f, r.height() * 0.28f, Path.Direction.CW)
            2 -> { // squircle
                val k = 0.92f
                val cx = r.centerX(); val cy = r.centerY(); val hw = r.width() / 2; val hh = r.height() / 2
                p.moveTo(cx, r.top)
                p.cubicTo(cx + k * hw, r.top, r.right, cy - k * hh, r.right, cy)
                p.cubicTo(r.right, cy + k * hh, cx + k * hw, r.bottom, cx, r.bottom)
                p.cubicTo(cx - k * hw, r.bottom, r.left, cy + k * hh, r.left, cy)
                p.cubicTo(r.left, cy - k * hh, cx - k * hw, r.top, cx, r.top)
                p.close()
            }
            3 -> polyTo(hex)
            4 -> polyTo(octagon)
            5 -> polyTo(diamond)
            6 -> polyTo(shield)
            7 -> polyTo(skew)
            8 -> polyTo(pentagon)
            9 -> polyTo(cut)
            else -> polyTo(sealPts)
        }
        return p
    }

    private class ShapeDrawable(
        private val shape: Int, private val treat: Int,
        private val a1: Int, private val a2: Int, private val dark1: Int, private val dark2: Int, private val d: Float
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(c: Canvas) {
            val b = bounds
            val pad = 3f * d
            val r = RectF(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad)
            val path = shapePath(shape, r)
            val x0 = r.left; val y0 = r.top; val x1 = r.right; val y1 = r.bottom
            paint.shader = null
            paint.strokeJoin = Paint.Join.ROUND
            when (treat) {
                0 -> { // Garis: isi gelap + garis aksen
                    fill(c, path, x0, y0, x1, y1, dark1, dark2)
                    stroke(c, path, a1, 2f * d)
                }
                1 -> { // Solid: isi aksen penuh
                    paint.style = Paint.Style.FILL
                    paint.color = a1
                    c.drawPath(path, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1f * d
                    paint.color = Color.argb(60, 0, 0, 0)
                    c.drawPath(path, paint)
                }
                2 -> { // Kaca: hitam transparan + garis putih tipis
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb(175, 0, 0, 0)
                    c.drawPath(path, paint)
                    stroke(c, path, Color.argb(120, 255, 255, 255), 1.4f * d)
                }
                3 -> { // Gradien aksen
                    paint.style = Paint.Style.FILL
                    paint.shader = LinearGradient(x0, y0, x1, y1, a1, a2, Shader.TileMode.CLAMP)
                    c.drawPath(path, paint)
                    paint.shader = null
                    stroke(c, path, Color.argb(70, 255, 255, 255), 1f * d)
                }
                4 -> { // Ganda: dua garis
                    fill(c, path, x0, y0, x1, y1, dark1, dark2)
                    stroke(c, path, a1, 2f * d)
                    val inner = Path(path)
                    val m = Matrix()
                    m.setScale(0.78f, 0.78f, r.centerX(), r.centerY())
                    inner.transform(m)
                    stroke(c, inner, a2, 1.2f * d)
                }
                else -> { // Pendar: cincin berpendar di luar
                    val glow = Path(path)
                    stroke(c, glow, ThemeManager.withAlpha(a1, 40), 7f * d)
                    stroke(c, glow, ThemeManager.withAlpha(a1, 90), 4f * d)
                    fill(c, path, x0, y0, x1, y1, dark1, dark2)
                    stroke(c, path, a1, 1.5f * d)
                }
            }
        }

        private fun fill(c: Canvas, path: Path, x0: Float, y0: Float, x1: Float, y1: Float, c1: Int, c2: Int) {
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(x0, y0, x1, y1, c1, c2, Shader.TileMode.CLAMP)
            c.drawPath(path, paint)
            paint.shader = null
        }

        private fun stroke(c: Canvas, path: Path, color: Int, w: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w
            paint.color = color
            c.drawPath(path, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** Latar tombol sesuai desain [style] (0..65). [ctx] harus ber-tema. */
    fun background(ctx: Context, style: Int, sizePx: Int): Drawable {
        val d = ctx.resources.displayMetrics.density
        val i = style.coerceIn(0, styleNames.size - 1)
        val a1 = ThemeManager.accent1(ctx)
        val a2 = ThemeManager.accent2(ctx)
        val dark1 = ThemeManager.color(ctx, R.attr.blSurface)
        val dark2 = ThemeManager.color(ctx, R.attr.blBg2)
        return ShapeDrawable(i % shapeNames.size, i / shapeNames.size, a1, a2, dark1, dark2, d)
    }

    /** Pasang gambar ikon ke [img]. "Foto Sendiri" memakai gambar dari galeri. */
    fun applyIcon(ctx: Context, img: ImageView, iconIdx: Int, customPath: String?, sizePx: Int) {
        if (iconIdx == CUSTOM_ICON && customPath != null) {
            img.setImageDrawable(LogoHelper.drawable(ctx, "", customPath, sizePx))
        } else {
            img.setImageResource(iconRes[iconIdx.coerceIn(0, iconRes.size - 1)])
        }
    }
}
