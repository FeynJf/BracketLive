package com.gamebracket.overlay

/**
 * Shared tournament state — native, no HTML.
 */
object BracketStore {
    data class Team(var name: String, var score: Int = 0)
    data class Mode(val id: String, val name: String, val type: String) // br | bracket

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

    var gameId: String? = null
    var mode: Mode? = null
    var size: Int = 8
    var boFormat: Int = 3 // BO1 / BO3 / BO5
    var teams: MutableList<Team> = mutableListOf()
    /** players for bracket slots */
    var players: MutableList<String> = MutableList(8) { "Tim ${it + 1}" }
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

    fun ensureTeams() {
        while (teams.size < size) teams.add(Team("Tim ${teams.size + 1}"))
        while (teams.size > size) teams.removeAt(teams.lastIndex)
    }

    fun ensurePlayers() {
        while (players.size < size) players.add("Tim ${players.size + 1}")
        while (players.size > size) players.removeAt(players.lastIndex)
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
