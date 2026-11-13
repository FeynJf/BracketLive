package com.gamebracket.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Cek build terbaru. Sumber:
 * 1) branch "apk-latest" — diisi otomatis oleh task Gradle publishApk di setiap build GitHub Actions
 *    (tidak perlu mengubah workflow);
 * 2) GitHub Releases dengan tag "build-<nomor>" (cadangan, kalau workflow membuat rilis).
 */
object UpdateChecker {

    private const val REPO = "FeynJf/BracketLive"
    private const val BRANCH = "apk-latest"

    class Info(val build: Int, val title: String, val notes: String, val apkUrl: String, val size: Long = 0L)

    /** Hasil cek: [info] terisi kalau ada rilis; kalau tidak, [error] berisi alasannya. */
    class Check(val info: Info?, val error: String?)

    /** Blocking — panggil dari thread background. */
    fun fetchLatest(): Check {
        var branchError: String? = null
        try {
            fromBranch()?.let { return Check(it, null) }
        } catch (e: Exception) {
            branchError = explain(e)
        }
        val rel = try { fromReleases() } catch (e: Exception) { Check(null, explain(e)) }
        if (rel.info != null) return rel
        return Check(null, branchError ?: rel.error)
    }

    /** GET → isi respons; null kalau 404; melempar exception untuk error lain (mis. 403 rate limit). */
    private fun httpGet(url: String, api: Boolean): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "BracketLive-App")
            conn.setRequestProperty("Cache-Control", "no-cache")
            if (api) conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            if (code == 404) return null
            if (code == 403 || code == 429) throw IllegalStateException("Batas permintaan GitHub tercapai. Coba lagi beberapa menit lagi.")
            if (code != 200) throw IllegalStateException("GitHub membalas HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Baca version.json + APK dari branch apk-latest. Null = branch/berkas belum ada. */
    private fun fromBranch(): Info? {
        // version.json lewat API (segar); cadangan lewat raw kalau API dibatasi
        var text: String? = null
        var apiFailure: Exception? = null
        try {
            val body = httpGet("https://api.github.com/repos/$REPO/contents/version.json?ref=$BRANCH", true)
                ?: return null // branch belum ada
            val b64 = JSONObject(body).optString("content").replace("\n", "")
            text = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            apiFailure = e
        }
        if (text == null) {
            text = try {
                httpGet("https://raw.githubusercontent.com/$REPO/$BRANCH/version.json", false)
            } catch (e: Exception) {
                throw apiFailure ?: e
            } ?: return null
        }

        val j = JSONObject(text)
        val build = j.optInt("build", -1)
        if (build < 0) return null
        val apkName = j.optString("apk", "BracketLive.apk")

        // kunci URL APK ke commit supaya tidak kena cache CDN (selalu cocok dengan version.json)
        val sha = try {
            httpGet("https://api.github.com/repos/$REPO/commits/$BRANCH", true)?.let { JSONObject(it).optString("sha") }
        } catch (e: Exception) {
            null
        }
        val ref = if (!sha.isNullOrEmpty()) sha else BRANCH
        return Info(
            build,
            j.optString("name", "build $build"),
            j.optString("notes", ""),
            "https://raw.githubusercontent.com/$REPO/$ref/$apkName"
        )
    }

    private fun fromReleases(): Check {
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
            else Check(null, "Belum ada build yang dipublikasikan. Jalankan workflow Build APK sampai selesai dulu — setelah itu APK terbaru otomatis terdeteksi.")
        } finally {
            conn.disconnect()
        }
    }

    /** Ubah catatan update (markdown sederhana) jadi teks berpoin yang rapi untuk ditampilkan. */
    fun prettyNotes(raw: String): String {
        val sb = StringBuilder()
        for (line in raw.replace("\r", "").lines()) {
            val l = line.trim()
            if (l.isEmpty()) continue
            if (l.startsWith("-") || l.startsWith("*") || l.startsWith("•")) {
                sb.append("• ").append(l.trimStart('-', '*', '•', ' ')).append('\n')
            } else {
                sb.append(l.trimStart('#', ' ')).append('\n')
            }
        }
        return sb.toString().trim()
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
