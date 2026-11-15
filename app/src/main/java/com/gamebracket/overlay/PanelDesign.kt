package com.gamebracket.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

/**
 * Latar panel overlay: 35 bentuk/struktur berbeda (sudut potong, tiket, tab, braket HUD, bevel, dll).
 * Isinya hitam transparan (tingkat transparansi dari Pengaturan); warna aksen dipakai untuk detailnya.
 */
object PanelDesign {

    val names = listOf(
        "Bulat", "Tajam", "Pil", "Potong Kiri-Atas", "Potong Kanan-Atas", "Potong Semua", "Sudut Cekung",
        "Tiket", "Tab Atas", "Tab Kanan", "Header Terpisah", "Bar Kiri", "Bar Atas", "Braket HUD",
        "Bingkai Ganda", "Garis Putus", "Bayangan Offset", "Sudut Terlipat", "Panah Kiri", "Gelombang Atas",
        "Gerigi Bawah", "Heksagon Panjang", "Oktagon Besar", "Pixel", "Perisai Atas", "Gelembung Bicara",
        "Bulat Besar", "Asimetris", "Garis Bawah", "Sidebar", "Bevel", "Teknis", "Titik-titik",
        "Outline Tipis", "Frosted"
    )

    fun background(ctx: Context, style: Int, radiusDp: Int, opacity: Int): Drawable =
        PanelShape(
            style.coerceIn(0, names.size - 1), ThemeManager.accent1(ctx), radiusDp,
            opacity, ctx.resources.displayMetrics.density
        )
}

