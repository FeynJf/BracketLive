package com.gamebracket.overlay

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/** Pengaturan: tampilan, overlay, turnamen, koneksi live, dan update. Dibangun lewat kode. */
class SettingsActivity : BaseActivity() {

    private lateinit var box: LinearLayout
    private val updater by lazy { UpdateFlow(this) { tvUpdateStatus?.text = it } }

    private var tvUpdateStatus: TextView? = null
    private var tvFeedStatus: TextView? = null
    private var tvOverlayState: TextView? = null
    private var dotOverlay: View? = null
    private var switchOverlay: SwitchCompat? = null
    private var previewHolder: FrameLayout? = null
    private var tilesHolder: LinearLayout? = null

    private var suppressSwitch = false
    private var pendingOverlay = false

    private val iconPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val path = LogoHelper.saveFromUri(this, uri)
        if (path == null) {
            Toast.makeText(this, "Gambar tidak bisa dibaca", Toast.LENGTH_SHORT).show()
        } else {
            Prefs.setBubbleCustom(this, path)
            Prefs.setBubbleIcon(this, BubbleFactory.CUSTOM_ICON)
            overlayLookChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        box = findViewById(R.id.settingsContainer)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        buildAppearance()
        buildOverlay()
        buildPanel()
        buildTournament()
        buildLive()
        buildUpdate()
        buildAbout()
        Anim.enterChildren(box, 100, 70)
        Anim.pressAll(box)
    }

    override fun onStart() {
        super.onStart()
        OverlayService.stateListener = { on -> runOnUiThread { setOverlaySwitch(on) } }
        LiveFeed.setListener("settings") { _ -> runOnUiThread { tvFeedStatus?.text = LiveFeed.status } }
    }

    override fun onStop() {
        OverlayService.stateListener = null
        LiveFeed.setListener("settings", null)
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
        updater.onResume()
    }

    // ───────────────────────── Bagian: Tampilan ─────────────────────────

