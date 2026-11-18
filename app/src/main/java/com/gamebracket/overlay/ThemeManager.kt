package com.gamebracket.overlay

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.ContextThemeWrapper

/**
 * Tema = 25 pilihan warna dasar (latar & permukaan, lewat style XML) + 1 warna aksen bebas
 * (16,7 juta warna, disimpan sebagai angka warna). Warna turunan aksen dihitung otomatis.
 */
object ThemeManager {

    val baseNames = listOf(
        "Midnight Navy",
        "AMOLED Hitam",
        "Slate Abu",
        "Ungu Aurora",
        "Hutan Gelap",
        "Karbon",
        "Arang Hangat",
        "Biru Baja",
        "Samudra Dalam",
        "Merah Marun",
        "Espresso",
        "Zaitun Gelap",
        "Teal Malam",
        "Indigo Royal",
        "Plum",
        "Grafit Biru",
        "Batu Bara",
        "Senja Ungu",
        "Terminal",
        "Mocha",
        "Es Gelap",
        "Mawar Gelap",
        "Bara",
        "Safir",
        "Lumut"
    )
    private val baseStyles = intArrayOf(
        R.style.BL_Base_T01,
        R.style.BL_Base_T02,
        R.style.BL_Base_T03,
        R.style.BL_Base_T04,
        R.style.BL_Base_T05,
        R.style.BL_Base_T06,
        R.style.BL_Base_T07,
        R.style.BL_Base_T08,
        R.style.BL_Base_T09,
        R.style.BL_Base_T10,
        R.style.BL_Base_T11,
        R.style.BL_Base_T12,
        R.style.BL_Base_T13,
        R.style.BL_Base_T14,
        R.style.BL_Base_T15,
        R.style.BL_Base_T16,
        R.style.BL_Base_T17,
        R.style.BL_Base_T18,
        R.style.BL_Base_T19,
        R.style.BL_Base_T20,
        R.style.BL_Base_T21,
        R.style.BL_Base_T22,
        R.style.BL_Base_T23,
        R.style.BL_Base_T24,
        R.style.BL_Base_T25
    )
    /** Pratinjau: [header, latar, kartu] tiap tema (harus sama dengan themes.xml). */
    val basePreview = listOf(
        intArrayOf(0xFF0C122A.toInt(), 0xFF050710.toInt(), 0xFF0D142D.toInt()),
        intArrayOf(0xFF0A0A0A.toInt(), 0xFF000000.toInt(), 0xFF0C0C0C.toInt()),
        intArrayOf(0xFF161A20.toInt(), 0xFF080A0C.toInt(), 0xFF181C23.toInt()),
        intArrayOf(0xFF170D28.toInt(), 0xFF09050F.toInt(), 0xFF190F2C.toInt()),
        intArrayOf(0xFF10251E.toInt(), 0xFF060E0C.toInt(), 0xFF122921.toInt()),
        intArrayOf(0xFF191A1C.toInt(), 0xFF0A0A0B.toInt(), 0xFF1C1D1F.toInt()),
        intArrayOf(0xFF1D1A18.toInt(), 0xFF0B0A09.toInt(), 0xFF201D1A.toInt()),
        intArrayOf(0xFF111C24.toInt(), 0xFF070B0E.toInt(), 0xFF131F28.toInt()),
        intArrayOf(0xFF0C252A.toInt(), 0xFF050E10.toInt(), 0xFF0D282D.toInt()),
        intArrayOf(0xFF270F13.toInt(), 0xFF0F0607.toInt(), 0xFF2B1015.toInt()),
        intArrayOf(0xFF241811.toInt(), 0xFF0E0907.toInt(), 0xFF281B13.toInt()),
        intArrayOf(0xFF1F2313.toInt(), 0xFF0C0D07.toInt(), 0xFF222615.toInt()),
        intArrayOf(0xFF0F2725.toInt(), 0xFF060F0E.toInt(), 0xFF102B28.toInt()),
        intArrayOf(0xFF0D0D28.toInt(), 0xFF05050F.toInt(), 0xFF0F0F2C.toInt()),
        intArrayOf(0xFF241124.toInt(), 0xFF0E070E.toInt(), 0xFF281328.toInt()),
        intArrayOf(0xFF17181F.toInt(), 0xFF09090C.toInt(), 0xFF191A22.toInt()),
        intArrayOf(0xFF1C1A1A.toInt(), 0xFF0B0A0A.toInt(), 0xFF1E1C1C.toInt()),
        intArrayOf(0xFF1E1025.toInt(), 0xFF0C060E.toInt(), 0xFF211229.toInt()),
        intArrayOf(0xFF112418.toInt(), 0xFF070E09.toInt(), 0xFF13281A.toInt()),
        intArrayOf(0xFF201B16.toInt(), 0xFF0C0A08.toInt(), 0xFF231D18.toInt()),
        intArrayOf(0xFF141D21.toInt(), 0xFF080B0D.toInt(), 0xFF162025.toInt()),
        intArrayOf(0xFF25111B.toInt(), 0xFF0E060A.toInt(), 0xFF28121D.toInt()),
        intArrayOf(0xFF27150F.toInt(), 0xFF0F0806.toInt(), 0xFF2B1710.toInt()),
        intArrayOf(0xFF09182C.toInt(), 0xFF040911.toInt(), 0xFF0A1A30.toInt()),
        intArrayOf(0xFF182213.toInt(), 0xFF090D07.toInt(), 0xFF1B2615.toInt()),
    )

