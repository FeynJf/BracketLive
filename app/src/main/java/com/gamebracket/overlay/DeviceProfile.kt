package com.gamebracket.overlay

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Menilai kemampuan perangkat (RAM, inti CPU, versi Android) supaya animasi, confetti, blur, dan
 * ukuran gambar menyesuaikan — mulus di HP kelas atas, tetap ringan di HP kentang.
 */
object DeviceProfile {

    enum class Tier { LOW, MID, HIGH }

    private var detected: Tier? = null
    private var ramMb = 0L

    private fun detect(ctx: Context): Tier {
        detected?.let { return it }
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        try { am?.getMemoryInfo(mi) } catch (e: Exception) { /* abaikan */ }
        ramMb = mi.totalMem / (1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()
        val lowRam = am?.isLowRamDevice == true
        val memClass = am?.memoryClass ?: 192
        val t = when {
            lowRam || (ramMb in 1..2999) || Build.VERSION.SDK_INT <= 25 || memClass <= 128 -> Tier.LOW
            ramMb >= 6000 && cores >= 6 && Build.VERSION.SDK_INT >= 29 -> Tier.HIGH
            else -> Tier.MID
        }
        detected = t
        return t
    }

    /** Tingkat yang dipakai: otomatis dari perangkat, atau dipaksa lewat Pengaturan → Performa. */
    fun tier(ctx: Context): Tier = when (Prefs.perfMode(ctx)) {
        1 -> Tier.LOW
        2 -> Tier.HIGH
        else -> detect(ctx)
    }

    fun reset() { detected = null }

    /** Pengali durasi animasi (HP lemah = animasi lebih singkat). */
    fun animScale(ctx: Context): Float = when (tier(ctx)) { Tier.LOW -> 0.6f; Tier.MID -> 0.85f; Tier.HIGH -> 1f }

    /** Batas jumlah elemen yang dianimasikan berurutan sekaligus. */
    fun maxStagger(ctx: Context): Int = when (tier(ctx)) { Tier.LOW -> 4; Tier.MID -> 8; Tier.HIGH -> 14 }

    fun confettiCount(ctx: Context): Int = when (tier(ctx)) { Tier.LOW -> 24; Tier.MID -> 55; Tier.HIGH -> 90 }

    /** Sisi terpanjang gambar latar sertifikat saat dibuka (px). */
    fun maxImageSide(ctx: Context): Int = when (tier(ctx)) { Tier.LOW -> 1000; Tier.MID -> 1400; Tier.HIGH -> 1600 }

    /** Kualitas pratinjau sertifikat (skala bitmap). */
    fun previewQuality(ctx: Context): Float = when (tier(ctx)) { Tier.LOW -> 0.6f; Tier.MID -> 0.8f; Tier.HIGH -> 1f }

    fun allowBlur(ctx: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && tier(ctx) != Tier.LOW

    /** true kalau animasi sistem dimatikan pengguna (Opsi Pengembang / Aksesibilitas). */
    fun systemAnimationsOff(ctx: Context): Boolean = try {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f
    } catch (e: Exception) { false }

    fun tierName(ctx: Context) = when (tier(ctx)) { Tier.LOW -> "Hemat"; Tier.MID -> "Sedang"; Tier.HIGH -> "Maksimal" }

    fun describe(ctx: Context): String {
        detect(ctx)
        val ram = if (ramMb > 0) "%.1f GB RAM".format(ramMb / 1024.0) else "RAM ?"
        return "$ram · ${Runtime.getRuntime().availableProcessors()} inti · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
    }
}
