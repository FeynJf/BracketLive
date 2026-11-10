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
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

    private val chipIds = listOf(
        R.id.btnSize4, R.id.btnSize8, R.id.btnSize16,
        R.id.btnBO1, R.id.btnBO3, R.id.btnBO5
    )

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

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 99)
        }

        setupToolbar()
        setupGames()
        findViewById<TextView>(R.id.btnReset).setOnClickListener { resetAll() }
        findViewById<TextView>(R.id.btnFill).setOnClickListener { fillSample() }
        findViewById<TextView>(R.id.btnShuffle).setOnClickListener { shuffle() }
        findViewById<TextView>(R.id.btnAdd).setOnClickListener { addTeam() }
        btnCert.setOnClickListener { makeCertificate() }

        status("Pilih game untuk mulai — native Android")
    }

    private fun setupToolbar() {
        findViewById<TextView>(R.id.btnOverlay).setOnClickListener { handleOverlay() }
        findViewById<TextView>(R.id.btnGame).setOnClickListener {
            panelGame.visibility = View.VISIBLE
            panelMode.visibility = View.GONE
        }
        findViewById<TextView>(R.id.btnMode).setOnClickListener {
            if (BracketStore.gameId == null) {
                status("Pilih game dulu"); return@setOnClickListener
            }
            panelMode.visibility = View.VISIBLE
        }
        findViewById<TextView>(R.id.btnSize4).setOnClickListener { setSize(4) }
        findViewById<TextView>(R.id.btnSize8).setOnClickListener { setSize(8) }
        findViewById<TextView>(R.id.btnSize16).setOnClickListener { setSize(16) }
        findViewById<TextView>(R.id.btnBO1).setOnClickListener { setBO(1) }
        findViewById<TextView>(R.id.btnBO3).setOnClickListener { setBO(3) }
        findViewById<TextView>(R.id.btnBO5).setOnClickListener { setBO(5) }
    }

    private fun setupGames() {
        gridGames.removeAllViews()
        BracketStore.games.forEach { (id, pair) ->
            val btn = TextView(this)
            btn.text = pair.first
            btn.setTextColor(Color.parseColor("#B0B0C4"))
            btn.textSize = 12f
            btn.setTypeface(null, Typeface.BOLD)
            btn.setPadding(16, 28, 16, 28)
            btn.setBackgroundResource(R.drawable.bg_chip)
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(6, 6, 6, 6)
            }
            btn.layoutParams = lp
            btn.gravity = Gravity.CENTER
            btn.setOnClickListener { selectGame(id) }
            gridGames.addView(btn)
        }
    }

    private fun selectGame(id: String) {
        BracketStore.gameId = id
        val name = BracketStore.games[id]?.first ?: id
        tvGameTag.text = name
        panelGame.visibility = View.GONE
        // modes
        gridModes.removeAllViews()
        BracketStore.games[id]?.second?.forEach { mode ->
            val btn = TextView(this)
            btn.text = mode.name
            btn.setTextColor(Color.parseColor("#B0B0C4"))
            btn.textSize = 12f
            btn.setTypeface(null, Typeface.BOLD)
            btn.setPadding(12, 28, 12, 28)
            btn.setBackgroundResource(R.drawable.bg_chip)
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(6, 6, 6, 6)
            }
            btn.layoutParams = lp
            btn.gravity = Gravity.CENTER
            btn.setOnClickListener { selectMode(mode) }
            gridModes.addView(btn)
        }
        panelMode.visibility = View.VISIBLE
        // auto first mode
        BracketStore.games[id]?.second?.firstOrNull()?.let { selectMode(it) }
        status("Game: $name")
    }

    private fun selectMode(mode: BracketStore.Mode) {
        BracketStore.mode = mode
        BracketStore.resetMatches()
        panelMode.visibility = View.GONE
        if (mode.type == "br") {
            BracketStore.ensureTeams()
            teamList.visibility = View.VISIBLE
            bracketScroll.visibility = View.GONE
            renderTeams()
        } else {
            BracketStore.ensurePlayers()
            teamList.visibility = View.GONE
            bracketScroll.visibility = View.VISIBLE
            renderBracket()
        }
        champBox.visibility = View.VISIBLE
        updateChampion()
        status("${mode.name} · BO${BracketStore.boFormat}")
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
            val tv = findViewById<TextView>(id)
            tv.setBackgroundResource(if (BracketStore.size == v) R.drawable.bg_pill_pink else R.drawable.bg_chip)
            tv.setTextColor(if (BracketStore.size == v) Color.WHITE else Color.parseColor("#7A7A90"))
        }
    }

    private fun updateBOChips() {
        listOf(1 to R.id.btnBO1, 3 to R.id.btnBO3, 5 to R.id.btnBO5).forEach { (v, id) ->
            val tv = findViewById<TextView>(id)
            tv.setBackgroundResource(if (BracketStore.boFormat == v) R.drawable.bg_pill_pink else R.drawable.bg_chip)
            tv.setTextColor(if (BracketStore.boFormat == v) Color.WHITE else Color.parseColor("#7A7A90"))
        }
    }

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
            etName.setText(team.name)
            etScore.setText(team.score.toString())
            etName.addTextChangedListener(simpleWatcher {
                BracketStore.teams[idx].name = it
                updateChampion()
            })
            etScore.addTextChangedListener(simpleWatcher {
                BracketStore.teams[idx].score = it.toIntOrNull() ?: 0
                // re-render ranks after pause would be heavy; update champ only
                updateChampion()
            })
            btnDel.setOnClickListener {
                if (BracketStore.teams.size > 2) {
                    BracketStore.teams.removeAt(idx)
                    BracketStore.size = BracketStore.teams.size
                    renderTeams()
                }
            }
            teamList.addView(row)
        }
    }

    private fun renderBracket() {
        bracketContainer.removeAllViews()
        val R = BracketStore.rounds()
        for (r in 0 until R) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 0, 8, 0)
            }
            val label = TextView(this).apply {
                text = "MATCH ${r + 1}"
                setTextColor(Color.parseColor("#55556A"))
                textSize = 10f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
            }
            col.addView(label)
            val M = BracketStore.matchesIn(r)
            for (m in 0 until M) {
                col.addView(buildMatchView(r, m))
            }
            bracketContainer.addView(col)
        }
        updateChampion()
    }

    private fun buildMatchView(r: Int, m: Int): View {
        val key = "r${r}m$m"
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_match)
            setPadding(0, 0, 0, 0)
            val lp = LinearLayout.LayoutParams(dp(160), LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
        }
        if (BracketStore.boFormat > 1) {
            val sc = BracketStore.series[key] ?: intArrayOf(0, 0)
            val bo = TextView(this).apply {
                text = "BO${BracketStore.boFormat}  ${sc[0]}-${sc[1]}"
                setTextColor(Color.parseColor("#55556A"))
                textSize = 9f
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            }
            box.addView(bo)
        }
        for (s in 0..1) {
            val name = BracketStore.getName(r, m, s)
            val winner = BracketStore.winners[key]
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                if (winner == s) setBackgroundColor(Color.parseColor("#1820E3D0"))
            }
            val seed = TextView(this).apply {
                text = if (r == 0) "${m * 2 + s + 1}" else ""
                setTextColor(Color.parseColor("#3A3A48"))
                textSize = 10f
                width = dp(16)
            }
            row.addView(seed)
            if (r == 0) {
                val et = EditText(this).apply {
                    setText(name)
                    setTextColor(Color.parseColor("#D0D0E0"))
                    textSize = 12f
                    setBackgroundColor(Color.TRANSPARENT)
                    setSingleLine()
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addTextChangedListener(simpleWatcher { t ->
                        BracketStore.players[m * 2 + s] = t
                    })
                }
                row.addView(et)
            } else {
                val tv = TextView(this).apply {
                    text = name.ifBlank { "—" }
                    setTextColor(
                        when {
                            winner == s -> Color.parseColor("#1AD9C8")
                            winner != null -> Color.parseColor("#2A2A36")
                            else -> Color.parseColor("#D0D0E0")
                        }
                    )
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(tv)
            }
            val sc = BracketStore.series[key] ?: intArrayOf(0, 0)
            if (BracketStore.boFormat > 1 || sc[0] + sc[1] > 0) {
                val badge = TextView(this).apply {
                    text = "${sc[s]}"
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                    setBackgroundResource(R.drawable.bg_pill_pink)
                }
                row.addView(badge)
            }
            row.setOnClickListener { onSlotClick(r, m, s) }
            box.addView(row)
        }
        return box
    }

    private fun onSlotClick(r: Int, m: Int, s: Int) {
        val key = "r${r}m$m"
        val n0 = BracketStore.getName(r, m, 0)
        val n1 = BracketStore.getName(r, m, 1)
        if (n0.isBlank() && n1.isBlank()) return
        if (r > 0 && (n0.isBlank() || n1.isBlank())) return

        if (BracketStore.winners[key] != null) {
            BracketStore.winners.remove(key)
            BracketStore.series[key] = intArrayOf(0, 0)
            clearDown(r, m)
            renderBracket()
            return
        }
        val sc = BracketStore.series.getOrPut(key) { intArrayOf(0, 0) }
        val need = BracketStore.winsNeeded()
        if (sc[s] < need) sc[s] = sc[s] + 1
        if (sc[s] >= need) {
            BracketStore.winners[key] = s
            clearDown(r, m)
            status("${if (s == 0) n0 else n1} menang ${sc[0]}-${sc[1]}")
        } else {
            status("Series ${sc[0]}-${sc[1]} (butuh $need)")
        }
        renderBracket()
    }

    private fun clearDown(fromR: Int, fromM: Int) {
        val R = BracketStore.rounds()
        for (r in fromR + 1 until R) {
            val mk = "r${r}m${fromM / (1 shl (r - fromR))}"
            BracketStore.winners.remove(mk)
            BracketStore.series.remove(mk)
        }
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
        } else status("Untuk bracket, ganti ukuran 4/8/16")
    }

    private fun handleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Izin Overlay")
                .setMessage("Aktifkan izin tampil di atas aplikasi.\n\nCari \"Game Bracket\" → Diizinkan")
                .setPositiveButton("Buka Settings") { _, _ ->
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
        Toast.makeText(this, "Overlay dinyalakan", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
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

        // save
        val fileName = "Sertifikat_${name.replace(" ", "_")}.png"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GameBracket")
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
                val folder = File(dir, "GameBracket").apply { mkdirs() }
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun simpleWatcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
    }
}
