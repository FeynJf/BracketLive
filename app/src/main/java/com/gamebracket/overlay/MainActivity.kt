package com.gamebracket.overlay

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
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

class MainActivity : AppCompatActivity() {

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
    private lateinit var switchOverlay: SwitchCompat
    private lateinit var tvOverlayState: TextView
    private lateinit var dotOverlay: View

    private val gameButtons = linkedMapOf<String, TextView>()
    private val modeButtons = linkedMapOf<String, TextView>()

    private var suppressSwitch = false
    private var pendingOverlay = false

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
        switchOverlay = findViewById(R.id.switchOverlay)
        tvOverlayState = findViewById(R.id.tvOverlayState)
        dotOverlay = findViewById(R.id.dotOverlay)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 99)
        }

        setupOverlayToggle()
        setupToolbar()
        setupGames()
        findViewById<TextView>(R.id.btnReset).setOnClickListener { resetAll() }
        findViewById<TextView>(R.id.btnFill).setOnClickListener { fillSample() }
        findViewById<TextView>(R.id.btnShuffle).setOnClickListener { shuffle() }
        findViewById<TextView>(R.id.btnAdd).setOnClickListener { addTeam() }
        btnCert.setOnClickListener { makeCertificate() }

        updateSizeChips()
        updateBOChips()
        setOverlaySwitch(OverlayService.isRunning)

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
    }

    override fun onStart() {
        super.onStart()
        OverlayService.stateListener = { on -> runOnUiThread { setOverlaySwitch(on) } }
    }

    override fun onStop() {
        OverlayService.stateListener = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (pendingOverlay) {
            pendingOverlay = false
            if (Settings.canDrawOverlays(this)) startOverlay()
        } else {
            setOverlaySwitch(OverlayService.isRunning)
        }
        // Data bisa berubah dari overlay → segarkan tampilan
        refreshFromStore()
    }

    // ───────────────────────── Overlay toggle ─────────────────────────

    private fun setupOverlayToggle() {
        switchOverlay.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitch) return@setOnCheckedChangeListener
            if (checked) startOverlay() else stopOverlay()
        }
    }

    private fun setOverlaySwitch(on: Boolean) {
        suppressSwitch = true
        switchOverlay.isChecked = on
        suppressSwitch = false
        tvOverlayState.text = if (on) "Overlay Aktif" else "Overlay Nonaktif"
        tvOverlayState.setTextColor(col(if (on) R.color.green else R.color.muted))
        dotOverlay.setBackgroundResource(if (on) R.drawable.bg_dot_on else R.drawable.bg_dot_off)
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            setOverlaySwitch(false)
            AlertDialog.Builder(this)
                .setTitle("Izin Overlay")
                .setMessage("Aktifkan izin \"Tampil di atas aplikasi lain\" untuk Bracket Live, lalu kembali ke aplikasi ini.")
                .setPositiveButton("Buka Settings") { _, _ ->
                    pendingOverlay = true
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .setNegativeButton("Batal", null)
                .show()
            return
        }
        val i = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
        else startService(i)
        setOverlaySwitch(true)
        status("Overlay aktif — ketuk ikon B untuk membuka")
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        setOverlaySwitch(false)
        status("Overlay dimatikan")
    }

    // ───────────────────────── Setup ─────────────────────────

    private fun setupToolbar() {
        findViewById<TextView>(R.id.btnGame).setOnClickListener {
            panelGame.visibility = View.VISIBLE
            panelMode.visibility = View.GONE
        }
        findViewById<TextView>(R.id.btnMode).setOnClickListener {
            if (BracketStore.gameId == null) {
                status("Pilih game dulu"); return@setOnClickListener
            }
            panelMode.visibility = View.VISIBLE
            panelGame.visibility = View.GONE
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
        panelMode.visibility = View.GONE
        modeButtons.forEach { (k, v) -> styleChoice(v, k == mode.id) }
        if (mode.type == "br") {
            BracketStore.ensureTeams()
            teamList.visibility = View.VISIBLE
            bracketScroll.visibility = View.GONE
            renderTeams()
        } else {
            // bracket eliminasi harus pangkat 2
            if (Integer.bitCount(BracketStore.size) != 1) {
                BracketStore.size = 8
                updateSizeChips()
            }
            BracketStore.ensurePlayers()
            teamList.visibility = View.GONE
            bracketScroll.visibility = View.VISIBLE
            renderBracket()
        }
        champBox.visibility = View.VISIBLE
        updateChampion()
    }

    private fun refreshFromStore() {
        val m = BracketStore.mode ?: return
        if (m.type == "br") renderTeams() else renderBracket()
        updateChampion()
    }

    private fun setSize(n: Int) {
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
            styleChoice(findViewById(id), BracketStore.size == v)
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
    }

    private fun renderBracket() {
        bracketContainer.removeAllViews()
        val rounds = BracketStore.rounds()
        val cardW = dp(172)
        val baseSlot = dp(104)
        for (r in 0 until rounds) {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(5), 0, dp(5), 0)
            }
            val label = TextView(this).apply {
                text = BracketStore.roundName(r)
                setTextColor(col(R.color.cyan))
                textSize = 10f
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(8))
                layoutParams = LinearLayout.LayoutParams(cardW, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            column.addView(label)
            for (m in 0 until BracketStore.matchesIn(r)) {
                // slot makin tinggi tiap babak → kartu match tampil sejajar di tengah (bentuk bracket)
                val slot = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(cardW, baseSlot * (1 shl r))
                }
                val card = buildMatchView(r, m)
                card.layoutParams = FrameLayout.LayoutParams(
                    cardW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL
                )
                slot.addView(card)
                column.addView(slot)
            }
            bracketContainer.addView(column)
        }
        updateChampion()
    }

    private fun buildMatchView(r: Int, m: Int): View {
        val key = "r${r}m$m"
        val sc = BracketStore.series[key] ?: intArrayOf(0, 0)
        val winner = BracketStore.winners[key]
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_match)
        }
        if (BracketStore.boFormat > 1) {
            box.addView(TextView(this).apply {
                text = "BO${BracketStore.boFormat}"
                setTextColor(col(R.color.muted))
                textSize = 9f
                letterSpacing = 0.15f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(5), 0, 0)
            })
        }
        for (s in 0..1) {
            val name = BracketStore.getName(r, m, s)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(6), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(36)
                )
                if (winner == s) setBackgroundColor(Color.parseColor("#1A00E5FF"))
            }
            row.addView(TextView(this).apply {
                text = if (r == 0) "${m * 2 + s + 1}" else ""
                setTextColor(col(R.color.dim))
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(16), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            if (r == 0) {
                row.addView(EditText(this).apply {
                    setText(name)
                    setTextColor(col(if (winner != null && winner != s) R.color.dim else R.color.text))
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setBackgroundColor(Color.TRANSPARENT)
                    setPadding(0, 0, 0, 0)
                    minimumHeight = 0
                    minHeight = 0
                    setSingleLine()
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    addTextChangedListener(simpleWatcher { t ->
                        BracketStore.players[m * 2 + s] = t
                    })
                })
            } else {
                row.addView(TextView(this).apply {
                    text = name.ifBlank { "—" }
                    setTextColor(
                        col(
                            when {
                                winner == s -> R.color.cyan
                                winner != null -> R.color.dim
                                else -> R.color.text
                            }
                        )
                    )
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
            // Tombol skor: ketuk untuk +1 poin / menangkan match
            val badge = TextView(this).apply {
                text = "${sc[s]}"
                gravity = Gravity.CENTER
                setTextColor(if (winner == s) Color.WHITE else col(R.color.muted_light))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setBackgroundResource(if (winner == s) R.drawable.bg_btn_primary else R.drawable.bg_chip)
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(26))
                setOnClickListener { onSlotClick(r, m, s) }
            }
            row.addView(badge)
            if (r > 0) row.setOnClickListener { onSlotClick(r, m, s) }
            box.addView(row)
        }
        return box
    }

    private fun onSlotClick(r: Int, m: Int, s: Int) {
        val msg = BracketStore.registerWin(r, m, s) ?: return
        status(msg)
        renderBracket()
    }

    private fun updateChampion() {
        val ch = BracketStore.champion()
        if (ch != null) {
            tvChampion.text = ch
            btnCert.visibility = View.VISIBLE
        } else {
            tvChampion.text = "—"
            btnCert.visibility = View.GONE
        }
    }

    // ───────────────────────── Actions ─────────────────────────

    private fun resetAll() {
        BracketStore.resetMatches()
        BracketStore.teams.clear()
        BracketStore.players = MutableList(BracketStore.size) { "Tim ${it + 1}" }
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
            BracketStore.players = sample.take(BracketStore.size).toMutableList()
            while (BracketStore.players.size < BracketStore.size)
                BracketStore.players.add("Tim ${BracketStore.players.size + 1}")
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
            BracketStore.players.shuffle()
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

    private fun makeCertificate() {
        val name = BracketStore.champion() ?: return
        val bmp = Bitmap.createBitmap(1200, 850, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = Color.parseColor("#FAF7F0")
        c.drawRect(0f, 0f, 1200f, 850f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 18f
        p.color = Color.parseColor("#C9A227")
        c.drawRect(28f, 28f, 1172f, 822f, p)
        p.strokeWidth = 2.5f
        p.color = Color.parseColor("#8B7355")
        c.drawRect(52f, 52f, 1148f, 798f, p)

        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#5C4A1F")
        p.textAlign = Paint.Align.CENTER
        p.textSize = 42f
        p.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        c.drawText("SERTIFIKAT JUARA", 600f, 160f, p)

        p.textSize = 56f
        c.drawText("🏆", 600f, 240f, p)

        p.color = Color.parseColor("#555555")
        p.textSize = 22f
        p.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        c.drawText("Dengan ini menyatakan bahwa", 600f, 300f, p)

        p.color = Color.BLACK
        p.textSize = 52f
        p.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        c.drawText(name, 600f, 380f, p)

        val game = BracketStore.gameId?.let { BracketStore.games[it]?.first } ?: ""
        val mode = BracketStore.mode?.name ?: ""
        p.color = Color.parseColor("#444444")
        p.textSize = 20f
        p.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        c.drawText("Juara $game - $mode", 600f, 440f, p)

        val date = SimpleDateFormat("d MMMM yyyy", Locale("id")).format(Date())
        p.color = Color.parseColor("#777777")
        p.textSize = 18f
        c.drawText(date, 600f, 490f, p)

        p.color = Color.parseColor("#0D1728")
        p.textSize = 36f
        p.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        c.drawText("Panitia", 900f, 700f, p)
        p.strokeWidth = 2f
        p.style = Paint.Style.STROKE
        c.drawLine(760f, 720f, 1040f, 720f, p)

        p.color = Color.parseColor("#9A8F78")
        p.textSize = 16f
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        c.drawText("BRACKET LIVE", 600f, 790f, p)

        // save
        val fileName = "Sertifikat_${name.replace(" ", "_")}.png"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BracketLive")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(it, values, null, null)
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val folder = File(dir, "BracketLive").apply { mkdirs() }
                FileOutputStream(File(folder, fileName)).use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            Toast.makeText(this, "Sertifikat disimpan ke Galeri", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal simpan: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
