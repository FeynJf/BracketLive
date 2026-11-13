package com.gamebracket.overlay

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catatan error. Crash fatal disimpan ke berkas lalu dilaporkan di peluncuran berikutnya;
 * error non-fatal (yang sudah ditangani) dicatat supaya mudah dilacak di Pengaturan → Performa.
 */
object CrashLog {
    private const val FATAL = "crash_report.txt"
    private const val ERRORS = "error_log.txt"
    private const val MAX_BYTES = 20_000

    private class Handler(val ctx: Context, val prev: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            try { write(ctx, FATAL, "FATAL (${t.name})", e, replace = true) } catch (_: Throwable) { }
            prev?.uncaughtException(t, e)
        }
    }

    fun install(ctx: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        if (prev is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(ctx.applicationContext, prev))
    }

    /** Catat error yang sudah ditangani (tidak membuat aplikasi berhenti). */
    fun record(ctx: Context, tag: String, e: Throwable) {
        try { write(ctx, ERRORS, tag, e, replace = false) } catch (_: Throwable) { }
    }

    private fun write(ctx: Context, name: String, tag: String, e: Throwable, replace: Boolean) {
        val f = File(ctx.filesDir, name)
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "[$time] $tag · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            sw.toString().lines().take(24).joinToString("\n") + "\n\n"
        val old = if (!replace && f.exists()) f.readText() else ""
        val all = (old + entry)
        f.writeText(if (all.length > MAX_BYTES) all.takeLast(MAX_BYTES) else all)
    }

    fun pendingFatal(ctx: Context): String? = read(ctx, FATAL)
    fun errors(ctx: Context): String? = read(ctx, ERRORS)

    private fun read(ctx: Context, name: String): String? = try {
        val f = File(ctx.filesDir, name)
        if (f.exists() && f.length() > 0) f.readText() else null
    } catch (e: Exception) { null }

    fun clearFatal(ctx: Context) { File(ctx.filesDir, FATAL).delete() }
    fun clearErrors(ctx: Context) { File(ctx.filesDir, ERRORS).delete() }
}