    val uiDesignNames: List<String> = listOf(
        "Outline · Tajam · Ikut Kartu",
        "Outline · Tajam · Pil",
        "Outline · Tajam · Kotak",
        "Outline · Tajam · Kecil",
        "Outline · Halus · Ikut Kartu",
        "Outline · Halus · Pil",
        "Outline · Halus · Kotak",
        "Outline · Halus · Kecil",
        "Outline · Bulat · Ikut Kartu",
        "Outline · Bulat · Pil",
        "Outline · Bulat · Kotak",
        "Outline · Bulat · Kecil",
        "Outline · Lembut · Ikut Kartu",
        "Outline · Lembut · Pil",
        "Outline · Lembut · Kotak",
        "Outline · Lembut · Kecil",
        "Outline · Besar · Ikut Kartu",
        "Outline · Besar · Pil",
        "Outline · Besar · Kotak",
        "Outline · Besar · Kecil",
        "Datar · Tajam · Ikut Kartu",
        "Datar · Tajam · Pil",
        "Datar · Tajam · Kotak",
        "Datar · Tajam · Kecil",
        "Datar · Halus · Ikut Kartu",
        "Datar · Halus · Pil",
        "Datar · Halus · Kotak",
        "Datar · Halus · Kecil",
        "Datar · Bulat · Ikut Kartu",
        "Datar · Bulat · Pil",
        "Datar · Bulat · Kotak",
        "Datar · Bulat · Kecil",
        "Datar · Lembut · Ikut Kartu",
        "Datar · Lembut · Pil",
        "Datar · Lembut · Kotak",
        "Datar · Lembut · Kecil",
        "Datar · Besar · Ikut Kartu",
        "Datar · Besar · Pil",
        "Datar · Besar · Kotak",
        "Datar · Besar · Kecil",
        "Kaca · Tajam · Ikut Kartu",
        "Kaca · Tajam · Pil",
        "Kaca · Tajam · Kotak",
        "Kaca · Tajam · Kecil",
        "Kaca · Halus · Ikut Kartu",
        "Kaca · Halus · Pil",
        "Kaca · Halus · Kotak",
        "Kaca · Halus · Kecil",
        "Kaca · Bulat · Ikut Kartu",
        "Kaca · Bulat · Pil",
        "Kaca · Bulat · Kotak",
        "Kaca · Bulat · Kecil",
        "Kaca · Lembut · Ikut Kartu",
        "Kaca · Lembut · Pil",
        "Kaca · Lembut · Kotak",
        "Kaca · Lembut · Kecil",
        "Kaca · Besar · Ikut Kartu",
        "Kaca · Besar · Pil",
        "Kaca · Besar · Kotak",
        "Kaca · Besar · Kecil",
        "Cekung · Tajam · Ikut Kartu",
        "Cekung · Tajam · Pil",
        "Cekung · Tajam · Kotak",
        "Cekung · Tajam · Kecil",
        "Cekung · Halus · Ikut Kartu",
        "Cekung · Halus · Pil",
        "Cekung · Halus · Kotak",
        "Cekung · Halus · Kecil",
        "Cekung · Bulat · Ikut Kartu",
        "Cekung · Bulat · Pil",
        "Cekung · Bulat · Kotak",
        "Cekung · Bulat · Kecil",
        "Cekung · Lembut · Ikut Kartu",
        "Cekung · Lembut · Pil",
        "Cekung · Lembut · Kotak",
        "Cekung · Lembut · Kecil",
        "Cekung · Besar · Ikut Kartu",
        "Cekung · Besar · Pil",
        "Cekung · Besar · Kotak",
        "Cekung · Besar · Kecil",
        "Tebal · Tajam · Ikut Kartu",
        "Tebal · Tajam · Pil",
        "Tebal · Tajam · Kotak",
        "Tebal · Tajam · Kecil",
        "Tebal · Halus · Ikut Kartu",
        "Tebal · Halus · Pil",
        "Tebal · Halus · Kotak",
        "Tebal · Halus · Kecil",
        "Tebal · Bulat · Ikut Kartu",
        "Tebal · Bulat · Pil",
        "Tebal · Bulat · Kotak",
        "Tebal · Bulat · Kecil",
        "Tebal · Lembut · Ikut Kartu",
        "Tebal · Lembut · Pil",
        "Tebal · Lembut · Kotak",
        "Tebal · Lembut · Kecil",
        "Tebal · Besar · Ikut Kartu",
        "Tebal · Besar · Pil",
        "Tebal · Besar · Kotak",
        "Tebal · Besar · Kecil"
    )
    private val uiDesignStyles = intArrayOf(
        R.style.BL_Ui_D001,
        R.style.BL_Ui_D002,
        R.style.BL_Ui_D003,
        R.style.BL_Ui_D004,
        R.style.BL_Ui_D005,
        R.style.BL_Ui_D006,
        R.style.BL_Ui_D007,
        R.style.BL_Ui_D008,
        R.style.BL_Ui_D009,
        R.style.BL_Ui_D010,
        R.style.BL_Ui_D011,
        R.style.BL_Ui_D012,
        R.style.BL_Ui_D013,
        R.style.BL_Ui_D014,
        R.style.BL_Ui_D015,
        R.style.BL_Ui_D016,
        R.style.BL_Ui_D017,
        R.style.BL_Ui_D018,
        R.style.BL_Ui_D019,
        R.style.BL_Ui_D020,
        R.style.BL_Ui_D021,
        R.style.BL_Ui_D022,
        R.style.BL_Ui_D023,
        R.style.BL_Ui_D024,
        R.style.BL_Ui_D025,
        R.style.BL_Ui_D026,
        R.style.BL_Ui_D027,
        R.style.BL_Ui_D028,
        R.style.BL_Ui_D029,
        R.style.BL_Ui_D030,
        R.style.BL_Ui_D031,
        R.style.BL_Ui_D032,
        R.style.BL_Ui_D033,
        R.style.BL_Ui_D034,
        R.style.BL_Ui_D035,
        R.style.BL_Ui_D036,
        R.style.BL_Ui_D037,
        R.style.BL_Ui_D038,
        R.style.BL_Ui_D039,
        R.style.BL_Ui_D040,
        R.style.BL_Ui_D041,
        R.style.BL_Ui_D042,
        R.style.BL_Ui_D043,
        R.style.BL_Ui_D044,
        R.style.BL_Ui_D045,
        R.style.BL_Ui_D046,
        R.style.BL_Ui_D047,
        R.style.BL_Ui_D048,
        R.style.BL_Ui_D049,
        R.style.BL_Ui_D050,
        R.style.BL_Ui_D051,
        R.style.BL_Ui_D052,
        R.style.BL_Ui_D053,
        R.style.BL_Ui_D054,
        R.style.BL_Ui_D055,
        R.style.BL_Ui_D056,
        R.style.BL_Ui_D057,
        R.style.BL_Ui_D058,
        R.style.BL_Ui_D059,
        R.style.BL_Ui_D060,
        R.style.BL_Ui_D061,
        R.style.BL_Ui_D062,
        R.style.BL_Ui_D063,
        R.style.BL_Ui_D064,
        R.style.BL_Ui_D065,
        R.style.BL_Ui_D066,
        R.style.BL_Ui_D067,
        R.style.BL_Ui_D068,
        R.style.BL_Ui_D069,
        R.style.BL_Ui_D070,
        R.style.BL_Ui_D071,
        R.style.BL_Ui_D072,
        R.style.BL_Ui_D073,
        R.style.BL_Ui_D074,
        R.style.BL_Ui_D075,
        R.style.BL_Ui_D076,
        R.style.BL_Ui_D077,
        R.style.BL_Ui_D078,
        R.style.BL_Ui_D079,
        R.style.BL_Ui_D080,
        R.style.BL_Ui_D081,
        R.style.BL_Ui_D082,
        R.style.BL_Ui_D083,
        R.style.BL_Ui_D084,
        R.style.BL_Ui_D085,
        R.style.BL_Ui_D086,
        R.style.BL_Ui_D087,
        R.style.BL_Ui_D088,
        R.style.BL_Ui_D089,
        R.style.BL_Ui_D090,
        R.style.BL_Ui_D091,
        R.style.BL_Ui_D092,
        R.style.BL_Ui_D093,
        R.style.BL_Ui_D094,
        R.style.BL_Ui_D095,
        R.style.BL_Ui_D096,
        R.style.BL_Ui_D097,
        R.style.BL_Ui_D098,
        R.style.BL_Ui_D099,
        R.style.BL_Ui_D100
    )

