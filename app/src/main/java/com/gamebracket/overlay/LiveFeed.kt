package com.gamebracket.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Koneksi live.
 *
 * Mode utama (v1.6): pilih game yang terpasang di perangkat → Bracket Live terhubung dan memantau
 * apakah game itu sedang dibuka ([GameWatcher]). Tidak perlu URL.
 *
 * Mode lanjutan (opsional): kalau URL feed diisi, data tim/poin juga dibaca otomatis dari feed.
 *
 * Game mobile tidak menyediakan API resmi untuk membaca room dari luar, jadi Bracket Live membaca
 * "feed" JSON (HTTPS) yang kamu isi dari tool/bot/spreadsheet milik panitia. Tiap [interval] detik feed
 * dibaca ulang: tim yang ikut terdeteksi otomatis dan poin dihitung otomatis dari aturan di Pengaturan.
 *
 * Format feed:
 * {"match":1,"teams":[{"name":"EVOS","kills":5,"placement":2,"members":["A","B"]}]}
 */
object LiveFeed {

    private class FeedTeam(val name: String, val kills: Int, val placement: Int, val members: List<String>)

    @Volatile var running = false
        private set
    @Volatile var status = "Belum terhubung"
        private set

    /** Naik tiap start() supaya polling lama yang masih berjalan tidak ikut menjadwalkan ulang. */
    @Volatile private var gen = 0
    private var deviceLine: String? = null
    private var feedLine: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashMap<String, (Boolean) -> Unit>()
    private var appCtx: Context? = null
    private var autoFilled = false

    /** match → (nama tim → poin match tsb) */
    private val matchPoints = HashMap<Int, HashMap<String, Int>>()

    /** [fn] dipanggil di main thread; argumen true = data tim/poin berubah (perlu gambar ulang). */
    fun setListener(key: String, fn: ((Boolean) -> Unit)?) {
        if (fn == null) listeners.remove(key) else listeners[key] = fn
    }

    private fun fireListeners(dataChanged: Boolean = true) = listeners.values.toList().forEach { it(dataChanged) }

    private fun signature(): String =
        BracketStore.mode?.id + "|" + BracketStore.teams.joinToString(";") { "${it.name}:${it.score}:${it.members.size}" } +
            "|" + BracketStore.players.joinToString(";")

