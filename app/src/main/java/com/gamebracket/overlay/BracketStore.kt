package com.gamebracket.overlay

/**
 * Shared tournament state — native, no HTML.
 */
object BracketStore {
    data class Team(var name: String, var score: Int = 0, var logo: String? = null, var members: MutableList<String> = mutableListOf())
    /** type: br (poin/ranking) | bracket (eliminasi). members > 0 = tim punya roster anggota (mis. 8). */
    data class Mode(val id: String, val name: String, val type: String, val members: Int = 0)

    val games = linkedMapOf(
        "freefire" to ("Free Fire" to listOf(
            Mode("clash", "Clash Squad (Eliminasi)", "bracket"),
            Mode("bomb", "Bomb Squad (Eliminasi)", "bracket"),
            Mode("lone", "Lone Wolf (Eliminasi)", "bracket"),
            Mode("br", "Battle Royale (Ranking)", "br")
        )),
        "pubg" to ("PUBG Mobile" to listOf(
            Mode("tdm", "TDM (Eliminasi)", "bracket"),
            Mode("br", "Battle Royale (Ranking)", "br")
        )),
        "mlbb" to ("Mobile Legends" to listOf(
            Mode("5v5", "5v5 (Eliminasi)", "bracket"),
            Mode("brawl", "Brawl (Eliminasi)", "bracket")
        )),
        "codm" to ("COD Mobile" to listOf(
            Mode("mp", "Multiplayer (Eliminasi)", "bracket"),
            Mode("br", "Battle Royale (Ranking)", "br")
        )),
        "valorant" to ("Valorant" to listOf(
            Mode("std", "Standard (Eliminasi)", "bracket")
        )),
        "other" to ("Lainnya" to listOf(
            Mode("elim", "Eliminasi", "bracket"),
            Mode("br", "Ranking", "br")
        ))
    )

    init {
        // Mode 8 anggota: CRF System (sistem poin) & Eliminasi (bracket)
        val extra = listOf(
            Mode("crf", "CRF System (8 Anggota)", "br", 8),
            Mode("elim8", "Eliminasi (8 Anggota)", "bracket", 8)
        )
        for ((id, pair) in games.entries.toList()) games[id] = pair.first to (pair.second + extra)
    }

    var gameId: String? = null
    var mode: Mode? = null
    var size: Int = 8
    var boFormat: Int = 3 // BO1 / BO3 / BO5
    var teams: MutableList<Team> = mutableListOf()
    /** players for bracket slots */
    var players: MutableList<String> = MutableList(8) { "Tim ${it + 1}" }
    /** path logo untuk tiap slot bracket (sejajar dengan [players]) */
    var playerLogos: MutableList<String?> = MutableList(8) { null }
    /** roster anggota tiap slot bracket (sejajar dengan [players]) */
    var playerMembers: MutableList<MutableList<String>> = MutableList(8) { mutableListOf() }
    /** matchKey "r{r}m{m}" -> winner slot 0/1 */
    var winners: MutableMap<String, Int> = mutableMapOf()
    /** series scores */
    var series: MutableMap<String, IntArray> = mutableMapOf()

    fun winsNeeded() = (boFormat + 1) / 2

    fun rounds() = Integer.numberOfTrailingZeros(size) // size must be power of 2

    fun matchesIn(r: Int) = size / (1 shl (r + 1))

    fun getName(r: Int, m: Int, s: Int): String {
        if (r == 0) return players.getOrNull(m * 2 + s) ?: ""
        val prev = winners["r${r - 1}m${m * 2 + s}"] ?: return ""
        return getName(r - 1, m * 2 + s, prev)
    }

    fun getLogo(r: Int, m: Int, s: Int): String? {
        if (r == 0) return playerLogos.getOrNull(m * 2 + s)
        val prev = winners["r${r - 1}m${m * 2 + s}"] ?: return null
        return getLogo(r - 1, m * 2 + s, prev)
    }

    /** Acak slot bracket (nama + logo ikut berpindah bersama). */
    fun shufflePlayers() {
        ensurePlayers()
        val order = players.indices.shuffled()
        val np = order.map { players[it] }
        val nl = order.map { playerLogos[it] }
        val nm = order.map { playerMembers[it] }
        players = np.toMutableList()
        playerLogos = nl.toMutableList()
        playerMembers = nm.toMutableList()
    }

