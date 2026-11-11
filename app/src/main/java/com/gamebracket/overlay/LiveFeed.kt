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
 * Koneksi live ke sumber data custom room.
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

    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        if (running) return
        running = true
        autoFilled = false
        matchPoints.clear()
        status = "Menghubungkan…"
        fireListeners(false)
        schedule(0)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        status = "Terputus"
        fireListeners(false)
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

    private fun schedule(delayMs: Long) {
        handler.postDelayed({ if (running) pollOnce() }, delayMs)
    }

    private fun pollOnce() {
        val ctx = appCtx ?: return
        val url = Prefs.feedUrl(ctx).trim()
        val interval = Prefs.feedInterval(ctx) * 1000L
        Thread {
            try {
                val (match, teams) = fetch(url)
                handler.post { if (running) apply(ctx, match, teams) }
            } catch (e: Exception) {
                handler.post {
                    if (running) {
                        status = "Gagal membaca feed: ${e.message}"
                        fireListeners(false)
                    }
                }
            }
            handler.post { if (running) schedule(interval) }
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
        status = "Terhubung · match $match · ${teams.size} tim · $time"
        fireListeners(signature() != before)
    }
}
