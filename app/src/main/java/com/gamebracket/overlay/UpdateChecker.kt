package com.gamebracket.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Cek rilis terbaru di GitHub Releases (dibuat otomatis oleh GitHub Actions dengan tag "build-<nomor>").
 * Membaca daftar rilis lalu memilih nomor build tertinggi yang punya file .apk.
 */
object UpdateChecker {

    private const val REPO = "FeynJf/BracketLive"

    class Info(val build: Int, val title: String, val notes: String, val apkUrl: String, val size: Long = 0L)

    /** Hasil cek: [info] terisi kalau ada rilis; kalau tidak, [error] berisi alasannya. */
    class Check(val info: Info?, val error: String?)

    /** Blocking — panggil dari thread background. */
    fun fetchLatest(): Check {
        val conn = URL("https://api.github.com/repos/$REPO/releases?per_page=30").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "BracketLive-App")
            conn.setRequestProperty("Cache-Control", "no-cache")
            val code = conn.responseCode
            if (code == 403 || code == 429) return Check(null, "Batas permintaan GitHub tercapai. Coba lagi beberapa menit lagi.")
            if (code == 404) return Check(null, "Repo GitHub tidak ditemukan (pastikan repo berstatus Public).")
            if (code != 200) return Check(null, "GitHub membalas HTTP $code")
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            var best: Info? = null
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                if (r.optBoolean("draft", false)) continue
                val tag = r.optString("tag_name")
                val build = Regex("(\\d+)").find(tag)?.value?.toIntOrNull() ?: continue
                val assets = r.optJSONArray("assets") ?: continue
                var url = ""
                var size = 0L
                for (k in 0 until assets.length()) {
                    val a = assets.optJSONObject(k) ?: continue
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        url = a.optString("browser_download_url")
                        size = a.optLong("size", 0L)
                        break
                    }
                }
                if (url.isEmpty()) continue
                val cur = best
                if (cur == null || build > cur.build) {
                    best = Info(build, r.optString("name", tag), r.optString("body", ""), url, size)
                }
            }
            val found = best
            return if (found != null) Check(found, null)
            else Check(null, "Belum ada rilis APK di GitHub. Jalankan workflow Build APK sampai selesai dulu.")
        } finally {
            conn.disconnect()
        }
    }

    fun explain(e: Exception): String = when (e) {
        is UnknownHostException -> "Tidak ada koneksi internet"
        is SocketTimeoutException -> "Koneksi ke GitHub terlalu lambat"
        else -> "Gagal mengecek update: ${e.message ?: e.javaClass.simpleName}"
    }

    /** Pastikan APK hasil unduhan memang untuk aplikasi ini dan lebih baru. Null = aman dipasang. */
    fun verify(ctx: Context, apk: File, currentBuild: Int): String? {
        val info = try {
            ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        } catch (e: Exception) {
            null
        } ?: return null
        if (info.packageName != ctx.packageName) return "APK yang diunduh bukan untuk aplikasi Bracket Live"
        if (PackageInfoCompat.getLongVersionCode(info) <= currentBuild) return "APK yang diunduh tidak lebih baru dari yang terpasang"
        return null
    }

    /** Blocking — unduh APK ke cache. [onProgress] dipanggil dengan 0..100 (atau -1 kalau ukuran tak diketahui). */
    fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "BracketLive-update.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "BracketLive-App")
            conn.setRequestProperty("Accept", "application/octet-stream")
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = conn.contentLength
            var done = 0L
            var last = -2
            conn.inputStream.use { input ->
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else -1
                        if (pct != last) { last = pct; onProgress(pct) }
                    }
                }
            }
            if (file.length() <= 0L || (total > 0 && file.length() != total.toLong())) {
                throw IllegalStateException("unduhan tidak lengkap, coba lagi")
            }
        } finally {
            conn.disconnect()
        }
        return file
    }

    fun installIntent(ctx: Context, apk: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
