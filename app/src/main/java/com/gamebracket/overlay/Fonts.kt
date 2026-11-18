package com.gamebracket.overlay

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Font dari katalog Google Fonts. Berkas font tidak dibundel di APK (ratusan MB), tapi diunduh
 * sekali saat dipilih lalu disimpan di perangkat — setelah itu dipakai offline.
 */
object Fonts {

    private const val BASE = "https://fonts.gstatic.com/s/"
    private const val UA = "Mozilla/5.0 (Linux; U; Android 4.4.2; en-us; Nexus 5 Build/KOT49H) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2)
    private val faces = HashMap<String, Typeface>()
    private val pending = HashMap<String, MutableList<(Boolean) -> Unit>>()
    private val listeners = LinkedHashMap<String, () -> Unit>()
    private var catalog: Map<String, String>? = null
    private val lists = HashMap<String, List<String>>()

    /** Naik tiap font baru selesai diunduh (layar menyegarkan teksnya). */
    @Volatile var version = 0
        private set

    // ───────────── daftar & katalog ─────────────

    /** Daftar nama font: "ui" (700), "title" (300), "cert" (300), "sign" (20). */
    fun list(ctx: Context, which: String): List<String> = lists.getOrPut(which) {
        try {
            ctx.assets.open("fontlist/$which.txt").bufferedReader().use { r -> r.readLines().filter { it.isNotBlank() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun catalog(ctx: Context): Map<String, String> {
        catalog?.let { return it }
        val m = LinkedHashMap<String, String>()
        try {
            for (part in listOf("catalog1.tsv", "catalog2.tsv")) {
                ctx.assets.open("fontlist/$part").bufferedReader().use { r ->
                    r.forEachLine { line ->
                        val i = line.indexOf('\t')
                        if (i > 0) m[line.substring(0, i)] = line.substring(i + 1).trim()
                    }
                }
            }
        } catch (e: Exception) {
            CrashLog.record(ctx, "baca katalog font", e)
        }
        catalog = m
        return m
    }

    // ───────────── cache & unduh ─────────────

    private fun file(ctx: Context, family: String): File {
        val slug = family.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        return File(File(ctx.filesDir, "gfonts").apply { mkdirs() }, "$slug.ttf")
    }

    /** Font yang sudah ada di perangkat (memori atau berkas). Null kalau belum diunduh. */
    fun cached(ctx: Context, family: String): Typeface? {
        if (family.isBlank()) return null
        faces[family]?.let { return it }
        val f = file(ctx, family)
        if (!f.exists() || f.length() < 2048) return null
        return try {
            Typeface.createFromFile(f).also { faces[family] = it }
        } catch (e: Exception) {
            f.delete()
            null
        }
    }

    /** Pastikan font ada; unduh kalau perlu. [cb] dipanggil di main thread (true = siap dipakai). */
    fun ensure(ctx: Context, family: String, cb: (Boolean) -> Unit = {}) {
        if (family.isBlank()) { cb(true); return }
        if (cached(ctx, family) != null) { cb(true); return }
        val app = ctx.applicationContext
        synchronized(pending) {
            val waiting = pending[family]
            if (waiting != null) { waiting.add(cb); return }
            pending[family] = mutableListOf(cb)
        }
        pool.execute {
            val ok = try { download(app, family) } catch (e: Exception) { CrashLog.record(app, "unduh font $family", e); false }
            main.post {
                if (ok) { version++; listeners.values.toList().forEach { it() } }
                val cbs = synchronized(pending) { pending.remove(family) } ?: mutableListOf()
                cbs.forEach { it(ok && cached(app, family) != null) }
            }
        }
    }

    private fun fetch(url: String, dest: File): Boolean {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", UA)
            if (conn.responseCode != 200) return false
            val part = File(dest.parentFile, dest.name + ".part")
            conn.inputStream.use { i -> part.outputStream().use { o -> i.copyTo(o) } }
            if (part.length() < 2048) { part.delete(); return false }
            // pastikan berkasnya font yang valid sebelum dipakai
            Typeface.createFromFile(part)
            return part.renameTo(dest)
        } finally {
            conn.disconnect()
        }
    }

    private fun download(ctx: Context, family: String): Boolean {
        val dest = file(ctx, family)
        val path = catalog(ctx)[family]
        if (path != null) {
            try { if (fetch(BASE + path, dest)) return true } catch (e: Exception) { /* coba jalur cadangan */ }
        }
        // cadangan: minta CSS ke Google Fonts lalu ambil URL TTF-nya
        val css = URL("https://fonts.googleapis.com/css?family=" + URLEncoder.encode(family, "UTF-8"))
            .openConnection().let { c ->
                c.setRequestProperty("User-Agent", UA)
                c.connectTimeout = 12000
                c.readTimeout = 12000
                c.getInputStream().bufferedReader().use { it.readText() }
            }
        val url = Regex("url\\((https://[^)]+\\.ttf)\\)").find(css)?.groupValues?.get(1) ?: return false
        return fetch(url, dest)
    }

    // ───────────── pemakaian ─────────────

    fun uiFace(ctx: Context): Typeface? = Prefs.uiFont(ctx).let { if (it.isEmpty()) null else cached(ctx, it) }
    fun titleFace(ctx: Context): Typeface? = Prefs.titleFont(ctx).let { if (it.isEmpty()) null else cached(ctx, it) }

    /** Unduh font pilihan yang belum ada (dipanggil saat aplikasi dibuka). */
    fun preload(ctx: Context) {
        listOf(Prefs.uiFont(ctx), Prefs.titleFont(ctx)).forEach { if (it.isNotEmpty()) ensure(ctx, it) }
    }

    fun addListener(key: String, fn: (() -> Unit)?) {
        if (fn == null) listeners.remove(key) else listeners[key] = fn
    }

    /** Terapkan font aplikasi ke semua TextView di [root] (judul turnamen memakai font judulnya sendiri). */
    fun apply(root: View) {
        val ctx = root.context
        val ui = uiFace(ctx)
        val title = titleFace(ctx)
        if (ui == null && title == null) return
        walk(root, ctx, ui, title)
    }

    private fun walk(v: View, ctx: Context, ui: Typeface?, title: Typeface?) {
        if (v is TextView) {
            val role = v.getTag(R.id.font_role) as? String
            val face = when (role) {
                "keep" -> null
                "title" -> title ?: ui
                else -> ui
            }
            if (face != null && v.getTag(R.id.font_key) != face) {
                v.setTypeface(face, v.typeface?.style ?: Typeface.NORMAL)
                v.setTag(R.id.font_key, face)
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), ctx, ui, title)
    }

    /** Tandai TextView agar font aplikasi tidak menimpanya (mis. pratinjau font). */
    fun markKeep(v: TextView) { v.setTag(R.id.font_role, "keep") }

    /** Tandai TextView sebagai judul turnamen (memakai font & gaya judul). */
    fun markTitle(v: TextView) { v.setTag(R.id.font_role, "title") }
}
