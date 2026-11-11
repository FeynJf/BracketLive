package com.gamebracket.overlay

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Build

/** Daftar game yang terpasang di perangkat (dipakai untuk fitur Koneksi Live). */
object InstalledGames {

    class App(val pkg: String, val label: String, val icon: Drawable?, val gameId: String?, val isGame: Boolean)

    /** Nama paket game populer → id game di [BracketStore.games]. */
    private val known = mapOf(
        "com.dts.freefireth" to "freefire",
        "com.dts.freefiremax" to "freefire",
        "com.tencent.ig" to "pubg",
        "com.pubg.imobile" to "pubg",
        "com.pubg.krmobile" to "pubg",
        "com.vng.pubgmobile" to "pubg",
        "com.rekoo.pubgm" to "pubg",
        "com.mobile.legends" to "mlbb",
        "com.activision.callofduty.shooter" to "codm",
        "com.garena.game.codm" to "codm",
        "com.tencent.tmgp.cod" to "codm"
    )

    fun gameIdFor(pkg: String): String? = known[pkg]

    /**
     * Blocking — panggil dari thread background.
     * [includeAll] = false → hanya game; true → semua aplikasi yang punya ikon peluncur.
     */
    @Suppress("DEPRECATION")
    fun load(ctx: Context, includeAll: Boolean): List<App> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try { pm.queryIntentActivities(intent, 0) } catch (e: Exception) { emptyList() }
        val seen = HashSet<String>()
        val out = ArrayList<App>()
        for (ri in resolved) {
            val ai = ri.activityInfo?.applicationInfo ?: continue
            val pkg = ai.packageName ?: continue
            if (pkg == ctx.packageName || !seen.add(pkg)) continue
            val game = isGame(ai)
            if (!game && !includeAll) continue
            val label = try { ri.loadLabel(pm).toString() } catch (e: Exception) { pkg }
            val icon = try { ri.loadIcon(pm) } catch (e: Exception) { null }
            out.add(App(pkg, label, icon, known[pkg], game))
        }
        return out.sortedWith(
            compareBy<App>({ it.gameId == null }, { !it.isGame }, { it.label.lowercase() })
        )
    }

    @Suppress("DEPRECATION")
    private fun isGame(ai: ApplicationInfo): Boolean {
        if (known.containsKey(ai.packageName)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ai.category == ApplicationInfo.CATEGORY_GAME) return true
        return (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    }
}
