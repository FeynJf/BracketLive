package com.gamebracket.overlay

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.pm.PackageInfoCompat
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity() {

    private lateinit var tvGameTag: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvChampion: TextView
    private lateinit var btnCert: TextView
    private lateinit var panelGame: View
    private lateinit var panelMode: View
    private lateinit var teamList: LinearLayout
    private lateinit var bracketScroll: View
    private lateinit var bracketContainer: LinearLayout
    private lateinit var champBox: View
    private lateinit var gridGames: GridLayout
    private lateinit var gridModes: GridLayout
    private lateinit var emptyState: View

    private val gameButtons = linkedMapOf<String, TextView>()
    private val modeButtons = linkedMapOf<String, TextView>()

    private val updater by lazy { UpdateFlow(this) { status(it) } }

    private var pickTarget: ((String) -> Unit)? = null

    private val logoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val path = LogoHelper.saveFromUri(this, uri)
            if (path != null) pickTarget?.invoke(path) else status("Gambar tidak bisa dibaca")
        }
    }

    private var lastChampion: String? = null

    companion object {
        private var updateChecked = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvGameTag = findViewById(R.id.tvGameTag)
        tvStatus = findViewById(R.id.tvStatus)
        tvChampion = findViewById(R.id.tvChampion)
        btnCert = findViewById(R.id.btnCert)
        panelGame = findViewById(R.id.panelGame)
        panelMode = findViewById(R.id.panelMode)
        teamList = findViewById(R.id.teamList)
        bracketScroll = findViewById(R.id.bracketScroll)
        bracketContainer = findViewById(R.id.bracketContainer)
        champBox = findViewById(R.id.champBox)
        gridGames = findViewById(R.id.gridGames)
        gridModes = findViewById(R.id.gridModes)
        emptyState = findViewById(R.id.emptyState)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 99)
        }

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        // judul "LIVE" dengan gradien neon
        val live = findViewById<TextView>(R.id.tvTitleLive)
        live.post {
            val w = live.paint.measureText(live.text.toString())
            live.paint.shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(ThemeManager.accent1(this), ThemeManager.accent2(this)), null, Shader.TileMode.CLAMP
            )
            live.invalidate()
        }
        setupToolbar()
        setupGames()
        findViewById<TextView>(R.id.btnReset).setOnClickListener { resetAll() }
        findViewById<TextView>(R.id.btnFill).setOnClickListener { fillSample() }
        findViewById<TextView>(R.id.btnShuffle).setOnClickListener { shuffle() }
        findViewById<TextView>(R.id.btnAdd).setOnClickListener { addTeam() }
        btnCert.setOnClickListener { startActivity(Intent(this, CertificateActivity::class.java)) }

        updateSizeChips()
        updateBOChips()
        // Pulihkan tampilan kalau activity dibuat ulang (mis. rotasi) tapi data masih ada
        val gid = BracketStore.gameId
        val md = BracketStore.mode
        if (gid != null && md != null) {
            tvGameTag.text = BracketStore.games[gid]?.first ?: gid
            panelGame.visibility = View.GONE
            gameButtons.forEach { (k, v) -> styleChoice(v, k == gid) }
            buildModes(gid)
            applyModeUi(md)
            status("${md.name} · BO${BracketStore.boFormat}")
        } else {
            status("Pilih game untuk mulai")
        }

        if (!updateChecked) {
            updateChecked = true
            updater.check(false)
        }
        if (Prefs.feedEnabled(this) && !LiveFeed.running) LiveFeed.start(this)

        // animasi masuk + efek tekan
        Anim.enterChildren(findViewById<ViewGroup>(R.id.contentContainer), 120, 70)
        Anim.pressAll(window.decorView)
    }

    override fun onStart() {
        super.onStart()
        LiveFeed.setListener("main") { changed -> runOnUiThread { onFeedUpdate(changed) } }
    }

    override fun onStop() {
        LiveFeed.setListener("main", null)
        super.onStop()
    }

    /** Data dari Live Connect masuk: tim terdeteksi & poin diperbarui otomatis. */
    private fun onFeedUpdate(changed: Boolean) {
        if (LiveFeed.running) status(LiveFeed.status)
        if (!changed) return
        // jangan menggambar ulang saat pengguna sedang mengetik
        if (currentFocus is EditText && emptyState.visibility != View.VISIBLE) return
        val m = BracketStore.mode
        if (m != null && emptyState.visibility == View.VISIBLE) {
            val gid = BracketStore.gameId
            if (gid != null) {
                tvGameTag.text = BracketStore.games[gid]?.first ?: gid
                gameButtons.forEach { (k, v) -> styleChoice(v, k == gid) }
                buildModes(gid)
            }
            applyModeUi(m)
        } else {
            refreshFromStore()
        }
    }

    override fun onResume() {
        super.onResume()
        updater.onResume()
        // "Langsung semifinal" diaktifkan di Pengaturan → bracket menyesuaikan jadi 4 tim
        val cur = BracketStore.mode
        if (cur != null && cur.type != "br" && Prefs.semifinalOnly(this) && BracketStore.size != 4) {
            BracketStore.size = 4
            BracketStore.resetMatches()
            BracketStore.ensurePlayers()
            applyModeUi(cur)
            return
        }
        updateSizeChips()
        // Data bisa berubah dari overlay → segarkan tampilan
        refreshFromStore()
    }

    // ───────────────────────── Setup ─────────────────────────

    private fun setupToolbar() {
        findViewById<TextView>(R.id.btnGame).setOnClickListener {
            panelGame.visibility = View.VISIBLE
            panelMode.visibility = View.GONE
            Anim.reveal(panelGame)
        }
        findViewById<TextView>(R.id.btnMode).setOnClickListener {
            if (BracketStore.gameId == null) {
                status("Pilih game dulu"); return@setOnClickListener
            }
            panelMode.visibility = View.VISIBLE
            panelGame.visibility = View.GONE
            Anim.reveal(panelMode)
        }
        findViewById<TextView>(R.id.btnSize4).setOnClickListener { setSize(4) }
        findViewById<TextView>(R.id.btnSize8).setOnClickListener { setSize(8) }
        findViewById<TextView>(R.id.btnSize16).setOnClickListener { setSize(16) }
        findViewById<TextView>(R.id.btnBO1).setOnClickListener { setBO(1) }
        findViewById<TextView>(R.id.btnBO3).setOnClickListener { setBO(3) }
        findViewById<TextView>(R.id.btnBO5).setOnClickListener { setBO(5) }
    }

    private fun choiceButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(14), dp(8), dp(14))
        setBackgroundResource(R.drawable.bg_chip)
        setTextColor(col(R.color.muted_light))
        layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
        setOnClickListener { onClick() }
        Anim.pressable(this)
    }

    private fun styleChoice(tv: TextView, active: Boolean) {
        tv.setBackgroundResource(if (active) R.drawable.bg_chip_active else R.drawable.bg_chip)
        tv.setTextColor(col(if (active) R.color.on_accent else R.color.muted_light))
    }

    private fun setupGames() {
        gridGames.removeAllViews()
        gameButtons.clear()
        BracketStore.games.forEach { (id, pair) ->
            val btn = choiceButton(pair.first) { selectGame(id) }
            gameButtons[id] = btn
            gridGames.addView(btn)
        }
        BracketStore.gameId?.let { gid -> gameButtons.forEach { (k, v) -> styleChoice(v, k == gid) } }
    }

    private fun buildModes(id: String) {
        gridModes.removeAllViews()
        modeButtons.clear()
        BracketStore.games[id]?.second?.forEach { mode ->
            val btn = choiceButton(mode.name) { selectMode(mode) }
            modeButtons[mode.id] = btn
            gridModes.addView(btn)
        }
    }

    private fun selectGame(id: String) {
        BracketStore.gameId = id
        val name = BracketStore.games[id]?.first ?: id
        tvGameTag.text = name
        panelGame.visibility = View.GONE
        gameButtons.forEach { (k, v) -> styleChoice(v, k == id) }
        buildModes(id)
        panelMode.visibility = View.VISIBLE
        Anim.reveal(panelMode)
        // otomatis pilih mode pertama
        BracketStore.games[id]?.second?.firstOrNull()?.let { selectMode(it) }
        status("Game: $name")
    }

    private fun selectMode(mode: BracketStore.Mode) {
        BracketStore.mode = mode
        BracketStore.resetMatches()
        applyModeUi(mode)
        status("${mode.name} · BO${BracketStore.boFormat}")
    }

    private fun applyModeUi(mode: BracketStore.Mode) {
        emptyState.visibility = View.GONE
        panelMode.visibility = View.GONE
        modeButtons.forEach { (k, v) -> styleChoice(v, k == mode.id) }
        if (mode.type == "br") {
            BracketStore.ensureTeams()
            teamList.visibility = View.VISIBLE
            bracketScroll.visibility = View.GONE
            renderTeams()
        } else {
            // bracket eliminasi harus pangkat 2; mode "langsung semifinal" (Pengaturan) = 4 tim
            if (Prefs.semifinalOnly(this)) {
                BracketStore.size = 4
            } else if (Integer.bitCount(BracketStore.size) != 1) {
                BracketStore.size = 8
            }
            updateSizeChips()
            BracketStore.ensurePlayers()
            teamList.visibility = View.GONE
            bracketScroll.visibility = View.VISIBLE
            renderBracket()
        }
        champBox.visibility = View.VISIBLE
        updateChampion()
        Anim.reveal(if (mode.type == "br") teamList else bracketScroll)
    }

    private fun refreshFromStore() {
        val m = BracketStore.mode ?: return
        if (emptyState.visibility == View.VISIBLE) { applyModeUi(m); return }
        if (m.type == "br") renderTeams() else renderBracket()
        updateChampion()
    }

    private fun setSize(n: Int) {
        if (Prefs.semifinalOnly(this) && BracketStore.mode?.type != "br") {
            status("Mode langsung semifinal aktif (ubah di Pengaturan → Turnamen)")
            return
        }
        BracketStore.size = n
        BracketStore.resetMatches()
        updateSizeChips()
        BracketStore.mode?.let { selectMode(it) } ?: status("Ukuran $n")
    }

    private fun setBO(n: Int) {
        BracketStore.boFormat = n
        BracketStore.resetMatches()
        updateBOChips()
        if (BracketStore.mode?.type == "bracket") renderBracket()
        status("Format BO$n (butuh ${BracketStore.winsNeeded()} map)")
    }

    private fun updateSizeChips() {
        listOf(4 to R.id.btnSize4, 8 to R.id.btnSize8, 16 to R.id.btnSize16).forEach { (v, id) ->
            val chip = findViewById<TextView>(id)
            styleChoice(chip, BracketStore.size == v)
            val locked = Prefs.semifinalOnly(this) && BracketStore.mode?.type != "br" && v != 4
            chip.alpha = if (locked) 0.35f else 1f
        }
    }

    private fun updateBOChips() {
        listOf(1 to R.id.btnBO1, 3 to R.id.btnBO3, 5 to R.id.btnBO5).forEach { (v, id) ->
            styleChoice(findViewById(id), BracketStore.boFormat == v)
        }
    }

    // ───────────────────────── Render ─────────────────────────

    private fun renderTeams() {
        teamList.removeAllViews()
        val sorted = BracketStore.teams.mapIndexed { i, t -> i to t }.sortedByDescending { it.second.score }
        sorted.forEachIndexed { rank, (idx, team) ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_team_row, teamList, false)
            val tvRank = row.findViewById<TextView>(R.id.tvRank)
            val etName = row.findViewById<EditText>(R.id.etName)
            val etScore = row.findViewById<EditText>(R.id.etScore)
            val btnDel = row.findViewById<TextView>(R.id.btnDel)
            val ivLogo = row.findViewById<ImageView>(R.id.ivLogo)
            val btnRoster = row.findViewById<TextView>(R.id.btnRoster)
            val rosterSize = BracketStore.mode?.members ?: 0
            if (rosterSize > 0) {
                btnRoster.visibility = View.VISIBLE
                btnRoster.text = "👥 ${team.members.count { it.isNotBlank() }}/$rosterSize"
                btnRoster.setOnClickListener {
                    RosterDialog.show(this, "Anggota ${team.name}", rosterSize, team.members) { list ->
                        BracketStore.teams[idx].members = list.toMutableList()
                        renderTeams()
                    }
                }
            }
            ivLogo.setImageDrawable(LogoHelper.drawable(this, team.name, team.logo, dp(36)))
            ivLogo.setOnClickListener {
                pickLogo { path ->
                    BracketStore.teams[idx].logo = path
                    renderTeams()
                }
            }
            tvRank.text = "${rank + 1}"
            tvRank.setTextColor(
                col(
                    when (rank) {
                        0 -> R.color.gold
                        1 -> R.color.silver
                        2 -> R.color.bronze
                        else -> R.color.muted
                    }
                )
            )
            etName.setText(team.name)
            etScore.setText(team.score.toString())
            etName.addTextChangedListener(simpleWatcher {
                BracketStore.teams[idx].name = it
                updateChampion()
            })
            etScore.addTextChangedListener(simpleWatcher {
                BracketStore.teams[idx].score = it.toIntOrNull() ?: 0
                updateChampion()
            })
            btnDel.setOnClickListener {
                if (BracketStore.teams.size > 2) {
                    BracketStore.teams.removeAt(idx)
                    BracketStore.size = BracketStore.teams.size
                    renderTeams()
                    updateChampion()
                }
            }
            teamList.addView(row)
        }
        Anim.pressAll(teamList)
    }

    private fun renderBracket() {
        val cfg = BracketUi.Config(cardWidthDp = 178, rowHeightDp = 38, textSp = 12.5f, logoDp = 26, connectorDp = 24)
        BracketUi.build(
            this, bracketContainer, cfg,
            pickLogo = { slot ->
                pickLogo { path ->
                    BracketStore.ensurePlayers()
                    BracketStore.playerLogos[slot] = path
                    renderBracket()
                }
            },
            onWin = { msg ->
                msg?.let { status(it) }
                renderBracket()
            },
            rosterSlot = if ((BracketStore.mode?.members ?: 0) > 0) { slot ->
                val size = BracketStore.mode?.members ?: 0
                BracketStore.ensurePlayers()
                RosterDialog.show(this, "Anggota ${BracketStore.players[slot]}", size, BracketStore.playerMembers[slot]) { list ->
                    BracketStore.playerMembers[slot] = list.toMutableList()
                    status("Roster ${BracketStore.players[slot]} disimpan (${list.count { it.isNotBlank() }}/$size)")
                }
            } else null
        )
        updateChampion()
    }

    private fun pickLogo(setter: (String) -> Unit) {
        pickTarget = setter
        logoPicker.launch("image/*")
    }

    private fun updateChampion() {
        val ch = BracketStore.champion()
        if (ch != null) {
            tvChampion.text = ch
            btnCert.visibility = View.VISIBLE
            if (ch != lastChampion && champBox.visibility == View.VISIBLE) {
                Anim.pop(tvChampion)
                ConfettiView.play(this)
            }
        } else {
            tvChampion.text = "—"
            btnCert.visibility = View.GONE
        }
        lastChampion = ch
    }

    // ───────────────────────── Actions ─────────────────────────

    private fun resetAll() {
        BracketStore.resetMatches()
        BracketStore.teams.clear()
        BracketStore.replacePlayers(List(BracketStore.size) { "Tim ${it + 1}" })
        BracketStore.ensureTeams()
        BracketStore.mode?.let { selectMode(it) }
        status("Reset")
    }

    private fun fillSample() {
        val sample = listOf("EVOS", "RRQ", "ONIC", "AURA", "GEEK", "BTR", "DEWA", "ALTER")
        if (BracketStore.mode?.type == "br") {
            BracketStore.teams = sample.take(BracketStore.size).map {
                BracketStore.Team(it, (0..30).random())
            }.toMutableList()
            while (BracketStore.teams.size < BracketStore.size)
                BracketStore.teams.add(BracketStore.Team("Tim ${BracketStore.teams.size + 1}"))
            renderTeams()
        } else {
            BracketStore.replacePlayers((0 until BracketStore.size).map { sample.getOrNull(it) ?: "Tim ${it + 1}" })
            BracketStore.resetMatches()
            renderBracket()
        }
        updateChampion()
        status("Data contoh dimuat")
    }

    private fun shuffle() {
        if (BracketStore.mode?.type == "br") {
            BracketStore.teams.shuffle()
            renderTeams()
        } else {
            BracketStore.shufflePlayers()
            BracketStore.resetMatches()
            renderBracket()
        }
        status("Diacak")
    }

    private fun addTeam() {
        if (BracketStore.mode?.type == "br") {
            BracketStore.teams.add(BracketStore.Team("Tim ${BracketStore.teams.size + 1}"))
            BracketStore.size = BracketStore.teams.size
            renderTeams()
        } else status("Untuk bracket, ganti jumlah slot 4/8/16")
    }

    private fun status(msg: String) { tvStatus.text = msg }

    private fun col(id: Int) = ContextCompat.getColor(this, id)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun simpleWatcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
    }
}