class PanelShape(
    private val kind: Int, private val accent: Int, private val radiusDp: Int,
    private val opacity: Int, private val d: Float
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun fill(c: Canvas, p: Path, color: Int) {
        paint.reset(); paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawPath(p, paint)
    }

    private fun line(c: Canvas, p: Path, color: Int, w: Float, dash: FloatArray? = null, round: Boolean = false) {
        paint.reset(); paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w
        paint.color = color
        paint.strokeJoin = Paint.Join.ROUND
        if (round) paint.strokeCap = Paint.Cap.ROUND
        if (dash != null) paint.pathEffect = DashPathEffect(dash, 0f)
        c.drawPath(p, paint)
    }

    private fun poly(vararg v: Float): Path {
        val p = Path()
        for (i in v.indices step 2) if (i == 0) p.moveTo(v[i], v[i + 1]) else p.lineTo(v[i], v[i + 1])
        p.close()
        return p
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val inset = 0.75f * d
        val l = b.left + inset; val t = b.top + inset; val r = b.right - inset; val bt = b.bottom - inset
        val w = r - l; val h = bt - t
        if (w <= 4f || h <= 4f) return
        val a = opacity.coerceIn(0, 100) * 255 / 100
        val base = Color.argb(a, 0, 0, 0)
        val hair = Color.argb(75, 255, 255, 255)
        val acc = accent
        val rad = max(1f, radiusDp * d)
        val cut = min(max(rad, 12f * d), min(w, h) / 4f)
        val box = RectF(l, t, r, bt)
        val mid = t + h / 2f
        val hair1 = 1.1f * d

        fun rr(rect: RectF, rx: Float) = Path().apply { addRoundRect(rect, rx, rx, Path.Direction.CW) }

        when (kind) {
            0 -> { val p = rr(box, rad); fill(c, p, base); line(c, p, hair, hair1) }
            1 -> { val p = poly(l, t, r, t, r, bt, l, bt); fill(c, p, base); line(c, p, hair, hair1) }
            2 -> { val p = rr(box, min(34f * d, h / 2f)); fill(c, p, base); line(c, p, hair, hair1) }
            3 -> { val p = poly(l + cut, t, r, t, r, bt - cut, r - cut, bt, l, bt, l, t + cut); fill(c, p, base); line(c, p, hair, hair1) }
            4 -> { val p = poly(l, t, r - cut, t, r, t + cut, r, bt, l + cut, bt, l, bt - cut); fill(c, p, base); line(c, p, hair, hair1) }
            5 -> { val p = poly(l + cut, t, r - cut, t, r, t + cut, r, bt - cut, r - cut, bt, l + cut, bt, l, bt - cut, l, t + cut); fill(c, p, base); line(c, p, hair, hair1) }
            6 -> { // sudut cekung
                val k = min(cut, 16f * d)
                val p = Path().apply {
                    moveTo(l + k, t); lineTo(r - k, t)
                    arcTo(RectF(r - k, t - k, r + k, t + k), 180f, -90f)
                    lineTo(r, bt - k)
                    arcTo(RectF(r - k, bt - k, r + k, bt + k), 270f, -90f)
                    lineTo(l + k, bt)
                    arcTo(RectF(l - k, bt - k, l + k, bt + k), 0f, -90f)
                    lineTo(l, t + k)
                    arcTo(RectF(l - k, t - k, l + k, t + k), 90f, -90f)
                    close()
                }
                fill(c, p, base); line(c, p, hair, hair1)
            }
            7 -> { // tiket: lekuk setengah lingkaran di kiri & kanan
                val n = 10f * d
                val p = rr(box, min(rad, 14f * d))
                val notch = Path().apply { addCircle(l, mid, n, Path.Direction.CW); addCircle(r, mid, n, Path.Direction.CW) }
                p.op(notch, Path.Op.DIFFERENCE)
                fill(c, p, base); line(c, p, hair, hair1)
                line(c, Path().apply { moveTo(l + n + 6f * d, mid); lineTo(r - n - 6f * d, mid) }, Color.argb(40, 255, 255, 255), 1f * d, floatArrayOf(4f * d, 5f * d))
            }
            8 -> { // tab di kiri atas
                val tw = w * 0.38f; val th = 14f * d
                val p = poly(l, bt, l, t, l + tw, t, l + tw + th, t + th, r, t + th, r, bt)
                fill(c, p, base); line(c, p, hair, hair1)
            }
            9 -> { // tab di kanan atas
                val tw = w * 0.38f; val th = 14f * d
                val p = poly(r, bt, r, t, r - tw, t, r - tw - th, t + th, l, t + th, l, bt)
                fill(c, p, base); line(c, p, hair, hair1)
            }
            10 -> { // header terpisah dari isi
                val hh = 50f * d; val gap = 4f * d; val rx = min(rad, 12f * d)
                val head = rr(RectF(l, t, r, t + hh), rx)
                val body = rr(RectF(l, t + hh + gap, r, bt), rx)
                fill(c, head, base); line(c, head, hair, hair1)
                fill(c, body, base); line(c, body, hair, hair1)
            }
            11 -> { // bar aksen kiri
                val p = rr(box, min(rad, 6f * d))
                fill(c, p, base)
                c.save(); c.clipPath(p)
                fill(c, poly(l, t, l + 5f * d, t, l + 5f * d, bt, l, bt), acc)
                c.restore()
                line(c, p, hair, hair1)
            }
            12 -> { // bar aksen atas
                val p = rr(box, min(rad, 8f * d))
                fill(c, p, base)
                c.save(); c.clipPath(p)
                fill(c, poly(l, t, r, t, r, t + 3.5f * d, l, t + 3.5f * d), acc)
                c.restore()
                line(c, p, hair, hair1)
            }
            13 -> { // braket HUD: hanya sudut berbentuk L
                fill(c, Path().apply { addRect(box, Path.Direction.CW) }, Color.argb((a * 0.8f).toInt(), 0, 0, 0))
                val k = 20f * d; val o = 1.5f * d
                val p = Path().apply {
                    moveTo(l + o, t + k); lineTo(l + o, t + o); lineTo(l + k, t + o)
                    moveTo(r - k, t + o); lineTo(r - o, t + o); lineTo(r - o, t + k)
                    moveTo(r - o, bt - k); lineTo(r - o, bt - o); lineTo(r - k, bt - o)
                    moveTo(l + k, bt - o); lineTo(l + o, bt - o); lineTo(l + o, bt - k)
                }
                line(c, p, acc, 2f * d)
            }
            14 -> { // bingkai ganda
                val outer = rr(box, rad)
                line(c, outer, acc, 1.5f * d)
                val gap = 5f * d
                val inner = rr(RectF(l + gap, t + gap, r - gap, bt - gap), max(1f, rad - gap))
                fill(c, inner, base); line(c, inner, hair, hair1)
                fill(c, outer.also { }, Color.argb(a / 3, 0, 0, 0))
            }
            15 -> { val p = rr(box, rad); fill(c, p, base); line(c, p, acc, 1.5f * d, floatArrayOf(8f * d, 5f * d)) }
            16 -> { // bayangan offset (gaya brutalis)
                val o = 6f * d
                fill(c, poly(l + o, t + o, r, t + o, r, bt, l + o, bt), ThemeManager.withAlpha(acc, 235))
                val front = poly(l, t, r - o, t, r - o, bt - o, l, bt - o)
                fill(c, front, Color.argb(max(a, 150), 8, 10, 16)); line(c, front, Color.argb(190, 255, 255, 255), 1.4f * d)
            }
            17 -> { // sudut terlipat
                val f = min(22f * d, min(w, h) / 3f)
                val p = poly(l, t, r - f, t, r, t + f, r, bt, l, bt)
                fill(c, p, base); line(c, p, hair, hair1)
                fill(c, poly(r - f, t, r - f, t + f, r, t + f), ThemeManager.withAlpha(acc, 200))
            }
            18 -> { // panah di kiri (seperti label)
                val ar = 16f * d
                val p = poly(l + ar, t, r, t, r, bt, l + ar, bt, l, mid)
                fill(c, p, base); line(c, p, hair, hair1)
            }
            19 -> { // tepi atas bergelombang
                val amp = 5f * d; val per = 44f * d
                val p = Path().apply {
                    moveTo(l, t + amp)
                    var x = l
                    var up = true
                    while (x < r) {
                        val nx = min(x + per / 2f, r)
                        quadTo((x + nx) / 2f, if (up) t - amp * 0.4f else t + amp * 2.4f, nx, t + amp)
                        x = nx; up = !up
                    }
                    lineTo(r, bt - rad); quadTo(r, bt, r - rad, bt)
                    lineTo(l + rad, bt); quadTo(l, bt, l, bt - rad)
                    close()
                }
                fill(c, p, base); line(c, p, hair, hair1)
            }
            20 -> { // tepi bawah bergerigi
                val tooth = 12f * d; val dep = 6f * d
                val p = Path().apply {
                    moveTo(l, t); lineTo(r, t); lineTo(r, bt - dep)
                    var x = r
                    var down = true
                    while (x > l) {
                        val nx = max(x - tooth, l)
                        lineTo((x + nx) / 2f, if (down) bt else bt - dep)
                        lineTo(nx, bt - dep)
                        x = nx; down = true
                    }
                    close()
                }
                fill(c, p, base); line(c, p, hair, hair1)
            }
            21 -> { val k = min(26f * d, w / 5f); val p = poly(l + k, t, r - k, t, r, mid, r - k, bt, l + k, bt, l, mid); fill(c, p, base); line(c, p, hair, hair1) }
            22 -> { val k = min(30f * d, min(w, h) / 3f); val p = poly(l + k, t, r - k, t, r, t + k, r, bt - k, r - k, bt, l + k, bt, l, bt - k, l, t + k); fill(c, p, base); line(c, p, hair, hair1) }
            23 -> { // pixel
                val s = min(4f * d, min(w, h) / 10f)
                val p = poly(l + 2 * s, t, r - 2 * s, t, r - 2 * s, t + s, r - s, t + s, r - s, t + 2 * s, r, t + 2 * s,
                    r, bt - 2 * s, r - s, bt - 2 * s, r - s, bt - s, r - 2 * s, bt - s, r - 2 * s, bt, l + 2 * s, bt,
                    l + 2 * s, bt - s, l + s, bt - s, l + s, bt - 2 * s, l, bt - 2 * s, l, t + 2 * s, l + s, t + 2 * s,
                    l + s, t + s, l + 2 * s, t + s)
                fill(c, p, base); line(c, p, acc, 2f * d)
            }
            24 -> { val pk = 16f * d; val p = poly(l, t + pk, l + w / 2f, t, r, t + pk, r, bt, l, bt); fill(c, p, base); line(c, p, hair, hair1) }
            25 -> { // gelembung bicara dengan ekor di kiri bawah
                val tail = 11f * d
                val body = rr(RectF(l, t, r, bt - tail), rad)
                body.op(Path().apply { moveTo(l + 22f * d, bt - tail - 1f); lineTo(l + 36f * d, bt - tail - 1f); lineTo(l + 20f * d, bt); close() }, Path.Op.UNION)
                fill(c, body, base); line(c, body, hair, hair1)
            }
            26 -> { val p = rr(box, min(34f * d, min(w, h) / 3f)); fill(c, p, base); line(c, p, hair, hair1) }
            27 -> { // sudut tidak simetris
                val big = max(rad * 1.8f, 16f * d); val sm = 3f * d
                val p = Path().apply { addRoundRect(box, floatArrayOf(big, big, sm, sm, big, big, sm, sm), Path.Direction.CW) }
                fill(c, p, base); line(c, p, hair, hair1)
            }
            28 -> { // garis bawah aksen
                val p = rr(box, min(rad, 8f * d))
                fill(c, p, base)
                c.save(); c.clipPath(p)
                fill(c, poly(l, bt - 3.5f * d, r, bt - 3.5f * d, r, bt, l, bt), acc)
                c.restore()
                line(c, p, hair, hair1)
            }
            29 -> { // sidebar terpisah di kiri
                val sw = 9f * d; val gap = 4f * d; val rx = min(rad, 8f * d)
                val side = rr(RectF(l, t, l + sw, bt), rx)
                val body = rr(RectF(l + sw + gap, t, r, bt), rx)
                fill(c, side, ThemeManager.withAlpha(acc, 230))
                fill(c, body, base); line(c, body, hair, hair1)
            }
            30 -> { // bevel timbul
                val p = poly(l, t, r, t, r, bt, l, bt)
                fill(c, p, base)
                val bw = 2.2f * d
                line(c, Path().apply { moveTo(l + bw / 2, bt - bw / 2); lineTo(l + bw / 2, t + bw / 2); lineTo(r - bw / 2, t + bw / 2) }, Color.argb(130, 255, 255, 255), bw)
                line(c, Path().apply { moveTo(l + bw / 2, bt - bw / 2); lineTo(r - bw / 2, bt - bw / 2); lineTo(r - bw / 2, t + bw / 2) }, Color.argb(190, 0, 0, 0), bw)
            }
            31 -> { // teknis: takik di tepi atas + sudut potong
                val k = min(14f * d, min(w, h) / 6f); val nx = w * 0.32f; val nh = 8f * d
                val p = poly(l + k, t, l + nx, t, l + nx + nh, t + nh, r - nx - nh, t + nh, r - nx, t, r - k, t,
                    r, t + k, r, bt - k, r - k, bt, l + k, bt, l, bt - k, l, t + k)
                fill(c, p, base); line(c, p, hair, hair1)
                line(c, Path().apply { moveTo(l + nx + nh, t + nh); lineTo(r - nx - nh, t + nh) }, acc, 2f * d)
            }
            32 -> { val p = rr(box, rad); fill(c, p, base); line(c, p, acc, 2f * d, floatArrayOf(0.1f, 6f * d), round = true) }
            33 -> { val p = rr(box, rad); fill(c, p, Color.argb((a * 0.4f).toInt(), 0, 0, 0)); line(c, p, ThemeManager.withAlpha(acc, 210), 1.2f * d) }
            else -> { // frosted: kaca dengan kilau di atas
                val p = rr(box, min(max(rad, 16f * d), min(w, h) / 3f))
                fill(c, p, base)
                fill(c, p, Color.argb(24, 255, 255, 255))
                line(c, p, Color.argb(90, 255, 255, 255), hair1)
                line(c, Path().apply { moveTo(l + rad, t + 1.6f * d); lineTo(r - rad, t + 1.6f * d) }, Color.argb(120, 255, 255, 255), 1.2f * d)
            }
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
