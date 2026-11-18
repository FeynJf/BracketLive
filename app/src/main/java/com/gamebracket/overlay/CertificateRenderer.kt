package com.gamebracket.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Menggambar sertifikat: 100 desain (10 tata letak x 10 bingkai), 20 bentuk, 100 tema warna,
 * font dari katalog, 20 gaya tanda tangan, dan background gambar sendiri.
 */
object CertificateRenderer {

    class Shape(val name: String, val w: Int, val h: Int)

    val shapes = listOf(
        Shape("Landscape A4", 1200, 850), Shape("Portrait A4", 850, 1200), Shape("Persegi 1:1", 1080, 1080),
        Shape("Layar 16:9", 1280, 720), Shape("Story 9:16", 720, 1280), Shape("Layar 4:3", 1200, 900),
        Shape("Potret 3:4", 900, 1200), Shape("Foto 3:2", 1200, 800), Shape("Potret 2:3", 800, 1200),
        Shape("Ultrawide 21:9", 1400, 600), Shape("Lebar 5:4", 1100, 880), Shape("Instagram 4:5", 880, 1100),
        Shape("Letter Landscape", 1100, 850), Shape("Letter Portrait", 850, 1100), Shape("Banner 3:1", 1500, 500),
        Shape("Tinggi 1:2", 600, 1200), Shape("Kartu 8:5", 1200, 750), Shape("Lebar 2:1", 1400, 700),
        Shape("Ponsel 19,5:9", 1170, 540), Shape("Poster 7:10", 840, 1200)
    )

    class Palette(
        val name: String,
        val bg1: Int, val bg2: Int, val acc1: Int, val acc2: Int, val line: Int,
        val title: Int, val text: Int, val nameColor: Int, val gold: Int, val ink: Int,
        val serif: Boolean, val light: Boolean
    )

    private fun c(s: String) = Color.parseColor(s)

