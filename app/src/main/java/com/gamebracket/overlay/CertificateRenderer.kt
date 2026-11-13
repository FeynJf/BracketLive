package com.gamebracket.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Menggambar sertifikat. Bentuk (landscape/portrait/persegi/16:9), tema warna, dan background
 * gambar sendiri bisa diatur lewat [Data].
 */
object CertificateRenderer {

    class Shape(val name: String, val w: Int, val h: Int)

    val shapes = listOf(
        Shape("Landscape", 1200, 850),
        Shape("Portrait", 850, 1200),
        Shape("Persegi", 1080, 1080),
        Shape("Layar 16:9", 1280, 720)
    )

    class Palette(
        val name: String,
        val bg1: Int, val bg2: Int, val acc1: Int, val acc2: Int, val line: Int,
        val title: Int, val text: Int, val nameColor: Int, val gold: Int, val ink: Int,
        val serif: Boolean, val light: Boolean
    )

    private fun c(s: String) = Color.parseColor(s)

    val themes = listOf(
        Palette("Neon Gamer", c("#141C4A"), c("#05060F"), c("#00E5FF"), c("#FF2E88"), c("#2A3560"),
            Color.WHITE, c("#A9B4D4"), Color.WHITE, c("#FFC83D"), c("#E8F1FF"), false, false),
        Palette("Klasik Emas", c("#FFFDF5"), c("#F1E7CC"), c("#C9A227"), c("#8B6B14"), c("#B89B4A"),
            c("#5C4A1F"), c("#6B5E40"), c("#1A1A1A"), c("#A8841A"), c("#0D1B3D"), true, true),
        Palette("Royal Ungu", c("#2A1566"), c("#0B0620"), c("#B388FF"), c("#FF4DA6"), c("#4A3290"),
            Color.WHITE, c("#C9BCF2"), Color.WHITE, c("#FFD76A"), c("#F3ECFF"), true, false),
        Palette("Api Crimson", c("#3A0A12"), c("#0F0306"), c("#FF8A00"), c("#FF2E4D"), c("#6A1A26"),
            Color.WHITE, c("#F0C0C6"), Color.WHITE, c("#FFC83D"), c("#FFF1E6"), false, false),
        Palette("Zamrud", c("#0B3A2D"), c("#03120D"), c("#00E5A0"), c("#00B3FF"), c("#1D6B55"),
            Color.WHITE, c("#B5E8D6"), Color.WHITE, c("#FFE27A"), c("#E6FFF5"), false, false),
        Palette("Carbon Gold", c("#1A1A1A"), c("#000000"), c("#FFC83D"), c("#C68A00"), c("#4A3D18"),
            c("#FFE9A8"), c("#BDB6A3"), Color.WHITE, c("#FFC83D"), c("#FFF3CC"), true, false),
        Palette("Putih Bersih", c("#FFFFFF"), c("#EEF3FB"), c("#2979FF"), c("#00B8D4"), c("#C9D6EE"),
            c("#10224A"), c("#51607F"), c("#0B1530"), c("#E08A00"), c("#0A1A44"), false, true)
    )

    class Data(
        var title: String = "SERTIFIKAT JUARA",
        var name: String = "",
        var ranking: String = "JUARA 1",
        var event: String = "",
        var date: String = "",
        var signer: String = "",
        var signerRole: String = "Ketua Panitia",
        var championLogo: String? = null,
        var orgLogo: String? = null,
        var theme: Int = 0,
        var shape: Int = 0,
        var bgImage: String? = null,
        /** 0..85: seberapa gelap/terang lapisan di atas gambar latar supaya teks tetap terbaca */
        var scrim: Int = 45
    )

    private var signFace: Typeface? = null

    /** Font tanda tangan tegak bersambung (Sacramento). Cadangan: font cursive bawaan Android. */
    private fun signatureFace(ctx: Context): Typeface {
        signFace?.let { return it }
        val tf = try {
            Typeface.createFromAsset(ctx.assets, "fonts/Sacramento-Regular.ttf")
        } catch (e: Exception) {
            Typeface.create("cursive", Typeface.NORMAL)
        }
        signFace = tf
        return tf
    }