    private fun buildAppearance() {
        val c = card("TAMPILAN", neon = true)

        label(c, "TEMA UI")
        chips(c, ThemeManager.baseNames, Prefs.themeBase(this)) { i ->
            Prefs.setThemeBase(this, i)
            recreate()
        }

        label(c, "WARNA AKSEN")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        val selected = Prefs.accent(this)
        ThemeManager.accentPreview.forEachIndexed { i, cols ->
            val dot = View(this).apply {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, cols
                ).apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    if (i == selected) setStroke(dp(3), ContextCompat.getColor(this@SettingsActivity, R.color.text))
                }
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) }
                contentDescription = ThemeManager.accentNames[i]
                setOnClickListener {
                    Prefs.setAccent(this@SettingsActivity, i)
                    recreate()
                }
            }
            row.addView(dot)
        }
        c.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        hint(c, "${ThemeManager.baseNames[Prefs.themeBase(this)]} · ${ThemeManager.accentNames[selected]}")

        switchRow(c, "Animasi", "Transisi, efek tekan, confetti saat juara", Prefs.animations(this)) {
            Prefs.setAnimations(this, it)
        }
    }

    // ───────────────────────── Bagian: Overlay ─────────────────────────

    private fun buildOverlay() {
        val c = card("OVERLAY LIVE")

        // toggle
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        dotOverlay = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)) }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) } }
        tvOverlayState = tv("Overlay Nonaktif", 16f, R.color.muted, true).apply { typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD) }
        texts.addView(tvOverlayState)
        texts.addView(tv("Ikon melayang setelah kamu keluar dari app", 10f, R.color.muted, false))
        switchOverlay = SwitchCompat(this).apply {
            minimumHeight = dp(44)
            thumbTintList = ContextCompat.getColorStateList(this@SettingsActivity, R.color.switch_thumb)
            trackTintList = ContextCompat.getColorStateList(this@SettingsActivity, R.color.switch_track)
            setOnCheckedChangeListener { _, checked ->
                if (suppressSwitch) return@setOnCheckedChangeListener
                if (checked) startOverlay() else stopOverlay()
            }
        }
        top.addView(dotOverlay); top.addView(texts); top.addView(switchOverlay)
        c.addView(top)
        setOverlaySwitch(OverlayService.isRunning)
        divider(c)

        // pratinjau + ukuran
        label(c, "PRATINJAU IKON")
        previewHolder = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)).apply { topMargin = dp(8) }
            setBackgroundResource(R.drawable.bg_chip)
        }
        c.addView(previewHolder)
        refreshPreview()

        slider(c, "UKURAN IKON", 70, 150, Prefs.overlayScale(this), { "$it%" }) {
            Prefs.setOverlayScale(this, it)
            refreshPreview()
            OverlayService.onSettingsChanged?.invoke()
        }

        label(c, "IKON OVERLAY")
        tilesHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(tilesHolder)
        buildTiles()

        slider(c, "TAHAN IKON UNTUK MEMBUKA", 300, 1500, Prefs.holdMs(this), { String.format(Locale("id"), "%.1f dtk", it / 1000.0) }, step = 100) {
            Prefs.setHoldMs(this, it)
        }
        hint(c, "Ikon harus ditahan supaya panel tidak terbuka tak sengaja saat kamu live.")
    }

    // ───────────────────────── Bagian: Desain & ukuran panel ─────────────────────────

    private var panelPreviewHolder: FrameLayout? = null

    private fun panelChanged() {
        refreshPanelPreview()
        OverlayService.onSettingsChanged?.invoke()
    }

    private fun buildPanel() {
        val c = card("DESAIN & UKURAN PANEL", neon = true)
        hint(c, "Panel adalah jendela bracket yang terbuka saat ikon ditahan. Ukurannya juga bisa diubah langsung dengan menggeser pojok kiri/kanan bawah panel.")

        label(c, "PRATINJAU PANEL")
        panelPreviewHolder = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)).apply { topMargin = dp(8) }
        }
        c.addView(panelPreviewHolder)
        refreshPanelPreview()

        label(c, "DESAIN PANEL")
        chips(c, PanelDesign.names, Prefs.panelStyle(this)) { i ->
            Prefs.setPanelStyle(this, i)
            panelChanged()
        }
        slider(c, "SUDUT MEMBULAT", 0, 32, Prefs.panelRadius(this), { "$it dp" }, step = 2) {
            Prefs.setPanelRadius(this, it); panelChanged()
        }
        slider(c, "TRANSPARANSI", 30, 100, Prefs.panelOpacity(this), { "$it%" }) {
            Prefs.setPanelOpacity(this, it); panelChanged()
        }
        slider(c, "LEBAR PANEL", 55, 100, Prefs.panelWidthPct(this), { "$it%" }, step = 5) {
            Prefs.setPanelWidthPct(this, it); panelChanged()
        }
        slider(c, "TINGGI PANEL", 0, 85, Prefs.panelHeightPct(this), { if (it < 15) "Otomatis" else "$it%" }, step = 5) {
            Prefs.setPanelHeightPct(this, it); panelChanged()
        }
        slider(c, "ZOOM ISI PANEL", 70, 150, Prefs.panelZoom(this), { "$it%" }, step = 5) {
            Prefs.setPanelZoom(this, it); panelChanged()
        }
        button(c, "Reset ukuran panel") {
            Prefs.setPanelWidthPct(this, 100)
            Prefs.setPanelHeightPct(this, 0)
            Prefs.setPanelZoom(this, 100)
            OverlayService.onSettingsChanged?.invoke()
            recreate()
        }
        hint(c, "Desain \"Kaca Blur\" memburamkan latar di Android 12+ (kalau didukung perangkat).")
    }

    private fun refreshPanelPreview() {
        val h = panelPreviewHolder ?: return
        h.removeAllViews()
        val ui = ThemeManager.themedContext(this)
        // latar warna-warni supaya efek transparansi terlihat
        h.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(ThemeManager.accent2(ui), ThemeManager.accent3(ui), ThemeManager.accent1(ui))
        ).apply { cornerRadius = dp(12).toFloat() }

        val available = resources.displayMetrics.widthPixels - dp(2 * 14 + 2 * 16)
        val mini = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = PanelDesign.background(ui, Prefs.panelStyle(this@SettingsActivity), Prefs.panelRadius(this@SettingsActivity), Prefs.panelOpacity(this@SettingsActivity))
        }
        mini.addView(tv("BRACKET LIVE", 11f, R.color.text, true))
        mini.addView(tv("Tim 1        0", 11f * Prefs.panelZoom(this) / 100f, R.color.text, false).apply { setPadding(0, dp(6), 0, 0) })
        mini.addView(tv("Tim 2        0", 11f * Prefs.panelZoom(this) / 100f, R.color.text, false).apply { setPadding(0, dp(2), 0, 0) })
        h.addView(mini, FrameLayout.LayoutParams(available * Prefs.panelWidthPct(this) / 100, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        Anim.reveal(mini)
    }

    /** Dua baris tile: desain tombol, lalu ikon. */
    private fun buildTiles() {
        val h = tilesHolder ?: return
        h.removeAllViews()
        val style = Prefs.bubbleStyle(this)
        val icon = Prefs.bubbleIcon(this)
        val custom = Prefs.bubbleCustom(this)
        val ui = ThemeManager.themedContext(this)

        h.addView(tv("Desain tombol", 10f, R.color.muted, false).apply { setPadding(0, dp(8), 0, dp(4)) })
        val styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        BubbleFactory.styleNames.forEachIndexed { i, name ->
            val img = ImageView(this).apply {
                setImageDrawable(BubbleFactory.background(ui, i, dp(44)))
                layoutParams = FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER)
            }
            styleRow.addView(tile(img, i == style, name) {
                Prefs.setBubbleStyle(this, i)
                overlayLookChanged()
            })
        }
        h.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(styleRow) })

        h.addView(tv("Gambar ikon", 10f, R.color.muted, false).apply { setPadding(0, dp(10), 0, dp(4)) })
        val iconRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        BubbleFactory.iconNames.forEachIndexed { i, name ->
            val cell = FrameLayout(this).apply { layoutParams = FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER) }
            cell.addView(ImageView(this).apply { setImageDrawable(BubbleFactory.background(ui, style, dp(44))) })
            val glyph = ImageView(this).apply { layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER) }
            if (i == BubbleFactory.CUSTOM_ICON) {
                if (custom != null) BubbleFactory.applyIcon(ui, glyph, i, custom, dp(26))
                else glyph.setImageResource(R.drawable.ic_ov_target)
            } else {
                BubbleFactory.applyIcon(ui, glyph, i, null, dp(26))
            }
            cell.addView(glyph)
            iconRow.addView(tile(cell, i == icon, name) {
                if (i == BubbleFactory.CUSTOM_ICON) {
                    iconPicker.launch("image/*")
                } else {
                    Prefs.setBubbleIcon(this, i)
                    overlayLookChanged()
                }
            })
        }
        h.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(iconRow) })
        Anim.pressAll(h)
    }

    private fun tile(content: View, selected: Boolean, desc: String, onClick: () -> Unit): View {
        val wrap = FrameLayout(this).apply {
            setBackgroundResource(if (selected) R.drawable.bg_chip_active else R.drawable.bg_chip)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(8) }
            contentDescription = desc
            setOnClickListener { onClick() }
        }
        (content.parent as? ViewGroup)?.removeView(content)
        wrap.addView(content)
        return wrap
    }

    private fun overlayLookChanged() {
        buildTiles()
        refreshPreview()
        OverlayService.onSettingsChanged?.invoke()
    }

    private fun refreshPreview() {
        val h = previewHolder ?: return
        h.removeAllViews()
        val ui = ThemeManager.themedContext(this)
        val scale = Prefs.overlayScale(this) / 100f
        val core = dp((44 * scale).toInt())
        val bg = ImageView(this).apply { setImageDrawable(BubbleFactory.background(ui, Prefs.bubbleStyle(this@SettingsActivity), core)) }
        val iconIdx = Prefs.bubbleIcon(this)
        val custom = Prefs.bubbleCustom(this)
        val imgSize = if (iconIdx == BubbleFactory.CUSTOM_ICON && custom != null) (core * 0.78f).toInt() else (core * 0.6f).toInt()
        val glyph = ImageView(this)
        BubbleFactory.applyIcon(ui, glyph, iconIdx, custom, imgSize)
        val cell = FrameLayout(this)
        cell.addView(bg, FrameLayout.LayoutParams(core, core, Gravity.CENTER))
        cell.addView(glyph, FrameLayout.LayoutParams(imgSize, imgSize, Gravity.CENTER))
        h.addView(cell, FrameLayout.LayoutParams(core, core, Gravity.CENTER))
        Anim.pop(cell)
    }

    // ───────────────────────── Bagian: Turnamen ─────────────────────────

    private fun buildTournament() {
        val c = card("TURNAMEN")

        label(c, "FORMAT BRACKET")
        chips(c, listOf("Eliminasi penuh", "Langsung Semifinal"), if (Prefs.semifinalOnly(this)) 1 else 0) { i ->
            Prefs.setSemifinalOnly(this, i == 1)
        }
        hint(c, "Langsung Semifinal: 4 tim, babak pertama = semifinal lalu final. Berlaku untuk mode eliminasi.")

        divider(c)
        label(c, "POIN PER KILL")
        field(c, Prefs.killPoint(this).toString(), "1", InputType.TYPE_CLASS_NUMBER) {
            Prefs.setKillPoint(this, it.toIntOrNull() ?: 1)
        }
        label(c, "POIN POSISI (juara 1, 2, 3, … dipisah koma)")
        field(c, Prefs.placementPoints(this), "12,9,8,7,6,5,4,3,2,1", InputType.TYPE_CLASS_TEXT) {
            Prefs.setPlacementPoints(this, it.ifBlank { "12,9,8,7,6,5,4,3,2,1" })
        }
        hint(c, "Dipakai saat poin dihitung otomatis dari Koneksi Live. Poin tiap tim juga bisa diubah manual di layar utama.")
    }

    // ───────────────────────── Bagian: Koneksi Live ─────────────────────────

    private fun buildLive() {
        val c = card("KONEKSI LIVE (CUSTOM ROOM)")
        hint(c, "Game mobile tidak punya API resmi untuk membaca room dari luar. Bracket Live membaca feed JSON (HTTPS) dari tool/bot/spreadsheet panitia: tim terdeteksi otomatis dan poin dihitung otomatis.")

        label(c, "URL FEED (https://…)")
        field(c, Prefs.feedUrl(this), "https://contoh.com/room.json", InputType.TYPE_TEXT_VARIATION_URI) {
            Prefs.setFeedUrl(this, it.trim())
        }
        slider(c, "CEK ULANG TIAP", 5, 60, Prefs.feedInterval(this), { "$it dtk" }) {
            Prefs.setFeedInterval(this, it)
        }
        switchRow(c, "Hubungkan", "Pantau room secara real-time", LiveFeed.running) { on ->
            Prefs.setFeedEnabled(this, on)
            if (on) {
                if (Prefs.feedUrl(this).isBlank()) {
                    Toast.makeText(this, "Isi URL feed dulu", Toast.LENGTH_SHORT).show()
                } else LiveFeed.start(this)
            } else LiveFeed.stop()
        }
        tvFeedStatus = tv(LiveFeed.status, 11f, R.color.text, true)
        tvFeedStatus!!.setTextColor(ThemeManager.accent1(this))
        tvFeedStatus!!.setPadding(0, dp(6), 0, 0)
        c.addView(tvFeedStatus)
        button(c, "Tes koneksi") {
            tvFeedStatus?.text = "Menguji…"
            LiveFeed.test(this) { tvFeedStatus?.text = it }
        }
        hint(c, "Format: {\"match\":1,\"teams\":[{\"name\":\"EVOS\",\"kills\":5,\"placement\":2,\"members\":[\"A\",\"B\"]}]}")
    }

    // ───────────────────────── Bagian: Update & Tentang ─────────────────────────

    private fun buildUpdate() {
        val c = card("UPDATE APLIKASI")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        col.addView(tv("Versi ${updater.versionName()}  (build ${updater.currentBuild()})", 15f, R.color.text, true).apply { typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD) })
        tvUpdateStatus = tv("Ketuk tombol untuk mengecek versi terbaru", 10f, R.color.muted, false)
        col.addView(tvUpdateStatus)
        row.addView(col)
        row.addView(TextView(this).apply {
            text = "Cek Update"
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD))
            setPadding(dp(18), 0, dp(18), 0)
            setBackgroundResource(R.drawable.bg_btn_primary)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
            setOnClickListener { updater.check(true) }
        })
        c.addView(row)
    }

    private fun buildAbout() {
        val c = card("TENTANG", plain = true)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) } }
        col.addView(tv("Bracket Live", 14f, R.color.text, true))
        col.addView(tv("Bracket turnamen untuk live streaming", 10f, R.color.muted, false))
        row.addView(col)
        c.addView(row)
    }

    // ───────────────────────── Overlay on/off ─────────────────────────

    private fun setOverlaySwitch(on: Boolean) {
        suppressSwitch = true
        switchOverlay?.isChecked = on
        suppressSwitch = false
        tvOverlayState?.text = if (on) "Overlay Aktif" else "Overlay Nonaktif"
        tvOverlayState?.setTextColor(ContextCompat.getColor(this, if (on) R.color.green else R.color.muted))
        dotOverlay?.setBackgroundResource(if (on) R.drawable.bg_dot_on else R.drawable.bg_dot_off)
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            setOverlaySwitch(false)
            AlertDialog.Builder(this)
                .setTitle("Izin Overlay")
                .setMessage("Aktifkan izin \"Tampil di atas aplikasi lain\" untuk Bracket Live, lalu kembali ke aplikasi ini.")
                .setPositiveButton("Buka Settings") { _, _ ->
                    pendingOverlay = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Batal", null)
                .show()
            return
        }
        val i = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        setOverlaySwitch(true)
        Toast.makeText(this, "Overlay aktif — keluar dari app, ikon akan muncul", Toast.LENGTH_LONG).show()
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        setOverlaySwitch(false)
    }

    // ───────────────────────── Pembangun UI ─────────────────────────

    private fun card(title: String, neon: Boolean = false, plain: Boolean = false): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundResource(if (neon) R.drawable.bg_card_neon else R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
        }
        c.addView(TextView(this, null, 0, R.style.SectionLabel).apply { text = title })
        box.addView(c)
        return c
    }

    private fun tv(text: String, sp: Float, colorRes: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = sp
        setTextColor(ContextCompat.getColor(this@SettingsActivity, colorRes))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun label(parent: LinearLayout, text: String) {
        parent.addView(TextView(this, null, 0, R.style.SectionLabel).apply {
            this.text = text
            setPadding(0, dp(14), 0, 0)
        })
    }

    private fun hint(parent: LinearLayout, text: String) {
        parent.addView(tv(text, 10f, R.color.muted, false).apply { setPadding(0, dp(6), 0, 0) })
    }

    private fun divider(parent: LinearLayout) {
        parent.addView(View(this).apply {
            setBackgroundColor(0x22FFFFFF)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { topMargin = dp(14) }
        })
    }

    private fun button(parent: LinearLayout, text: String, onClick: () -> Unit) {
        parent.addView(TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 12f
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD))
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.muted_light))
            setBackgroundResource(R.drawable.bg_btn_gray)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(10) }
            setOnClickListener { onClick() }
        })
    }

    private fun switchRow(parent: LinearLayout, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit): SwitchCompat {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, 0) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        col.addView(tv(title, 14f, R.color.text, true))
        col.addView(tv(sub, 10f, R.color.muted, false))
        val sw = SwitchCompat(this).apply {
            minimumHeight = dp(44)
            thumbTintList = ContextCompat.getColorStateList(this@SettingsActivity, R.color.switch_thumb)
            trackTintList = ContextCompat.getColorStateList(this@SettingsActivity, R.color.switch_track)
            isChecked = checked
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        row.addView(col); row.addView(sw)
        parent.addView(row)
        return sw
    }

    private fun chips(parent: LinearLayout, names: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        val views = ArrayList<TextView>()
        fun paint(sel: Int) = views.forEachIndexed { i, v ->
            v.setBackgroundResource(if (i == sel) R.drawable.bg_chip_active else R.drawable.bg_chip)
            v.setTextColor(ContextCompat.getColor(this, if (i == sel) R.color.on_accent else R.color.muted_light))
        }
        names.forEachIndexed { i, n ->
            val v = TextView(this).apply {
                text = n
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD))
                setPadding(dp(16), 0, dp(16), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { marginEnd = dp(8) }
                setOnClickListener { paint(i); onSelect(i) }
            }
            views.add(v)
            row.addView(v)
        }
        paint(selected)
        parent.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
    }

    private fun slider(parent: LinearLayout, title: String, min: Int, max: Int, value: Int, fmt: (Int) -> String, step: Int = 1, onChange: (Int) -> Unit) {
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(14), 0, 0) }
        head.addView(TextView(this, null, 0, R.style.SectionLabel).apply {
            text = title
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val tvVal = tv(fmt(value), 12f, R.color.text, true).apply { setTextColor(ThemeManager.accent1(this@SettingsActivity)) }
        head.addView(tvVal)
        parent.addView(head)
        val sb = SeekBar(this).apply {
            this.max = (max - min) / step
            progress = (value - min) / step
            val tint = ColorStateList.valueOf(ThemeManager.accent1(this@SettingsActivity))
            progressTintList = tint
            thumbTintList = tint
            progressBackgroundTintList = ColorStateList.valueOf(0xFF2C3A75.toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = min + p * step
                    tvVal.text = fmt(v)
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        parent.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
    }

    private fun field(parent: LinearLayout, value: String, hintText: String, inputType: Int, onChange: (String) -> Unit) {
        parent.addView(EditText(this).apply {
            setText(value)
            hint = hintText
            setHintTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.dim))
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text))
            textSize = 13f
            setSingleLine()
            this.inputType = inputType
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundResource(R.drawable.bg_input)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(8) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
            })
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