    /** Gagal berturut-turut → jeda cek makin panjang (maks 8x) supaya hemat baterai & kuota. */
    private var failures = 0

    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        if (running) return
        running = true
        gen++
        failures = 0
        autoFilled = false
        matchPoints.clear()
        deviceLine = null
        feedLine = null
        GameWatcher.reset()
        status = "Menghubungkan…"
        fireListeners(false)
        schedule(0, gen)
    }

    fun stop() {
        running = false
        gen++
        handler.removeCallbacksAndMessages(null)
        status = "Terputus"
        fireListeners(false)
    }

    private fun refreshStatus() {
        val parts = listOfNotNull(deviceLine, feedLine)
        status = if (parts.isEmpty()) "Terhubung" else "Terhubung · " + parts.joinToString(" · ")
    }

    /** Status game terpilih: sedang berjalan / menunggu dibuka / perlu izin akses penggunaan. */
    private fun gameStatusLine(ctx: Context, pkg: String): String {
        val name = Prefs.liveGameName(ctx).ifEmpty { pkg }
        if (!GameWatcher.hasAccess(ctx)) return "$name terpilih · aktifkan Akses penggunaan untuk mendeteksi game"
        return if (GameWatcher.foreground(ctx) == pkg) "$name sedang berjalan" else "Menunggu $name dibuka"
    }

    /** Baca sekali (tombol "Tes koneksi"). Hasil lewat [onResult] di main thread. */
    fun test(ctx: Context, onResult: (String) -> Unit) {
        val url = Prefs.feedUrl(ctx).trim()
        Thread {
            val msg = try {
                val (match, teams) = fetch(url)
                "OK · match $match · ${teams.size} tim terdeteksi"
            } catch (e: Exception) {
                "Gagal: ${e.message}"
            }
            handler.post { onResult(msg) }
        }.start()
    }

    private fun schedule(delayMs: Long, g: Int) {
        handler.postDelayed({ if (running && g == gen) pollOnce(g) }, delayMs)
    }

    private fun pollOnce(g: Int) {
        val ctx = appCtx ?: return
        val url = Prefs.feedUrl(ctx).trim()
        val pkg = Prefs.liveGamePkg(ctx)
        // tanpa URL feed hanya memantau game → cukup cek tiap 3 detik
        val interval = (if (url.isEmpty()) 3 else Prefs.feedInterval(ctx)) * 1000L
        Thread {
            val gameLine = if (pkg.isNotEmpty()) gameStatusLine(ctx, pkg) else null
            var result: Pair<Int, List<FeedTeam>>? = null
            var error: String? = null
            if (url.isNotEmpty()) {
                try {
                    result = fetch(url)
                } catch (e: Exception) {
                    error = e.message ?: e.javaClass.simpleName
                }
            }
            val res = result
            val err = error
            handler.post {
                if (running && g == gen) {
                    deviceLine = gameLine
                    if (url.isEmpty()) {
                        feedLine = null
                        refreshStatus()
                        fireListeners(false)
                    } else if (res != null) {
                        failures = 0
                        apply(ctx, res.first, res.second)
                    } else {
                        failures++
                        feedLine = "Feed gagal (coba lagi otomatis): $err"
                        refreshStatus()
                        fireListeners(false)
                    }
                    val backoff = if (failures > 0) (1L shl minOf(failures, 3)) else 1L
                    schedule(interval * backoff, g)
                }
            }
        }.start()
    }

    private fun fetch(url: String): Pair<Int, List<FeedTeam>> {
        if (!url.startsWith("https://")) throw IllegalArgumentException("URL harus diawali https://")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "BracketLive-App")
            conn.setRequestProperty("Cache-Control", "no-cache")
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val arr = json.optJSONArray("teams") ?: throw IllegalStateException("field \"teams\" tidak ada")
            val out = ArrayList<FeedTeam>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").trim()
                if (name.isEmpty()) continue
                val members = ArrayList<String>()
                o.optJSONArray("members")?.let { m -> for (k in 0 until m.length()) members.add(m.optString(k)) }
                out.add(FeedTeam(name, o.optInt("kills", 0), o.optInt("placement", 0), members))
            }
            return json.optInt("match", 1) to out
        } finally {
            conn.disconnect()
        }
    }

    /** Daftar poin posisi dari Pengaturan, mis. "12,9,8,7" → juara 1 = 12. */
    fun placementList(ctx: Context): List<Int> =
        Prefs.placementPoints(ctx).split(",").mapNotNull { it.trim().toIntOrNull() }

    fun pointsFor(ctx: Context, kills: Int, placement: Int): Int {
        val pl = placementList(ctx)
        val place = if (placement > 0) pl.getOrElse(placement - 1) { 0 } else 0
        return kills * Prefs.killPoint(ctx) + place
    }

    private fun apply(ctx: Context, match: Int, teams: List<FeedTeam>) {
        val before = signature()
        // simpan poin match ini (menggantikan snapshot lama untuk match yang sama)
        matchPoints[match] = HashMap<String, Int>().also { m ->
            teams.forEach { m[it.name.lowercase()] = pointsFor(ctx, it.kills, it.placement) }
        }

        // otomatis pilih mode kalau belum ada
        var mode = BracketStore.mode
        if (mode == null) {
            BracketStore.gameId = BracketStore.gameId ?: "other"
            mode = BracketStore.games[BracketStore.gameId]?.second?.firstOrNull { it.type == "br" }
            BracketStore.mode = mode
        }

        if (mode != null && mode.type == "br") {
            for (ft in teams) {
                var t = BracketStore.teams.firstOrNull { it.name.trim().equals(ft.name, true) }
                if (t == null) {
                    t = BracketStore.Team(ft.name)
                    BracketStore.teams.add(t)
                }
                if (ft.members.isNotEmpty() && t.members.isEmpty()) t.members = ft.members.toMutableList()
                t.score = matchPoints.values.sumOf { it[ft.name.lowercase()] ?: 0 }
            }
            BracketStore.size = BracketStore.teams.size
        } else if (mode != null && !autoFilled && teams.size >= 2) {
            // bracket eliminasi: isi slot dengan tim yang terdeteksi (sekali per koneksi)
            val names = teams.map { it.name }.take(BracketStore.size)
            BracketStore.replacePlayers((0 until BracketStore.size).map { names.getOrNull(it) ?: "Tim ${it + 1}" })
            BracketStore.resetMatches()
            autoFilled = true
        }

        val time = SimpleDateFormat("HH:mm:ss", Locale("id")).format(Date())
        feedLine = "match $match · ${teams.size} tim · $time"
        refreshStatus()
        fireListeners(signature() != before)
    }
}