    private val baseThemes = listOf(
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

    private val hues = listOf(
        "Merah" to 0, "Jingga" to 14, "Oranye" to 28, "Emas" to 42, "Kuning" to 54, "Lemon" to 66, "Lime" to 80,
        "Hijau Muda" to 100, "Hijau" to 125, "Zamrud" to 150, "Hijau Laut" to 165, "Tosca" to 178, "Cyan" to 190,
        "Langit" to 202, "Biru Muda" to 212, "Biru" to 224, "Safir" to 236, "Indigo" to 248, "Ungu" to 264,
        "Violet" to 276, "Lavender" to 288, "Magenta" to 300, "Fuchsia" to 314, "Pink" to 328, "Mawar" to 342,
        "Marun" to 352, "Karang" to 8, "Cokelat" to 22, "Perunggu" to 36, "Zaitun" to 70, "Teal" to 170
    )

    private fun hsl(h: Int, s: Float, l: Float): Int {
        val hh = ((h % 360) + 360) % 360
        val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun f(t0: Float): Float {
            var t = t0
            if (t < 0) t += 1f
            if (t > 1) t -= 1f
            return when {
                t < 1f / 6 -> p + (q - p) * 6 * t
                t < 1f / 2 -> q
                t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6
                else -> p
            }
        }
        val r = f(hh / 360f + 1f / 3); val g = f(hh / 360f); val b = f(hh / 360f - 1f / 3)
        return Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    /** 100 tema: 7 bernama + 31 warna x 3 suasana (Gelap, Terang, Pastel). */
    val themes: List<Palette> by lazy {
        val out = ArrayList<Palette>(baseThemes)
        hues.forEachIndexed { i, (name, h) ->
            val serif = i % 2 == 0
            out.add(Palette("$name Gelap", hsl(h, .45f, .15f), hsl(h, .5f, .05f), hsl(h, .85f, .62f), hsl(h + 35, .85f, .58f), hsl(h, .35f, .28f),
                Color.WHITE, hsl(h, .25f, .78f), Color.WHITE, hsl(45, .95f, .62f), hsl(h, .3f, .96f), serif, false))
            out.add(Palette("$name Terang", hsl(h, .35f, .97f), hsl(h, .4f, .90f), hsl(h, .7f, .38f), hsl(h + 30, .7f, .30f), hsl(h, .4f, .70f),
                hsl(h, .5f, .18f), hsl(h, .25f, .30f), hsl(h, .4f, .10f), hsl(40, .8f, .38f), hsl(h, .5f, .12f), serif, true))
            out.add(Palette("$name Pastel", hsl(h, .8f, .93f), hsl(h + 20, .7f, .85f), hsl(h, .6f, .48f), hsl(h + 40, .6f, .42f), hsl(h, .5f, .65f),
                hsl(h, .5f, .22f), hsl(h, .3f, .32f), hsl(h, .5f, .14f), hsl(38, .8f, .36f), hsl(h, .5f, .15f), !serif, true))
        }
        out
    }

    val layoutNames = listOf("Klasik Tengah", "Rata Kiri", "Banner Atas", "Sidebar Kiri", "Band Bawah", "Dua Kolom", "Segel Tengah", "Diagonal", "Panel Dalam", "Minimal")
    val borderNames = listOf("Garis Ganda", "Garis Tebal", "Braket Sudut", "Sudut Hias", "Putus-putus", "Tiga Garis", "Titik-titik", "Pita Atas-Bawah", "Bingkai Tajam", "Tanpa Bingkai")
    val designNames: List<String> = List(100) { "${layoutNames[it / 10]} · ${borderNames[it % 10]}" }

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
        var design: Int = 0,
        /** Nama keluarga font sertifikat ("" = bawaan) */
        var font: String = "",
        /** 0..19: gaya tanda tangan (font tulisan tangan dari katalog) */
        var sigStyle: Int = 0,
        var bgImage: String? = null,
        var scrim: Int = 45
    )

    private var bundledSign: Typeface? = null

    private fun signatureFace(ctx: Context, style: Int): Typeface {
        val fam = Fonts.list(ctx, "sign").getOrNull(style)
        if (fam != null) Fonts.cached(ctx, fam)?.let { return it }
        bundledSign?.let { return it }
        val tf = try { Typeface.createFromAsset(ctx.assets, "fonts/Sacramento-Regular.ttf") } catch (e: Exception) { Typeface.create("cursive", Typeface.NORMAL) }
        bundledSign = tf
        return tf
    }

    /** Posisi elemen untuk satu tata letak. */
    private class Plan(w: Float, h: Float, s: Float, val fy: (Float) -> Float) {
        var align = Paint.Align.CENTER
        var tx = w / 2f
        var maxW = w - 260f * s
        var brandY = fy(118f); var titleY = fy(205f)
        var logoCx = w / 2f; var logoCy = fy(335f); var logoR = 72f * s
        var toY = fy(455f); var nameY = fy(535f); var rankY = fy(620f); var eventY = fy(662f)
        var ulX0 = w / 2f - 130f * s; var ulX1 = w / 2f + 130f * s; var ulY = fy(228f)
        var nameLineX0 = w * 0.2f; var nameLineX1 = w * 0.8f; var nameLineY = fy(560f)
        var dateX = w * 0.25f; var sigX = w * 0.75f; var half = 150f * s
        var lineY = fy(735f); var dateY = fy(722f); var labelY = fy(772f); var sigNameY = fy(770f); var roleY = fy(796f)
        var showBrand = true; var showUl = true; var ring = true; var footer = true
        var titleOnBand = false; var sigOnBand = false
    }

    fun render(ctx: Context, d: Data, quality: Float = 1f): Bitmap {
        val pal = themes[d.theme.coerceIn(0, themes.size - 1)]
        val shape = shapes[d.shape.coerceIn(0, shapes.size - 1)]
        val q = quality.coerceIn(0.3f, 1f)
        val W = (shape.w * q).toInt()
        val H = (shape.h * q).toInt()
        val w = W.toFloat()
        val h = H.toFloat()
        val s = min(w / 1200f, h / 850f)
        fun fy(y: Float) = y / 850f * h
        val design = d.design.coerceIn(0, 99)
        val layout = design / 10
        val border = design % 10

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val base = Fonts.cached(ctx, d.font) ?: if (pal.serif) Typeface.SERIF else Typeface.SANS_SERIF

        // ── latar ──
        p.shader = LinearGradient(0f, 0f, w, h, pal.bg1, pal.bg2, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val bgPath = d.bgImage
        if (!bgPath.isNullOrEmpty()) {
            val src = try { BitmapFactory.decodeFile(bgPath) } catch (e: Throwable) { null }
            if (src != null) {
                val scale = maxOf(w / src.width, h / src.height)
                val sw = w / scale; val sh = h / scale
                val left = ((src.width - sw) / 2f).toInt(); val top = ((src.height - sh) / 2f).toInt()
                cv.drawBitmap(src, Rect(left, top, (left + sw).toInt(), (top + sh).toInt()), RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
                val a = d.scrim.coerceIn(0, 85) * 255 / 100
                p.color = if (pal.light) Color.argb(a, 255, 255, 255) else Color.argb(a, 0, 0, 0)
                cv.drawRect(0f, 0f, w, h, p)
            }
        }

        // ── rencana tata letak ──
        val pl = Plan(w, h, s, ::fy)
        val onAcc = if (lum(pal.acc1) > 0.55) Color.parseColor("#101418") else Color.WHITE
        when (layout) {
            1 -> { // rata kiri, logo di kanan
                pl.align = Paint.Align.LEFT; pl.tx = 130f * s; pl.maxW = w * 0.58f - pl.tx
                pl.logoCx = w - 230f * s; pl.logoCy = fy(400f); pl.logoR = 92f * s
                pl.ulX0 = pl.tx; pl.ulX1 = pl.tx + 240f * s
                pl.nameLineX0 = pl.tx; pl.nameLineX1 = w * 0.6f
            }
            2 -> { // banner atas
                pl.titleOnBand = true; pl.titleY = fy(120f); pl.brandY = fy(68f); pl.showUl = false
                pl.logoCy = fy(195f)
            }
            3 -> { // sidebar kiri
                val bar = 230f * s
                pl.tx = bar + (w - bar) / 2f; pl.maxW = w - bar - 120f * s
                pl.logoCx = bar / 2f; pl.logoCy = fy(290f); pl.logoR = 66f * s
                pl.ulX0 = pl.tx - 130f * s; pl.ulX1 = pl.tx + 130f * s
                pl.nameLineX0 = bar + 90f * s; pl.nameLineX1 = w - 90f * s
                pl.dateX = bar + (w - bar) * 0.27f; pl.sigX = bar + (w - bar) * 0.75f; pl.half = 130f * s
                pl.showBrand = false
            }
            4 -> { // band bawah
                pl.sigOnBand = true
                pl.lineY = fy(758f); pl.dateY = fy(742f); pl.labelY = fy(800f); pl.sigNameY = fy(788f); pl.roleY = fy(815f)
                pl.footer = false
            }
            5 -> { // dua kolom
                val col = w * 0.36f
                pl.logoCx = col / 2f; pl.logoCy = fy(290f); pl.logoR = 80f * s
                pl.brandY = fy(120f); pl.titleY = fy(470f); pl.ulX0 = col / 2f - 70f * s; pl.ulX1 = col / 2f + 70f * s; pl.ulY = fy(495f)
                pl.align = Paint.Align.LEFT; pl.tx = col + 70f * s; pl.maxW = w - pl.tx - 90f * s
                pl.toY = fy(300f); pl.nameY = fy(385f); pl.rankY = fy(480f); pl.eventY = fy(525f)
                pl.nameLineX0 = pl.tx; pl.nameLineX1 = w - 90f * s; pl.nameLineY = fy(410f)
                pl.dateX = col + (w - col) * 0.28f; pl.sigX = col + (w - col) * 0.75f; pl.half = 110f * s
                pl.footer = false
            }
            6 -> { // segel tengah
                pl.logoCy = fy(305f); pl.logoR = 96f * s; pl.titleY = fy(185f); pl.brandY = fy(112f)
                pl.ulX0 = w / 2f - 90f * s; pl.ulX1 = w / 2f + 90f * s; pl.ulY = fy(205f)
                pl.toY = fy(470f); pl.nameY = fy(548f)
            }
            7 -> { pl.maxW = w * 0.62f; pl.showBrand = false } // diagonal
            8 -> { pl.maxW = w - 340f * s } // panel dalam
            9 -> { // minimal
                pl.showBrand = false; pl.showUl = false; pl.ring = false; pl.logoR = 54f * s; pl.logoCy = fy(300f)
                pl.titleY = fy(200f); pl.toY = fy(430f); pl.nameY = fy(520f)
            }
        }

        // ── dekorasi tata letak (di bawah teks) ──
        fun accGrad(x0: Float, y0: Float, x1: Float, y1: Float) = LinearGradient(x0, y0, x1, y1, pal.acc1, pal.acc2, Shader.TileMode.CLAMP)
        when (layout) {
            2 -> { p.shader = accGrad(0f, 0f, w, 0f); cv.drawRect(0f, 0f, w, fy(195f), p); p.shader = null }
            3 -> { p.shader = accGrad(0f, 0f, 0f, h); cv.drawRect(0f, 0f, 230f * s, h, p); p.shader = null }
            4 -> { p.color = withAlpha(pal.acc1, 235); cv.drawRect(0f, fy(695f), w, h, p) }
            5 -> {
                p.color = withAlpha(pal.acc1, 40); cv.drawRect(0f, 0f, w * 0.36f, h, p)
                p.color = pal.acc1; cv.drawRect(w * 0.36f - 4f * s, 0f, w * 0.36f, h, p)
            }
            7 -> {
                p.shader = accGrad(0f, 0f, w * 0.4f, h * 0.4f)
                cv.drawPath(Path().apply { moveTo(0f, 0f); lineTo(w * 0.42f, 0f); lineTo(0f, h * 0.42f); close() }, p)
                p.shader = null
                p.color = withAlpha(pal.acc2, 120)
                cv.drawPath(Path().apply { moveTo(w * 0.46f, 0f); lineTo(w * 0.52f, 0f); lineTo(0f, h * 0.52f); lineTo(0f, h * 0.46f); close() }, p)
                p.color = withAlpha(pal.acc1, 90)
                cv.drawPath(Path().apply { moveTo(w, h); lineTo(w * 0.7f, h); lineTo(w, h * 0.7f); close() }, p)
            }
            8 -> {
                val r = RectF(80f * s, fy(90f), w - 80f * s, h - fy(90f))
                p.color = withAlpha(pal.bg1, 205); cv.drawRoundRect(r, 24f * s, 24f * s, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f * s; p.color = pal.line
                cv.drawRoundRect(r, 24f * s, 24f * s, p); p.style = Paint.Style.FILL
            }
        }

        // ── bingkai ──
        drawBorder(cv, border, w, h, s, pal)

        // ── logo ──
        val topY = 140f * s
        if (!d.orgLogo.isNullOrEmpty()) {
            val px = (192 * s).toInt().coerceAtLeast(64)
            val ox = if (layout == 3) 230f * s + 90f * s else 140f * s
            val oy = if (layout == 2 || layout == 7) fy(95f).coerceAtLeast(topY * 0.9f) else topY
            logoRing(cv, LogoHelper.circleBitmap("", d.orgLogo, px), ox, if (layout == 2) fy(95f) else oy, 44f * s, pal, s)
        }
        ContextCompat.getDrawable(ctx, R.drawable.ic_logo)?.let {
            val half = 44f * s
            val cx = w - 140f * s
            val cy = if (layout == 2) fy(95f) else topY
            it.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
            it.draw(cv)
        }
        val champName = d.name.ifBlank { "?" }
        val big = LogoHelper.circleBitmap(champName, d.championLogo, 280)
        if (pl.ring) logoRing(cv, big, pl.logoCx, pl.logoCy, pl.logoR, pal, s)
        else cv.drawBitmap(big, null, RectF(pl.logoCx - pl.logoR, pl.logoCy - pl.logoR, pl.logoCx + pl.logoR, pl.logoCy + pl.logoR), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        if (layout == 6) { // pita di bawah segel
            p.color = withAlpha(pal.acc2, 230)
            val y0 = pl.logoCy + pl.logoR * 0.7f
            cv.drawPath(Path().apply { moveTo(pl.logoCx - 36f * s, y0); lineTo(pl.logoCx - 62f * s, y0 + 70f * s); lineTo(pl.logoCx - 38f * s, y0 + 58f * s); lineTo(pl.logoCx - 22f * s, y0 + 78f * s); close() }, p)
            cv.drawPath(Path().apply { moveTo(pl.logoCx + 36f * s, y0); lineTo(pl.logoCx + 62f * s, y0 + 70f * s); lineTo(pl.logoCx + 38f * s, y0 + 58f * s); lineTo(pl.logoCx + 22f * s, y0 + 78f * s); close() }, p)
        }

        // ── teks ──
        val titleColor = if (pl.titleOnBand) onAcc else pal.title
        val al = pl.align
        if (pl.showBrand) text(cv, "BRACKET LIVE", pl.tx, pl.brandY, 20f * s, if (pl.titleOnBand) onAcc else pal.acc1, base, true, 0.3f, al)
        fit(cv, d.title.uppercase(), pl.tx, pl.titleY, 62f * s, pl.maxW, titleColor, base, true, 0.06f, al)
        if (pl.showUl) {
            p.shader = accGrad(pl.ulX0, 0f, pl.ulX1, 0f)
            cv.drawRoundRect(RectF(pl.ulX0, pl.ulY, pl.ulX1, pl.ulY + 5f * s), 3f, 3f, p)
            p.shader = null
        }
        text(cv, "diberikan kepada", pl.tx, pl.toY, 22f * s, pal.text, base, false, 0.05f, al)
        fit(cv, d.name.ifBlank { "—" }, pl.tx, pl.nameY, 70f * s, pl.maxW, pal.nameColor, base, true, 0f, al)
        if (layout != 9) {
            p.shader = accGrad(pl.nameLineX0, 0f, pl.nameLineX1, 0f)
            cv.drawRoundRect(RectF(pl.nameLineX0, pl.nameLineY, pl.nameLineX1, pl.nameLineY + 3f * s), 2f, 2f, p)
            p.shader = null
        } else {
            p.color = pal.line
            cv.drawRect(w * 0.3f, fy(232f), w * 0.7f, fy(232f) + 2f * s, p)
            cv.drawRect(w * 0.3f, fy(565f), w * 0.7f, fy(565f) + 2f * s, p)
        }
        fit(cv, d.ranking.uppercase(), pl.tx, pl.rankY, 34f * s, pl.maxW, pal.gold, base, true, 0.12f, al)
        fit(cv, d.event, pl.tx, pl.eventY, 24f * s, pl.maxW, pal.text, base, false, 0.02f, al)

        // ── tanggal & tanda tangan ──
        val bandText = if (pl.sigOnBand) onAcc else pal.nameColor
        val bandSub = if (pl.sigOnBand) withAlpha(onAcc, 200) else pal.text
        val lineColor = if (pl.sigOnBand) withAlpha(onAcc, 160) else pal.line
        text(cv, d.date, pl.dateX, pl.dateY, 28f * s, bandText, base, false, 0f, Paint.Align.CENTER)
        line(cv, pl.dateX - pl.half, pl.lineY, pl.dateX + pl.half, pl.lineY, lineColor, s)
        text(cv, "Tanggal", pl.dateX, pl.labelY, 18f * s, bandSub, base, false, 0.05f, Paint.Align.CENTER)

        val sig = d.signer.trim()
        if (sig.isNotEmpty()) {
            p.typeface = signatureFace(ctx, d.sigStyle)
            p.color = if (pl.sigOnBand) onAcc else pal.ink
            p.textAlign = Paint.Align.CENTER
            var size = 92f * s
            p.textSize = size
            while (p.measureText(sig) > pl.half * 2.2f && size > 30f * s) { size -= 2f; p.textSize = size }
            cv.drawText(sig, pl.sigX, pl.dateY + 2f * s, p)
        }
        line(cv, pl.sigX - pl.half, pl.lineY, pl.sigX + pl.half, pl.lineY, lineColor, s)
        fit(cv, sig.ifBlank { "Nama Panitia" }, pl.sigX, pl.sigNameY, 22f * s, pl.half * 2f, bandText, base, true, 0f, Paint.Align.CENTER)
        fit(cv, d.signerRole, pl.sigX, pl.roleY, 17f * s, pl.half * 2f, bandSub, base, false, 0.03f, Paint.Align.CENTER)
        if (pl.footer) text(cv, "dibuat dengan Bracket Live", w / 2f, fy(792f), 14f * s, withAlpha(pal.text, 170), base, false, 0.08f, Paint.Align.CENTER)
        return bmp
    }

    private fun lum(c: Int) = 0.2126 * Color.red(c) / 255 + 0.7152 * Color.green(c) / 255 + 0.0722 * Color.blue(c) / 255

    private fun drawBorder(cv: Canvas, kind: Int, w: Float, h: Float, s: Float, pal: Palette) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        fun grad(sw: Float) { p.strokeWidth = sw; p.shader = LinearGradient(0f, 0f, w, h, pal.acc1, pal.acc2, Shader.TileMode.CLAMP) }
        fun solid(sw: Float, col: Int) { p.shader = null; p.strokeWidth = sw; p.color = col }
        val m = 24f * s
        when (kind) {
            0 -> { grad(6f * s); cv.drawRoundRect(RectF(m, m, w - m, h - m), 28f * s, 28f * s, p); solid(1.5f * s, pal.line); cv.drawRoundRect(RectF(46f * s, 46f * s, w - 46f * s, h - 46f * s), 18f * s, 18f * s, p) }
            1 -> { grad(14f * s); cv.drawRect(RectF(m, m, w - m, h - m), p) }
            2 -> {
                grad(7f * s); p.strokeCap = Paint.Cap.SQUARE
                val k = 90f * s; val o = 34f * s
                val path = Path().apply {
                    moveTo(o, o + k); lineTo(o, o); lineTo(o + k, o)
                    moveTo(w - o - k, o); lineTo(w - o, o); lineTo(w - o, o + k)
                    moveTo(w - o, h - o - k); lineTo(w - o, h - o); lineTo(w - o - k, h - o)
                    moveTo(o + k, h - o); lineTo(o, h - o); lineTo(o, h - o - k)
                }
                cv.drawPath(path, p)
            }
            3 -> {
                solid(2f * s, pal.line); cv.drawRect(RectF(44f * s, 44f * s, w - 44f * s, h - 44f * s), p)
                grad(5f * s)
                val k = 56f * s; val o = 30f * s
                cv.drawPath(Path().apply {
                    moveTo(o, o + k); lineTo(o, o); lineTo(o + k, o)
                    moveTo(w - o - k, o); lineTo(w - o, o); lineTo(w - o, o + k)
                    moveTo(w - o, h - o - k); lineTo(w - o, h - o); lineTo(w - o - k, h - o)
                    moveTo(o + k, h - o); lineTo(o, h - o); lineTo(o, h - o - k)
                }, p)
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(0f, 0f, w, h, pal.acc1, pal.acc2, Shader.TileMode.CLAMP)
                for ((x, y) in listOf(o to o, w - o to o, w - o to h - o, o to h - o)) cv.drawCircle(x, y, 9f * s, p)
            }
            4 -> { grad(4f * s); p.pathEffect = DashPathEffect(floatArrayOf(22f * s, 14f * s), 0f); cv.drawRoundRect(RectF(m, m, w - m, h - m), 16f * s, 16f * s, p) }
            5 -> { solid(2.5f * s, pal.acc1); for (i in 0..2) { val o = (22f + i * 11f) * s; cv.drawRect(RectF(o, o, w - o, h - o), p); if (i == 0) p.color = pal.acc2 else p.color = pal.line } }
            6 -> { grad(5f * s); p.strokeCap = Paint.Cap.ROUND; p.pathEffect = DashPathEffect(floatArrayOf(0.1f, 16f * s), 0f); cv.drawRoundRect(RectF(m, m, w - m, h - m), 14f * s, 14f * s, p) }
            7 -> {
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(0f, 0f, w, 0f, pal.acc1, pal.acc2, Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w, 22f * s, p); cv.drawRect(0f, h - 22f * s, w, h, p)
                p.shader = null; p.color = withAlpha(pal.line, 200)
                cv.drawRect(0f, 30f * s, w, 32f * s, p); cv.drawRect(0f, h - 32f * s, w, h - 30f * s, p)
            }
            8 -> {
                grad(8f * s); val c0 = 30f * s; val o = m
                cv.drawPath(Path().apply {
                    moveTo(o + c0, o); lineTo(w - o - c0, o); lineTo(w - o, o + c0); lineTo(w - o, h - o - c0)
                    lineTo(w - o - c0, h - o); lineTo(o + c0, h - o); lineTo(o, h - o - c0); lineTo(o, o + c0); close()
                }, p)
            }
            else -> { // tanpa bingkai: vinyet halus
                p.style = Paint.Style.FILL
                p.shader = android.graphics.RadialGradient(w / 2f, h / 2f, maxOf(w, h) * 0.75f, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(70, 0, 0, 0)), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w, h, p)
            }
        }
    }

    private fun withAlpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.strokeWidth = 2f * s
        p.color = color
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

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface, bold: Boolean, spacing: Float, align: Paint.Align) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.textSize = size
        p.textAlign = align
        p.typeface = Typeface.create(face, if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.letterSpacing = spacing
        c.drawText(s, x, y, p)
    }

    /** Teks yang otomatis mengecil supaya muat di [maxW]. */
    private fun fit(c: Canvas, s: String, x: Float, y: Float, size: Float, maxW: Float, color: Int, face: Typeface, bold: Boolean, spacing: Float, align: Paint.Align) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = Typeface.create(face, if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.letterSpacing = spacing
        var sz = size
        p.textSize = sz
        while (p.measureText(s) > maxW && sz > 12f) { sz -= 2f; p.textSize = sz }
        p.color = color
        p.textAlign = align
        c.drawText(s, x, y, p)
    }
}