    fun replacePlayers(names: List<String>) {
        players = names.toMutableList()
        playerLogos = MutableList(players.size) { null }
        playerMembers = MutableList(players.size) { mutableListOf() }
        ensurePlayers()
    }

    fun championLogo(): String? {
        val m = mode ?: return null
        return if (m.type == "br") {
            teams.maxByOrNull { it.score }?.takeIf { it.score > 0 }?.logo
        } else {
            val total = rounds()
            if (total == 0) return null
            val w = winners["r${total - 1}m0"] ?: return null
            getLogo(total - 1, 0, w)
        }
    }

    fun ensureTeams() {
        while (teams.size < size) teams.add(Team("Tim ${teams.size + 1}"))
        while (teams.size > size) teams.removeAt(teams.lastIndex)
    }

    fun ensurePlayers() {
        while (players.size < size) players.add("Tim ${players.size + 1}")
        while (players.size > size) players.removeAt(players.lastIndex)
        while (playerLogos.size < players.size) playerLogos.add(null)
        while (playerLogos.size > players.size) playerLogos.removeAt(playerLogos.lastIndex)
        while (playerMembers.size < players.size) playerMembers.add(mutableListOf())
        while (playerMembers.size > players.size) playerMembers.removeAt(playerMembers.lastIndex)
    }

    /** Nama babak: ROUND OF 16 / QUARTERFINAL / SEMIFINAL / FINAL */
    fun roundName(r: Int): String {
        val total = rounds()
        return when (total - 1 - r) {
            0 -> "FINAL"
            1 -> "SEMIFINAL"
            2 -> "QUARTERFINAL"
            else -> "ROUND OF ${1 shl (total - r)}"
        }
    }

    /** Hapus hasil babak-babak setelah match (fromR, fromM) karena hasilnya berubah. */
    fun clearDown(fromR: Int, fromM: Int) {
        val total = rounds()
        for (r in fromR + 1 until total) {
            val mk = "r${r}m${fromM / (1 shl (r - fromR))}"
            winners.remove(mk)
            series.remove(mk)
        }
    }

    /**
     * Tambah 1 poin/map untuk slot [s] di match (r, m). Kalau match sudah ada pemenang,
     * hasilnya direset. Mengembalikan pesan status, atau null kalau klik diabaikan.
     */
    fun registerWin(r: Int, m: Int, s: Int): String? {
        val key = "r${r}m$m"
        val n0 = getName(r, m, 0)
        val n1 = getName(r, m, 1)
        if (n0.isBlank() && n1.isBlank()) return null
        if (r > 0 && (n0.isBlank() || n1.isBlank())) return null

        if (winners[key] != null) {
            winners.remove(key)
            series[key] = intArrayOf(0, 0)
            clearDown(r, m)
            return "Hasil match direset"
        }
        val sc = series.getOrPut(key) { intArrayOf(0, 0) }
        val need = winsNeeded()
        if (sc[s] < need) sc[s] = sc[s] + 1
        return if (sc[s] >= need) {
            winners[key] = s
            clearDown(r, m)
            "${if (s == 0) n0 else n1} menang ${sc[0]}-${sc[1]}"
        } else {
            "Series ${sc[0]}-${sc[1]} (butuh $need)"
        }
    }

    fun resetMatches() {
        winners.clear()
        series.clear()
    }

    fun champion(): String? {
        val m = mode ?: return null
        return if (m.type == "br") {
            teams.maxByOrNull { it.score }?.takeIf { it.score > 0 }?.name
        } else {
            val R = rounds()
            if (R == 0) return null
            val w = winners["r${R - 1}m0"] ?: return null
            getName(R - 1, 0, w).ifBlank { null }
        }
    }

    fun summaryText(): String {
        val g = gameId?.let { games[it]?.first } ?: "—"
        val md = mode?.name ?: "—"
        val ch = champion() ?: "—"
        return "$g\n$md\nSize $size · BO$boFormat\nJuara: $ch"
    }
}
