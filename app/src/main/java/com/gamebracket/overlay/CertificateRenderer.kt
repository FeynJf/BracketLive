package com.gamebracket.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat

/** Menggambar sertifikat 1200x850. Semua teks bisa diedit lewat [Data]. */
object CertificateRenderer {

    const val W = 1200
    const val H = 850

    const val THEME_NEON = 0
    const val THEME_CLASSIC = 1

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
        var theme: Int = THEME_NEON
    )

    private class Palette(
        val bg1: Int, val bg2: Int, val acc1: Int, val acc2: Int, val line: Int,
        val title: Int, val text: Int, val name: Int, val gold: Int, val ink: Int, val serif: Boolean
    )

    private val neon = Palette(
        Color.parseColor("#141C4A"), Color.parseColor("#05060F"),
        Color.parseColor("#00E5FF"), Color.parseColor("#FF2E88"), Color.parseColor("#2A3560"),
        Color.WHITE, Color.parseColor("#A9B4D4"), Color.WHITE, Color.parseColor("#FFC83D"),
        Color.parseColor("#E8F1FF"), false
    )
    private val classic = Palette(
        Color.parseColor("#FFFDF5"), Color.parseColor("#F1E7CC"),
        Color.parseColor("#C9A227"), Color.parseColor("#8B6B14"), Color.parseColor("#B89B4A"),
        Color.parseColor("#5C4A1F"), Color.parseColor("#6B5E40"), Color.parseColor("#1A1A1A"),
        Color.parseColor("#A8841A"), Color.parseColor("#0D1B3D"), true
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

    fun render(ctx: Context, d: Data): Bitmap {
        val pal = if (d.theme == THEME_CLASSIC) classic else neon
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = W.toFloat()
        val h = H.toFloat()
        val face = if (pal.serif) Typeface.SERIF else Typeface.SANS_SERIF

        // latar
        p.shader = LinearGradient(0f, 0f, w, h, pal.bg1, pal.bg2, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // motif bracket samar di sudut
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = withAlpha(pal.acc1, 28)
        c.drawLines(floatArrayOf(60f, 650f, 130f, 650f, 130f, 650f, 130f, 720f, 60f, 720f, 130f, 720f, 130f, 685f, 200f, 685f), p)
        c.drawLines(floatArrayOf(w - 60f, 430f, w - 130f, 430f, w - 130f, 430f, w - 130f, 500f, w - 60f, 500f, w - 130f, 500f, w - 130f, 465f, w - 200f, 465f), p)

        // bingkai
        p.strokeWidth = 6f
        p.shader = LinearGradient(0f, 0f, w, h, pal.acc1, pal.acc2, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(24f, 24f, w - 24f, h - 24f), 28f, 28f, p)
        p.shader = null
        p.strokeWidth = 1.5f
        p.color = pal.line
        c.drawRoundRect(RectF(46f, 46f, w - 46f, h - 46f), 18f, 18f, p)
        p.style = Paint.Style.FILL

        // logo penyelenggara (kiri atas) & logo Bracket Live (kanan atas)
        if (!d.orgLogo.isNullOrEmpty()) drawLogoRing(c, LogoHelper.circleBitmap("", d.orgLogo, 192), 140f, 140f, 48f, pal)
        ContextCompat.getDrawable(ctx, R.drawable.ic_logo)?.let {
            it.setBounds(W - 190, 92, W - 94, 188)
            it.draw(c)
        }

        // judul
        text(c, "BRACKET LIVE", 600f, 118f, 20f, pal.acc1, face, true, 0.45f)
        fit(c, d.title.uppercase(), 600f, 205f, 62f, 760f, pal.title, face, true, 0.1f)
        p.shader = LinearGradient(470f, 0f, 730f, 0f, pal.acc1, pal.acc2, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(470f, 228f, 730f, 233f), 3f, 3f, p)
        p.shader = null

        // logo juara
        val champName = d.name.ifBlank { "?" }
        val big = LogoHelper.circleBitmap(champName, d.championLogo, 280)
        drawLogoRing(c, big, 600f, 335f, 72f, pal)

        text(c, "diberikan kepada", 600f, 455f, 22f, pal.text, face, false, 0.08f)
        fit(c, d.name.ifBlank { "—" }, 600f, 535f, 70f, 940f, pal.name, face, true, 0f)
        p.shader = LinearGradient(250f, 0f, 950f, 0f, pal.acc1, pal.acc2, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(250f, 560f, 950f, 563f), 2f, 2f, p)
        p.shader = null

        fit(c, d.ranking.uppercase(), 600f, 620f, 34f, 900f, pal.gold, face, true, 0.15f)
        fit(c, d.event, 600f, 662f, 24f, 900f, pal.text, face, false, 0.03f)

        // blok tanggal (kiri) & tanda tangan (kanan)
        text(c, d.date, 300f, 722f, 28f, pal.name, face, false, 0f)
        line(c, 150f, 735f, 450f, 735f, pal)
        text(c, "Tanggal", 300f, 772f, 18f, pal.text, face, false, 0.1f)

        // tanda tangan: nama diketik → tampil tegak bersambung
        val sig = d.signer.trim()
        if (sig.isNotEmpty()) {
            p.typeface = signatureFace(ctx)
            p.color = pal.ink
            p.textAlign = Paint.Align.CENTER
            var size = 92f
            p.textSize = size
            while (p.measureText(sig) > 330f && size > 30f) { size -= 2f; p.textSize = size }
            c.drawText(sig, 900f, 724f, p)
        }
        line(c, 750f, 735f, 1050f, 735f, pal)
        text(c, sig.ifBlank { "Nama Panitia" }, 900f, 770f, 22f, pal.name, face, true, 0f)
        text(c, d.signerRole, 900f, 796f, 17f, pal.text, face, false, 0.05f)

        text(c, "dibuat dengan Bracket Live", 600f, 792f, 14f, withAlpha(pal.text, 170), face, false, 0.1f)
        return bmp
    }

    private fun withAlpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, pal: Palette) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.strokeWidth = 2f
        p.color = pal.line
        c.drawLine(x1, y1, x2, y2, p)
    }

    private fun drawLogoRing(c: Canvas, bmp: Bitmap, cx: Float, cy: Float, r: Float, pal: Palette) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // cincin neon
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, pal.acc1, pal.acc2, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r + 8f, p)
        p.shader = null
        p.style = Paint.Style.FILL
        val dst = RectF(cx - r, cy - r, cx + r, cy + r)
        c.drawBitmap(bmp, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
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
        while (p.measureText(s) > maxW && sz > 14f) { sz -= 2f; p.textSize = sz }
        p.color = color
        p.textAlign = Paint.Align.CENTER
        c.drawText(s, x, y, p)
    }
}