    /** Warna aksen siap pakai (esports): nama + warna. */
    val accentPresets = listOf(
        "Vermilion" to 0xFFFF5C39.toInt(), "Merah" to 0xFFFF4655.toInt(), "Crimson" to 0xFFE11D48.toInt(),
        "Oranye" to 0xFFFF9F1C.toInt(), "Amber" to 0xFFFFC83D.toInt(), "Lime" to 0xFFA3E635.toInt(),
        "Hijau" to 0xFF22C55E.toInt(), "Mint" to 0xFF2DD4BF.toInt(), "Cyan" to 0xFF22D3EE.toInt(),
        "Langit" to 0xFF38BDF8.toInt(), "Biru" to 0xFF3B82F6.toInt(), "Indigo" to 0xFF6366F1.toInt(),
        "Violet" to 0xFF8B5CF6.toInt(), "Magenta" to 0xFFD946EF.toInt(), "Pink" to 0xFFF472B6.toInt(),
        "Putih" to 0xFFE5E7EB.toInt()
    )

    /** Dipakai untuk mendeteksi perubahan tema (layar lain dibuat ulang). */
    fun key(ctx: Context) = "${Prefs.themeBase(ctx)}-${Prefs.accentColor(ctx)}-${Prefs.uiDesign(ctx)}-${Prefs.uiFont(ctx)}"

