package com.gamebracket.overlay

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Mendeteksi aplikasi yang sedang tampil di layar lewat "Akses penggunaan" (UsageStats).
 * Dipakai untuk tahu kapan game yang dipilih sedang dibuka.
 */
object GameWatcher {

    private var fg: String? = null
    private var lastEnd = 0L

    @Suppress("DEPRECATION")
    fun hasAccess(ctx: Context): Boolean {
        return try {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            } else {
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun reset() {
        fg = null
        lastEnd = 0L
    }

    /** Nama paket yang sedang di layar, null kalau tidak diketahui. Panggil dari thread background. */
    @Synchronized
    @Suppress("DEPRECATION")
    fun foreground(ctx: Context): String? {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return fg
        val now = System.currentTimeMillis()
        val begin = if (lastEnd == 0L) now - 2 * 60 * 60 * 1000L else lastEnd - 1000L
        val events = try { usm.queryEvents(begin, now) } catch (e: Exception) { null } ?: return fg
        val ev = UsageEvents.Event()
        var cur = fg
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            when (ev.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> cur = ev.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (ev.packageName == cur) cur = null
            }
        }
        lastEnd = now
        fg = cur
        return cur
    }
}
