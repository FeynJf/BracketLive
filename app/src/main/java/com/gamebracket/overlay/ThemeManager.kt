package com.gamebracket.overlay

import android.app.Activity
import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper

/** Tema dasar (warna latar/permukaan) + warna aksen. Ditumpuk di atas Theme.BracketLive. */
object ThemeManager {

    val baseNames = listOf("Neon Navy", "AMOLED Hitam", "Slate Abu", "Ungu Aurora", "Hutan Gelap")
    val accentNames = listOf("Cyan Neon", "Api Merah", "Zamrud", "Emas", "Violet", "Biru Es")

    private val baseStyles = intArrayOf(
        R.style.BL_Base_Navy, R.style.BL_Base_Amoled, R.style.BL_Base_Slate,
        R.style.BL_Base_Aurora, R.style.BL_Base_Forest
    )
    private val accentStyles = intArrayOf(
        R.style.BL_Accent_Cyan, R.style.BL_Accent_Fire, R.style.BL_Accent_Emerald,
        R.style.BL_Accent_Gold, R.style.BL_Accent_Violet, R.style.BL_Accent_Ice
    )

    // Pratinjau di Pengaturan (harus sama dengan themes.xml)
    val basePreview = listOf(
        intArrayOf(0xFF1A2260.toInt(), 0xFF04050C.toInt()),
        intArrayOf(0xFF1C1C22.toInt(), 0xFF000000.toInt()),
        intArrayOf(0xFF2C384F.toInt(), 0xFF0A0D13.toInt()),
        intArrayOf(0xFF3A2380.toInt(), 0xFF07040F.toInt()),
        intArrayOf(0xFF14503F.toInt(), 0xFF030907.toInt())
    )
    val accentPreview = listOf(
        intArrayOf(0xFF00E5FF.toInt(), 0xFFFF2E88.toInt()),
        intArrayOf(0xFFFF8A00.toInt(), 0xFFFF2E4D.toInt()),
        intArrayOf(0xFF00E5A0.toInt(), 0xFF00B3FF.toInt()),
        intArrayOf(0xFFFFC83D.toInt(), 0xFFFF7A00.toInt()),
        intArrayOf(0xFFB388FF.toInt(), 0xFFFF4DA6.toInt()),
        intArrayOf(0xFF4DB8FF.toInt(), 0xFF00E5FF.toInt())
    )

    /** Dipakai untuk mendeteksi perubahan tema (activity lain dibuat ulang). */
    fun key(ctx: Context) = "${Prefs.themeBase(ctx)}-${Prefs.accent(ctx)}"

    private fun overlay(theme: android.content.res.Resources.Theme, ctx: Context) {
        theme.applyStyle(baseStyles[Prefs.themeBase(ctx).coerceIn(0, baseStyles.size - 1)], true)
        theme.applyStyle(accentStyles[Prefs.accent(ctx).coerceIn(0, accentStyles.size - 1)], true)
    }

    /** Panggil di onCreate() sebelum setContentView(). */
    fun apply(activity: Activity) = overlay(activity.theme, activity)

    /** Context bertema untuk Service (overlay), supaya ?attr/... bisa di-resolve. */
    fun themedContext(ctx: Context): Context {
        val c = ContextThemeWrapper(ctx, R.style.Theme_BracketLive)
        overlay(c.theme, ctx)
        return c
    }

    fun color(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    fun accent1(ctx: Context) = color(ctx, R.attr.blAccent1)
    fun accent2(ctx: Context) = color(ctx, R.attr.blAccent2)
    fun accent3(ctx: Context) = color(ctx, R.attr.blAccent3)

    fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
}
