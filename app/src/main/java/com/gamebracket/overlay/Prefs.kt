package com.gamebracket.overlay

import android.content.Context

/** Pengaturan sederhana (disimpan di SharedPreferences). */
object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("bracketlive", Context.MODE_PRIVATE)

    /** Opasitas latar panel overlay, 30..100 (persen). */
    fun panelOpacity(ctx: Context): Int = sp(ctx).getInt("panel_opacity", 70).coerceIn(30, 100)
    fun setPanelOpacity(ctx: Context, v: Int) { sp(ctx).edit().putInt("panel_opacity", v.coerceIn(30, 100)).apply() }

    /** Lama menahan ikon sebelum panel terbuka, dalam milidetik (300..1500). */
    fun holdMs(ctx: Context): Int = sp(ctx).getInt("hold_ms", 700).coerceIn(300, 1500)
    fun setHoldMs(ctx: Context, v: Int) { sp(ctx).edit().putInt("hold_ms", v.coerceIn(300, 1500)).apply() }
}