    /** [quality] 0.3..1: skala ukuran bitmap (dipakai untuk pratinjau ringan / cadangan saat memori sempit). */
    fun render(ctx: Context, d: Data, quality: Float = 1f): Bitmap {
        val pal = themes[d.theme.coerceIn(0, themes.size - 1)]
        val shape = shapes[d.shape.coerceIn(0, shapes.size - 1)]
        val q = quality.coerceIn(0.3f, 1f)
        val W = (shape.w * q).toInt()
        val H = (shape.h * q).toInt()
        val w = W.toFloat()
        val h = H.toFloat()
        // desain dasar 1200x850: ukuran mengikuti skala s, posisi vertikal mengikuti tinggi kanvas
        val s = min(w / 1200f, h / 850f)
        fun fy(y: Float) = y / 850f * h

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val face = if (pal.serif) Typeface.SERIF else Typeface.SANS_SERIF

        // latar warna
        p.shader = LinearGradient(0f, 0f, w, h, pal.bg1, pal.bg2, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // gambar latar sendiri (center-crop) + lapisan peredam
        val bgPath = d.bgImage
        if (!bgPath.isNullOrEmpty()) {
            val src = try { BitmapFactory.decodeFile(bgPath) } catch (e: Exception) { null }
            if (src != null) {
                val scale = maxOf(w / src.width, h / src.height)
                val sw = w / scale
                val sh = h / scale
                val left = ((src.width - sw) / 2f).toInt()
                val top = ((src.height - sh) / 2f).toInt()
                cv.drawBitmap(src, Rect(left, top, (left + sw).toInt(), (top + sh).toInt()), RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
                val a = (d.scrim.coerceIn(0, 85) * 255 / 100)
                p.color = if (pal.light) Color.argb(a, 255, 255, 255) else Color.argb(a, 0, 0, 0)
                cv.drawRect(0f, 0f, w, h, p)
            }
        }

        // motif bracket samar
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * s
        p.color = withAlpha(pal.acc1, 30)
        val m = 60f * s
        cv.drawLines(floatArrayOf(m, h - 200f * s, m + 70f * s, h - 200f * s, m + 70f * s, h - 200f * s, m + 70f * s, h - 130f * s,
            m, h - 130f * s, m + 70f * s, h - 130f * s, m + 70f * s, h - 165f * s, m + 140f * s, h - 165f * s), p)
        cv.drawLines(floatArrayOf(w - m, fy(430f), w - m - 70f * s, fy(430f), w - m - 70f * s, fy(430f), w - m - 70f * s, fy(500f),
            w - m, fy(500f), w - m - 70f * s, fy(500f), w - m - 70f * s, fy(465f), w - m - 140f * s, fy(465f)), p)

        // bingkai
        p.strokeWidth = 6f * s
        p.shader = LinearGradient(0f, 0f, w, h, pal.acc1, pal.acc2, Shader.TileMode.CLAMP)
        cv.drawRoundRect(RectF(24f * s, 24f * s, w - 24f * s, h - 24f * s), 28f * s, 28f * s, p)
        p.shader = null
        p.strokeWidth = 1.5f * s
        p.color = pal.line
        cv.drawRoundRect(RectF(46f * s, 46f * s, w - 46f * s, h - 46f * s), 18f * s, 18f * s, p)
        p.style = Paint.Style.FILL

        // logo penyelenggara (kiri atas) & logo Bracket Live (kanan atas)
        val topY = 140f * s
        if (!d.orgLogo.isNullOrEmpty()) {
            val px = (192 * s).toInt().coerceAtLeast(64)
            logoRing(cv, LogoHelper.circleBitmap("", d.orgLogo, px), 140f * s, topY, 48f * s, pal, s)
        }
        ContextCompat.getDrawable(ctx, R.drawable.ic_logo)?.let {
            val half = 48f * s
            it.setBounds((w - 142f * s - half).toInt(), (topY - half).toInt(), (w - 142f * s + half).toInt(), (topY + half).toInt())
            it.draw(cv)
        }

        // judul
        text(cv, "BRACKET LIVE", w / 2, fy(118f), 20f * s, pal.acc1, face, true, 0.45f)
        fit(cv, d.title.uppercase(), w / 2, fy(205f), 62f * s, 760f * s, pal.title, face, true, 0.1f)
        p.shader = LinearGradient(w / 2 - 130f * s, 0f, w / 2 + 130f * s, 0f, pal.acc1, pal.acc2, Shader.TileMode.CLAMP)
        cv.drawRoundRect(RectF(w / 2 - 130f * s, fy(228f), w / 2 + 130f * s, fy(228f) + 5f * s), 3f, 3f, p)
        p.shader = null

        // logo juara
        val champName = d.name.ifBlank { "?" }
        val big = LogoHelper.circleBitmap(champName, d.championLogo, 280)
        logoRing(cv, big, w / 2, fy(335f), 72f * s, pal, s)

        text(cv, "diberikan kepada", w / 2, fy(455f), 22f * s, pal.text, face, false, 0.08f)
        fit(cv, d.name.ifBlank { "—" }, w / 2, fy(535f), 70f * s, w - 260f * s, pal.nameColor, face, true, 0f)
        p.shader = LinearGradient(w * 0.2f, 0f, w * 0.8f, 0f, pal.acc1, pal.acc2, Shader.TileMode.CLAMP)
        cv.drawRoundRect(RectF(w * 0.2f, fy(560f), w * 0.8f, fy(560f) + 3f * s), 2f, 2f, p)
        p.shader = null

        fit(cv, d.ranking.uppercase(), w / 2, fy(620f), 34f * s, w - 300f * s, pal.gold, face, true, 0.15f)
        fit(cv, d.event, w / 2, fy(662f), 24f * s, w - 300f * s, pal.text, face, false, 0.03f)

        // tanggal (kiri) & tanda tangan (kanan)
        val lx = w * 0.25f
        val rx = w * 0.75f
        val half = 150f * s
        text(cv, d.date, lx, fy(722f), 28f * s, pal.nameColor, face, false, 0f)
        line(cv, lx - half, fy(735f), lx + half, fy(735f), pal, s)
        text(cv, "Tanggal", lx, fy(772f), 18f * s, pal.text, face, false, 0.1f)

        // tanda tangan: nama diketik → tampil tegak bersambung
        val sig = d.signer.trim()
        if (sig.isNotEmpty()) {
            p.typeface = signatureFace(ctx)
            p.color = pal.ink
            p.textAlign = Paint.Align.CENTER
            var size = 92f * s
            p.textSize = size
            while (p.measureText(sig) > half * 2.2f && size > 30f * s) { size -= 2f; p.textSize = size }
            cv.drawText(sig, rx, fy(724f), p)
        }
        line(cv, rx - half, fy(735f), rx + half, fy(735f), pal, s)
        fit(cv, sig.ifBlank { "Nama Panitia" }, rx, fy(770f), 22f * s, half * 2f, pal.nameColor, face, true, 0f)
        fit(cv, d.signerRole, rx, fy(796f), 17f * s, half * 2f, pal.text, face, false, 0.05f)

        text(cv, "dibuat dengan Bracket Live", w / 2, fy(792f), 14f * s, withAlpha(pal.text, 170), face, false, 0.1f)
        return bmp
    }

    private fun withAlpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, pal: Palette, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.strokeWidth = 2f * s
        p.color = pal.line
        c.drawLine(x1, y1, x2, y2, p)
    }

    private fun logoRing(c: Canvas, bmp: Bitmap, cx: Float, cy: Float, r: Float, pal: Palette, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f * s
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, pal.acc1, pal.acc2, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r + 8f * s, p)
        p.shader = null
        c.drawBitmap(bmp, null, RectF(cx - r, cy - r, cx + r, cy + r), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface, bold: Boolean, spacing: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.textSize = size
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(face, if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.letterSpacing = spacing
        c.drawText(s, x, y, p)
    }

    /** Teks yang otomatis mengecil supaya muat di [maxW]. */
    private fun fit(c: Canvas, s: String, x: Float, y: Float, size: Float, maxW: Float, color: Int, face: Typeface, bold: Boolean, spacing: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = Typeface.create(face, if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.letterSpacing = spacing
        var sz = size
        p.textSize = sz
        while (p.measureText(s) > maxW && sz > 12f) { sz -= 2f; p.textSize = sz }
        p.color = color
        p.textAlign = Paint.Align.CENTER
        c.drawText(s, x, y, p)
    }
}