    private fun overlay(theme: android.content.res.Resources.Theme, ctx: Context) {
        theme.applyStyle(baseStyles[Prefs.themeBase(ctx).coerceIn(0, baseStyles.size - 1)], true)
        theme.applyStyle(uiDesignStyles[Prefs.uiDesign(ctx).coerceIn(0, uiDesignStyles.size - 1)], true)
    }

    /** Terapkan tema pilihan ke context apa pun (mis. dialog). */
    fun applyTo(ctx: Context) = overlay(ctx.theme, ctx)

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

    /** Nilai dimensi dari atribut tema (px). */
    fun dimen(ctx: Context, attr: Int): Float {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return tv.getDimension(ctx.resources.displayMetrics)
    }

    /** Context dengan desain UI nomor [i] (untuk pratinjau pilihan). */
    fun designContext(ctx: Context, i: Int): Context {
        val c = ContextThemeWrapper(ctx, R.style.Theme_BracketLive)
        c.theme.applyStyle(baseStyles[Prefs.themeBase(ctx).coerceIn(0, baseStyles.size - 1)], true)
        c.theme.applyStyle(uiDesignStyles[i.coerceIn(0, uiDesignStyles.size - 1)], true)
        return c
    }

    fun accent1(ctx: Context) = Prefs.accentColor(ctx)
    fun accent2(ctx: Context) = shiftHue(accent1(ctx), 28f)
    fun accent3(ctx: Context) = shiftHue(accent1(ctx), -28f)

    fun shiftHue(color: Int, deg: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + deg + 360f) % 360f
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    /** Warna teks yang terbaca di atas [accent] (gelap untuk aksen terang, putih untuk aksen gelap). */
    fun onAccent(accent: Int): Int {
        val r = Color.red(accent) / 255.0
        val g = Color.green(accent) / 255.0
        val b = Color.blue(accent) / 255.0
        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return if (lum > 0.55) 0xFF0B0F1A.toInt() else Color.WHITE
    }
}
