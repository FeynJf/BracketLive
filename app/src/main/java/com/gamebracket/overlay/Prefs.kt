package com.gamebracket.overlay

import android.content.Context

/** Semua pengaturan aplikasi (SharedPreferences). */
object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("bracketlive", Context.MODE_PRIVATE)
    private fun int(ctx: Context, k: String, d: Int) = sp(ctx).getInt(k, d)
    private fun putInt(ctx: Context, k: String, v: Int) { sp(ctx).edit().putInt(k, v).apply() }
    private fun bool(ctx: Context, k: String, d: Boolean) = sp(ctx).getBoolean(k, d)
    private fun putBool(ctx: Context, k: String, v: Boolean) { sp(ctx).edit().putBoolean(k, v).apply() }
    private fun str(ctx: Context, k: String, d: String) = sp(ctx).getString(k, d) ?: d
    private fun putStr(ctx: Context, k: String, v: String) { sp(ctx).edit().putString(k, v).apply() }

    // ── Tampilan ──
    fun themeBase(ctx: Context) = int(ctx, "theme_base", 0)
    fun setThemeBase(ctx: Context, v: Int) = putInt(ctx, "theme_base", v)
    fun accent(ctx: Context) = int(ctx, "accent", 0)
    fun setAccent(ctx: Context, v: Int) = putInt(ctx, "accent", v)
    fun animations(ctx: Context) = bool(ctx, "animations", true)
    fun setAnimations(ctx: Context, v: Boolean) = putBool(ctx, "animations", v)

    /** 0 = otomatis dari perangkat, 1 = hemat, 2 = maksimal. */
    fun perfMode(ctx: Context) = int(ctx, "perf_mode", 0)
    fun setPerfMode(ctx: Context, v: Int) = putInt(ctx, "perf_mode", v)

    /** Hapus semua pengaturan (kembali ke bawaan). */
    fun clearAll(ctx: Context) { sp(ctx).edit().clear().apply() }

    // ── Overlay ──
    /** Opasitas latar panel overlay, 30..100 (persen). */
    fun panelOpacity(ctx: Context): Int = int(ctx, "panel_opacity", 70).coerceIn(30, 100)
    fun setPanelOpacity(ctx: Context, v: Int) = putInt(ctx, "panel_opacity", v.coerceIn(30, 100))
    /** Lama menahan ikon sebelum panel terbuka (ms, 300..1500). */
    fun holdMs(ctx: Context): Int = int(ctx, "hold_ms", 700).coerceIn(300, 1500)
    fun setHoldMs(ctx: Context, v: Int) = putInt(ctx, "hold_ms", v.coerceIn(300, 1500))
    // Desain & ukuran PANEL overlay (terpisah dari ikon)
    fun panelStyle(ctx: Context) = int(ctx, "panel_style", 0)
    fun setPanelStyle(ctx: Context, v: Int) = putInt(ctx, "panel_style", v)
    /** Sudut membulat panel, dp (0..32). */
    fun panelRadius(ctx: Context) = int(ctx, "panel_radius", 20).coerceIn(0, 32)
    fun setPanelRadius(ctx: Context, v: Int) = putInt(ctx, "panel_radius", v.coerceIn(0, 32))
    /** Lebar panel, persen lebar layar (55..100). */
    fun panelWidthPct(ctx: Context) = int(ctx, "panel_w_pct", 100).coerceIn(55, 100)
    fun setPanelWidthPct(ctx: Context, v: Int) = putInt(ctx, "panel_w_pct", v.coerceIn(55, 100))
    /** Tinggi area isi panel, persen tinggi layar (15..85). 0 = otomatis mengikuti isi. */
    fun panelHeightPct(ctx: Context): Int { val v = int(ctx, "panel_h_pct", 0); return if (v <= 0) 0 else v.coerceIn(15, 85) }
    fun setPanelHeightPct(ctx: Context, v: Int) = putInt(ctx, "panel_h_pct", if (v <= 0) 0 else v.coerceIn(15, 85))
    /** Zoom isi panel (teks & kartu bracket), persen 70..150. */
    fun panelZoom(ctx: Context) = int(ctx, "panel_zoom", 100).coerceIn(70, 150)
    fun setPanelZoom(ctx: Context, v: Int) = putInt(ctx, "panel_zoom", v.coerceIn(70, 150))

    /** Ukuran IKON overlay (persen, 70..150). */
    fun overlayScale(ctx: Context): Int = int(ctx, "overlay_scale", 100).coerceIn(70, 150)
    fun setOverlayScale(ctx: Context, v: Int) = putInt(ctx, "overlay_scale", v.coerceIn(70, 150))
    fun bubbleStyle(ctx: Context) = int(ctx, "bubble_style", 0)
    fun setBubbleStyle(ctx: Context, v: Int) = putInt(ctx, "bubble_style", v)
    fun bubbleIcon(ctx: Context) = int(ctx, "bubble_icon", 0)
    fun setBubbleIcon(ctx: Context, v: Int) = putInt(ctx, "bubble_icon", v)
    fun bubbleCustom(ctx: Context): String? = sp(ctx).getString("bubble_custom", null)
    fun setBubbleCustom(ctx: Context, v: String?) { sp(ctx).edit().putString("bubble_custom", v).apply() }

    // ── Turnamen ──
    /** true = bracket langsung mulai dari semifinal (4 tim). */
    fun semifinalOnly(ctx: Context) = bool(ctx, "semifinal_only", false)
    fun setSemifinalOnly(ctx: Context, v: Boolean) = putBool(ctx, "semifinal_only", v)
    fun killPoint(ctx: Context) = int(ctx, "kill_point", 1)
    fun setKillPoint(ctx: Context, v: Int) = putInt(ctx, "kill_point", v)
    fun placementPoints(ctx: Context) = str(ctx, "placement_pts", "12,9,8,7,6,5,4,3,2,1")
    fun setPlacementPoints(ctx: Context, v: String) = putStr(ctx, "placement_pts", v)

    // ── Koneksi live ──
    fun feedUrl(ctx: Context) = str(ctx, "feed_url", "")
    fun setFeedUrl(ctx: Context, v: String) = putStr(ctx, "feed_url", v)
    fun feedInterval(ctx: Context) = int(ctx, "feed_interval", 10).coerceIn(5, 60)
    fun setFeedInterval(ctx: Context, v: Int) = putInt(ctx, "feed_interval", v.coerceIn(5, 60))
    fun feedEnabled(ctx: Context) = bool(ctx, "feed_enabled", false)
    fun setFeedEnabled(ctx: Context, v: Boolean) = putBool(ctx, "feed_enabled", v)

    /** Game di perangkat yang dipilih untuk Koneksi Live (nama paket + nama tampil). */
    fun liveGamePkg(ctx: Context) = str(ctx, "live_game_pkg", "")
    fun liveGameName(ctx: Context) = str(ctx, "live_game_name", "")
    fun setLiveGame(ctx: Context, pkg: String, name: String) {
        sp(ctx).edit().putString("live_game_pkg", pkg).putString("live_game_name", name).apply()
    }
}
