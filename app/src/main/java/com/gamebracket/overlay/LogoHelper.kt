package com.gamebracket.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/** Logo tim: simpan dari galeri, dan buat gambar bulat (logo asli atau inisial). */
object LogoHelper {

    private val cache = HashMap<String, Bitmap>()

    private val palette = listOf(
        "#00E5FF" to "#2979FF",
        "#FF2E88" to "#7C4DFF",
        "#FFC83D" to "#FF7A00",
        "#00E5A0" to "#00A3FF",
        "#7C4DFF" to "#00E5FF",
        "#FF4D6D" to "#FF9A3D"
    )

    /** Salin gambar pilihan pengguna ke penyimpanan app (kotak 256px). Mengembalikan path file. */
    fun saveFromUri(ctx: Context, uri: Uri): String? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val side = min(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(square, 256, 256, true)
            val dir = File(ctx.filesDir, "logos").apply { mkdirs() }
            val f = File(dir, "logo_${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return f.absolutePath
        } catch (e: Exception) {
            return null
        }
    }

    /** Simpan gambar latar sertifikat (sisi terpanjang maks 1600px, JPEG). */
    fun saveBackground(ctx: Context, uri: Uri): String? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val dir = File(ctx.filesDir, "cert_bg").apply { mkdirs() }
            val f = File(dir, "bg_${System.currentTimeMillis()}.jpg")
            FileOutputStream(f).use { src.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            return f.absolutePath
        } catch (e: Exception) {
            return null
        }
    }

    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }

    /** Gambar bulat [size]px: logo kalau ada, kalau tidak inisial berwarna. */
    fun circleBitmap(name: String, path: String?, size: Int): Bitmap {
        val key = if (path != null) "$path#$size" else "i:$name#$size"
        cache[key]?.let { return it }

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f

        var drawn = false
        if (path != null) {
            val src = try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null }
            if (src != null) {
                val scaled = Bitmap.createScaledBitmap(src, size, size, true)
                p.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                c.drawCircle(r, r, r, p)
                p.shader = null
                drawn = true
            }
        }
        if (!drawn) {
            val (a, b) = palette[(name.hashCode() and 0x7fffffff) % palette.size]
            p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), Color.parseColor(a), Color.parseColor(b), Shader.TileMode.CLAMP)
            c.drawCircle(r, r, r, p)
            p.shader = null
            p.color = Color.WHITE
            p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            p.textAlign = Paint.Align.CENTER
            p.textSize = size * 0.42f
            val y = r - (p.descent() + p.ascent()) / 2f
            c.drawText(initials(name), r, y, p)
        }
        cache[key] = out
        return out
    }

    fun drawable(ctx: Context, name: String, path: String?, sizePx: Int): Drawable =
        BitmapDrawable(ctx.resources, circleBitmap(name, path, sizePx))
}
