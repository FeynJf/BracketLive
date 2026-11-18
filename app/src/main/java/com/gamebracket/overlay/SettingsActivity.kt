package com.gamebracket.overlay

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
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
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
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
    private var tvUpdateNotes: TextView? = null
    private var tvFeedStatus: TextView? = null
    private var tvLiveSummary: TextView? = null
    private var tvGameName: TextView? = null
    private var ivLiveGame: ImageView? = null
    private var btnConnect: TextView? = null
    private var btnUsage: TextView? = null
    private var tvUsageHint: TextView? = null
    private var lastSummary: TextView? = null
    private var tvOverlayState: TextView? = null
    private var dotOverlay: View? = null
    private var switchOverlay: SwitchCompat? = null
    private var previewHolder: FrameLayout? = null

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
        buildDesign()
        buildPanel()
        buildTournament()
        buildTitleStyle()
        buildLive()
        buildPerformance()
        buildUpdate()
        buildAbout()
        Anim.enterChildren(box, 100, 70)
        Anim.pressAll(box)
    }

    override fun onStart() {
        super.onStart()
        OverlayService.stateListener = { on -> runOnUiThread { setOverlaySwitch(on) } }
        LiveFeed.setListener("settings") { _ -> runOnUiThread { refreshLiveUi() } }
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
        refreshLiveUi() // izin "Akses penggunaan" mungkin baru diberikan
    }

    // ───────────────────────── Bagian: Tampilan ─────────────────────────

    /** Baris "label + nilai + tombol Pilih". Mengembalikan TextView nilai supaya bisa diperbarui. */
    private fun pickRow(parent: LinearLayout, caption: String, value: String, button: String, onOpen: () -> Unit): TextView {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
        val valueTv = tv(value, 14f, R.color.text, true)
        r.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(valueTv)
            addView(tv(caption, 11f, R.color.muted, false))
        })
        r.addView(TextView(this).apply {
            text = button
            gravity = Gravity.CENTER
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.muted_light))
            setPadding(dp(16), 0, dp(16), 0)
            background = Skin.chip(this@SettingsActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
            setOnClickListener { onOpen() }
        })
        parent.addView(r)
        return valueTv
    }

    private fun pickFont(which: String, current: String, title: String, onChosen: (String) -> Unit) =
        Pickers.font(this, which, current, title, onChosen)

    // ───────────────────────── Bagian: Gaya judul turnamen ─────────────────────────

    private var titlePreview: TextView? = null

    private fun titleGravity() = when (Prefs.titleAlign(this)) { 0 -> Gravity.START; 1 -> Gravity.CENTER_HORIZONTAL; else -> Gravity.END }

    private fun refreshTitlePreview() {
        val t = titlePreview ?: return
        t.text = Prefs.tournamentName(this).ifBlank { "Nama Turnamen" }
        t.textSize = Prefs.titleSize(this).toFloat()
        t.gravity = titleGravity()
        Fonts.titleFace(this)?.let { t.setTypeface(it, Typeface.BOLD) } ?: t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD))
    }

    private fun titleStyleChanged() {
        refreshTitlePreview()
        OverlayService.onSettingsChanged?.invoke()
    }

    private fun buildTitleStyle() {
        val c = card("Gaya judul turnamen", summary = "Font, ukuran, dan posisi judul", open = true)
        hint(c, "Judul turnamen tampil di atas bracket dan di panel overlay.")
        titlePreview = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = Skin.chip(this@SettingsActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        Fonts.markTitle(titlePreview!!)
        c.addView(titlePreview)
        refreshTitlePreview()

        divider(c)
        label(c, "Font judul (${Fonts.list(this, "title").size} pilihan)")
        val valueTv = pickRow(c, "Khusus judul turnamen", Prefs.titleFont(this).ifEmpty { "Bawaan sistem" }, "Pilih") {
            pickFont("title", Prefs.titleFont(this), "Font judul") { n ->
                Prefs.setTitleFont(this, n)
                titleStyleChanged()
                titleFontValue?.text = n.ifEmpty { "Bawaan sistem" }
            }
        }
        titleFontValue = valueTv

        slider(c, "Ukuran judul", 12, 44, Prefs.titleSize(this), { "$it sp" }) {
            Prefs.setTitleSize(this, it)
            titleStyleChanged()
        }
        label(c, "Posisi judul")
        chips(c, listOf("Kiri", "Tengah", "Kanan"), Prefs.titleAlign(this)) { i ->
            Prefs.setTitleAlign(this, i)
            titleStyleChanged()
        }
    }

    private var titleFontValue: TextView? = null

    private fun accentHex() = "#%06X".format(Prefs.accentColor(this) and 0xFFFFFF)

    private fun buildAppearance() {
        val c = card("Tampilan", summary = "${ThemeManager.baseNames[Prefs.themeBase(this)]} · aksen ${accentHex()}", open = true)

        label(c, "Tema UI (${ThemeManager.baseNames.size} pilihan)")
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
        themeRow.addView(Pickers.themeTile(this, Prefs.themeBase(this), false).apply { layoutParams = LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT) })
        themeRow.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
            addView(tv(ThemeManager.baseNames[Prefs.themeBase(this@SettingsActivity)], 14f, R.color.text, true))
            addView(tv("Warna latar dan permukaan seluruh aplikasi", 11f, R.color.muted, false))
        })
        c.addView(themeRow)
        button(c, "Pilih tema") {
            Pickers.grid(this, "Tema UI", "${ThemeManager.baseNames.size} WARNA DASAR", ThemeManager.baseNames.size,
                Prefs.themeBase(this), 3, { i, sel -> Pickers.themeTile(this, i, sel) }) { i ->
                Prefs.setThemeBase(this, i)
                recreate()
            }
        }

        divider(c)
        label(c, "Warna aksen")
        val accRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
        accRow.addView(View(this).apply {
            background = Skin.rect(this@SettingsActivity, Prefs.accentColor(this@SettingsActivity), 10f)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        })
        accRow.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
            addView(tv(accentHex(), 14f, R.color.text, true).apply { typeface = Typeface.MONOSPACE })
            addView(tv("Bebas pilih warna apa pun (16,7 juta)", 11f, R.color.muted, false))
        })
        c.addView(accRow)
        // pilihan cepat
        val swatches = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        ThemeManager.accentPresets.forEach { (name, col) ->
            swatches.addView(View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(col)
                    if (col == Prefs.accentColor(this@SettingsActivity)) setStroke(dp(3), ContextCompat.getColor(this@SettingsActivity, R.color.text))
                    else setStroke(dp(1), 0x33FFFFFF)
                }
                contentDescription = name
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(9) }
                setOnClickListener {
                    Prefs.setAccentColor(this@SettingsActivity, col)
                    OverlayService.onSettingsChanged?.invoke()
                    recreate()
                }
            })
        }
        c.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(swatches) })
        button(c, "Pilih warna sendiri") { Pickers.accentDialog(this) { OverlayService.onSettingsChanged?.invoke(); recreate() } }

        divider(c)
        label(c, "Desain UI (${ThemeManager.uiDesignNames.size} pilihan)")
        pickRow(c, "Bentuk kartu, tombol, dan garis", ThemeManager.uiDesignNames[Prefs.uiDesign(this)], "Pilih") {
            Pickers.grid(this, "Desain UI", "${ThemeManager.uiDesignNames.size} bentuk berbeda (bukan warna)", ThemeManager.uiDesignNames.size,
                Prefs.uiDesign(this), 3, { i, sel -> Pickers.uiDesignTile(this, i, sel) }) { i ->
                Prefs.setUiDesign(this, i)
                recreate()
            }
        }

        divider(c)
        label(c, "Font aplikasi (${Fonts.list(this, "ui").size} pilihan)")
        pickRow(c, "Berlaku di seluruh aplikasi", Prefs.uiFont(this).ifEmpty { "Bawaan sistem" }, "Pilih") {
            pickFont("ui", Prefs.uiFont(this), "Font aplikasi") { n ->
                Prefs.setUiFont(this, n)
                recreate()
            }
        }

        divider(c)
        switchRow(c, "Animasi", "Transisi halus di seluruh aplikasi", Prefs.animations(this)) {
            Prefs.setAnimations(this, it)
        }
    }

    // ───────────────────────── Bagian: Overlay ─────────────────────────

    private fun buildOverlay() {
        val c = card("Overlay live", summary = "Aktifkan overlay, tahan untuk membuka", open = true)

        // toggle
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        dotOverlay = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)) }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) } }
        tvOverlayState = tv("Overlay Nonaktif", 16f, R.color.muted, true).apply { typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD) }
        texts.addView(tvOverlayState)
        texts.addView(tv("Ikon melayang setelah kamu keluar dari app", 11f, R.color.muted, false))
        switchOverlay = SwitchCompat(this).apply {
            minimumHeight = dp(44)
            thumbTintList = Skin.switchThumb(this@SettingsActivity)
            trackTintList = Skin.switchTrack(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                if (suppressSwitch) return@setOnCheckedChangeListener
                if (checked) startOverlay() else stopOverlay()
            }
        }
        top.addView(dotOverlay); top.addView(texts); top.addView(switchOverlay)
        c.addView(top)
        setOverlaySwitch(OverlayService.isRunning)
        divider(c)

        slider(c, "Tahan ikon untuk membuka", 300, 1500, Prefs.holdMs(this), { String.format(Locale("id"), "%.1f dtk", it / 1000.0) }, step = 100) {
            Prefs.setHoldMs(this, it)
        }
        hint(c, "Ikon harus ditahan supaya panel tidak terbuka tak sengaja saat kamu live.")
    }

    // ───────────────────────── Bagian: Desain overlay (galeri) ─────────────────────────

    private var designRows: LinearLayout? = null

    private fun buildDesign() {
        val c = card("Desain overlay", summary = "35 bentuk panel · 66 desain tombol · 44 ikon", open = true)

        label(c, "Pratinjau tombol")
        previewHolder = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)).apply { topMargin = dp(8) }
            setBackgroundResource(R.drawable.bg_chip)
        }
        c.addView(previewHolder)
        refreshPreview()

        slider(c, "Ukuran ikon", 70, 150, Prefs.overlayScale(this), { "$it%" }) {
            Prefs.setOverlayScale(this, it)
            refreshPreview()
            OverlayService.onSettingsChanged?.invoke()
        }

        designRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(designRows)
        buildDesignRows()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            divider(c)
            switchRow(c, "Blur latar panel", "Memburamkan layar di belakang panel (Android 12+, jika didukung perangkat)", Prefs.panelBlur(this)) {
                Prefs.setPanelBlur(this, it)
                panelChanged()
            }
        }
    }

    /** Tiga baris pilihan: desain panel, desain tombol, ikon. Tiap baris membuka dialog galeri. */
    private fun buildDesignRows() {
        val h = designRows ?: return
        h.removeAllViews()
        val ui = ThemeManager.themedContext(this)
        val custom = Prefs.bubbleCustom(this)

        fun row(label: String, name: String, count: Int, preview: View, onOpen: () -> Unit) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, 0)
            }
            r.addView(preview)
            r.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
                addView(TextView(this@SettingsActivity, null, 0, R.style.SectionLabel).apply { text = label })
                addView(tv(name, 14f, R.color.text, true))
            })
            r.addView(TextView(this).apply {
                text = "Pilih ($count)"
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.muted_light))
                setPadding(dp(14), 0, dp(14), 0)
                background = Skin.chip(this@SettingsActivity)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
                setOnClickListener { onOpen() }
            })
            h.addView(r)
        }

        // desain panel
        val panelPrev = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(44))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(ThemeManager.accent2(ui), ThemeManager.accent3(ui), ThemeManager.accent1(ui))
            ).apply { cornerRadius = dp(8).toFloat() }
        }
        panelPrev.addView(View(this).apply { background = PanelDesign.background(ui, Prefs.panelStyle(this@SettingsActivity), 8, 85) },
            FrameLayout.LayoutParams(dp(46), dp(30), Gravity.CENTER))
        row("Desain panel", PanelDesign.names[Prefs.panelStyle(this).coerceIn(0, PanelDesign.names.size - 1)], PanelDesign.names.size, panelPrev) {
            Pickers.grid(this, "Desain panel", "${PanelDesign.names.size} BENTUK BERBEDA", PanelDesign.names.size, Prefs.panelStyle(this), 3,
                { i, sel -> Pickers.panelTile(this, i, sel) }) { i ->
                Prefs.setPanelStyle(this, i)
                buildDesignRows()
                panelChanged()
            }
        }

        // desain tombol
        val bubPrev = ImageView(this).apply {
            setImageDrawable(BubbleFactory.background(ui, Prefs.bubbleStyle(this@SettingsActivity), dp(44)))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(10); marginEnd = dp(10) }
        }
        row("Desain tombol", BubbleFactory.styleNames[Prefs.bubbleStyle(this).coerceIn(0, BubbleFactory.styleNames.size - 1)], BubbleFactory.styleNames.size, bubPrev) {
            Pickers.grid(this, "Desain tombol", "${BubbleFactory.styleNames.size} DESAIN BERBEDA", BubbleFactory.styleNames.size, Prefs.bubbleStyle(this), 4,
                { i, sel -> Pickers.bubbleTile(this, i, sel) }) { i ->
                Prefs.setBubbleStyle(this, i)
                overlayLookChanged()
            }
        }

        // ikon
        val iconPrev = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(dp(64), dp(44)) }
        val glyph = ImageView(this)
        val idx = Prefs.bubbleIcon(this).coerceIn(0, BubbleFactory.iconNames.size - 1)
        BubbleFactory.applyIcon(ui, glyph, idx, custom, dp(26))
        iconPrev.addView(glyph, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        iconPrev.setBackgroundResource(R.drawable.bg_chip)
        row("Ikon", BubbleFactory.iconNames[idx], BubbleFactory.iconNames.size - 1, iconPrev) {
            Pickers.grid(this, "Ikon", "${BubbleFactory.iconNames.size - 1} GAMBAR + FOTO SENDIRI", BubbleFactory.iconNames.size, idx, 5,
                { i, sel -> Pickers.iconTile(this, i, sel) }) { i ->
                if (i == BubbleFactory.CUSTOM_ICON) {
                    iconPicker.launch("image/*")
                } else {
                    Prefs.setBubbleIcon(this, i)
                    overlayLookChanged()
                }
            }
        }
    }

    // ───────────────────────── Bagian: Ukuran & transparansi panel ─────────────────────────

    private var panelPreviewHolder: FrameLayout? = null

    private fun panelChanged() {
        refreshPanelPreview()
        OverlayService.onSettingsChanged?.invoke()
    }

    private fun buildPanel() {
        val c = card("Ukuran & transparansi panel", summary = "Transparansi, sudut, lebar, tinggi, zoom")
        hint(c, "Panel adalah jendela bracket yang terbuka saat ikon ditahan. Ukurannya juga bisa diubah langsung dengan menggeser pojok kiri/kanan bawah panel.")

        label(c, "Pratinjau panel")
        panelPreviewHolder = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)).apply { topMargin = dp(8) }
        }
        c.addView(panelPreviewHolder)
        refreshPanelPreview()

        hint(c, "Desain panel dipilih di kartu DESAIN OVERLAY di atas.")
        slider(c, "Sudut membulat", 0, 32, Prefs.panelRadius(this), { "$it dp" }, step = 2) {
            Prefs.setPanelRadius(this, it); panelChanged()
        }
        slider(c, "Transparansi", 30, 100, Prefs.panelOpacity(this), { "$it%" }) {
            Prefs.setPanelOpacity(this, it); panelChanged()
        }
        slider(c, "Lebar panel", 55, 100, Prefs.panelWidthPct(this), { "$it%" }, step = 5) {
            Prefs.setPanelWidthPct(this, it); panelChanged()
        }
        slider(c, "Tinggi panel", 0, 85, Prefs.panelHeightPct(this), { if (it < 15) "Otomatis" else "$it%" }, step = 5) {
            Prefs.setPanelHeightPct(this, it); panelChanged()
        }
        slider(c, "Zoom isi panel", 70, 150, Prefs.panelZoom(this), { "$it%" }, step = 5) {
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

    private fun overlayLookChanged() {
        buildDesignRows()
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
        val c = card("Turnamen", summary = "Nama, format bracket, poin kill & posisi", open = true)

        label(c, "Nama turnamen")
        field(c, Prefs.tournamentName(this), "Contoh: Liga Kampus 2026", InputType.TYPE_TEXT_FLAG_CAP_WORDS) {
            Prefs.setTournamentName(this, it)
        }
        hint(c, "Ditulis otomatis di atas bracket dan di panel overlay.")

        divider(c)
        label(c, "Format bracket")
        chips(c, listOf("Eliminasi penuh", "Langsung semifinal"), if (Prefs.semifinalOnly(this)) 1 else 0) { i ->
            Prefs.setSemifinalOnly(this, i == 1)
            Toast.makeText(this, if (i == 1) "Bracket berubah: semifinal → final (4 tim)" else "Bracket kembali ke eliminasi penuh", Toast.LENGTH_SHORT).show()
        }
        hint(c, "Langsung semifinal: 4 tim, babak pertama = semifinal lalu final. Berlaku untuk mode eliminasi dan langsung diterapkan di layar utama.")

        divider(c)
        label(c, "Poin per kill")
        field(c, Prefs.killPoint(this).toString(), "1", InputType.TYPE_CLASS_NUMBER) {
            Prefs.setKillPoint(this, it.toIntOrNull() ?: 1)
            LiveFeed.recompute(this)
        }
        label(c, "Poin posisi (juara 1, 2, 3, … dipisah koma)")
        field(c, Prefs.placementPoints(this), "12,9,8,7,6,5,4,3,2,1", InputType.TYPE_CLASS_TEXT) {
            Prefs.setPlacementPoints(this, it.ifBlank { "12,9,8,7,6,5,4,3,2,1" })
            LiveFeed.recompute(this)
        }
        hint(c, "Aturan ini dipakai saat poin dihitung otomatis dari koneksi live; perubahan langsung menghitung ulang skor tim. Poin tim juga bisa diubah manual di layar utama.")
    }

    // ───────────────────────── Bagian: Koneksi Live ─────────────────────────

    private fun buildLive() {
        val c = card("Koneksi live", summary = LiveFeed.status)
        tvLiveSummary = lastSummary
        hint(c, "Pilih game yang terpasang di HP kamu lalu hubungkan. Bracket Live mendeteksi kapan game dibuka, dan overlay langsung tampil di atas game. Tidak perlu memasukkan link.")

        // kartu game terpilih
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundResource(R.drawable.bg_match)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        ivLiveGame = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
        }
        tvGameName = tv("", 14f, R.color.text, true)
        tvFeedStatus = tv("", 11f, R.color.muted, false).apply { setPadding(0, dp(2), 0, 0) }
        col.addView(tvGameName)
        col.addView(tvFeedStatus)
        row.addView(ivLiveGame)
        row.addView(col)
        c.addView(row)

        c.addView(actionBtn("Pilih game di perangkat", false) { pickGame(false) }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(10) }
        })

        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        }
        btnConnect = actionBtn("Hubungkan", true) { toggleConnect() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) }
        }
        btns.addView(btnConnect)
        btns.addView(actionBtn("Buka game", false) { openGame() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        })
        c.addView(btns)

        // izin Akses penggunaan (supaya app tahu game sedang dibuka)
        tvUsageHint = tv("Aktifkan \"Akses penggunaan\" untuk Bracket Live supaya bisa tahu kapan game dibuka.", 11f, R.color.muted, false).apply { setPadding(0, dp(12), 0, 0) }
        c.addView(tvUsageHint)
        btnUsage = actionBtn("Beri akses penggunaan", false) { openUsageSettings() }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(6) }
        }
        c.addView(btnUsage)

        // sumber data otomatis (opsional, disembunyikan)
        divider(c)
        val hasFeed = Prefs.feedUrl(this).isNotBlank()
        val adv = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (hasFeed) View.VISIBLE else View.GONE
        }
        val advHead = tv(if (hasFeed) "Sumber data otomatis (opsional)  ▴" else "Sumber data otomatis (opsional)  ▾", 11f, R.color.muted_light, true).apply {
            setPadding(0, dp(14), 0, dp(6))
            setOnClickListener {
                val show = adv.visibility != View.VISIBLE
                adv.visibility = if (show) View.VISIBLE else View.GONE
                text = if (show) "Sumber data otomatis (opsional)  ▴" else "Sumber data otomatis (opsional)  ▾"
            }
        }
        c.addView(advHead)
        c.addView(adv)
        hint(adv, "Game mobile tidak menyediakan data room untuk aplikasi lain, jadi skor tidak bisa dibaca langsung dari game. Kalau panitia punya feed JSON (tool/bot/spreadsheet), isi URL-nya agar tim & poin terisi otomatis. Tidak wajib.")
        label(adv, "URL FEED (https://…)")
        field(adv, Prefs.feedUrl(this), "https://contoh.com/room.json", InputType.TYPE_TEXT_VARIATION_URI) {
            Prefs.setFeedUrl(this, it.trim())
        }
        slider(adv, "Cek ulang tiap", 5, 60, Prefs.feedInterval(this), { "$it dtk" }) {
            Prefs.setFeedInterval(this, it)
        }
        button(adv, "Tes koneksi feed") {
            tvFeedStatus?.text = "Menguji…"
            LiveFeed.test(this) { tvFeedStatus?.text = it }
        }
        hint(adv, "Format: {\"match\":1,\"teams\":[{\"name\":\"EVOS\",\"kills\":5,\"placement\":2,\"members\":[\"A\",\"B\"]}]}")

        refreshLiveUi()
    }

    private fun refreshLiveUi() {
        val pkg = Prefs.liveGamePkg(this)
        val name = Prefs.liveGameName(this)
        val running = LiveFeed.running
        tvGameName?.text = if (pkg.isEmpty()) "Belum ada game dipilih" else name
        tvFeedStatus?.text = when {
            running -> LiveFeed.status
            pkg.isEmpty() -> "Ketuk \"Pilih game di perangkat\""
            else -> "Belum terhubung"
        }
        tvFeedStatus?.setTextColor(
            if (running) ThemeManager.accent1(this) else ContextCompat.getColor(this, R.color.muted)
        )
        tvLiveSummary?.text = when {
            running -> LiveFeed.status
            pkg.isEmpty() -> "Belum ada game dipilih"
            else -> "$name · belum terhubung"
        }
        btnConnect?.text = if (running) "Putuskan" else "Hubungkan"
        val icon = if (pkg.isNotEmpty()) {
            try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
        } else null
        if (icon != null) ivLiveGame?.setImageDrawable(icon) else ivLiveGame?.setImageResource(R.drawable.ic_ov_pad)
        val needUsage = pkg.isNotEmpty() && !GameWatcher.hasAccess(this)
        tvUsageHint?.visibility = if (needUsage) View.VISIBLE else View.GONE
        btnUsage?.visibility = if (needUsage) View.VISIBLE else View.GONE
    }

    private fun openUsageSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Buka Settings → Akses penggunaan → Bracket Live", Toast.LENGTH_LONG).show()
        }
    }

    /** Hubungkan / putuskan koneksi live. */
    private fun toggleConnect() {
        if (LiveFeed.running) {
            Prefs.setFeedEnabled(this, false)
            LiveFeed.stop()
        } else {
            if (Prefs.liveGamePkg(this).isEmpty() && Prefs.feedUrl(this).isBlank()) {
                Toast.makeText(this, "Pilih game dulu", Toast.LENGTH_SHORT).show()
                pickGame(false)
                return
            }
            Prefs.setFeedEnabled(this, true)
            LiveFeed.start(this)
        }
        refreshLiveUi()
    }

    private fun openGame() {
        val pkg = Prefs.liveGamePkg(this)
        if (pkg.isEmpty()) {
            Toast.makeText(this, "Pilih game dulu", Toast.LENGTH_SHORT).show()
            return
        }
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) {
            Toast.makeText(this, "Game tidak ditemukan di perangkat", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(launch)
    }

    /** Dialog daftar game yang terpasang. Kalau tak ada game terdeteksi, langsung tampilkan semua aplikasi. */
    private fun pickGame(includeAll: Boolean) {
        Thread {
            val list = InstalledGames.load(this, includeAll)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (list.isEmpty() && !includeAll) {
                    pickGame(true)
                    return@runOnUiThread
                }
                val lv = ListView(this)
                lv.adapter = GameAdapter(list)
                val b = AlertDialog.Builder(this)
                    .setTitle(if (includeAll) "Semua aplikasi" else "Pilih game")
                    .setView(lv)
                    .setNegativeButton("Batal", null)
                if (!includeAll) b.setNeutralButton("Semua aplikasi") { _, _ -> pickGame(true) }
                val dlg = b.create()
                lv.setOnItemClickListener { _, _, pos, _ ->
                    dlg.dismiss()
                    onGameChosen(list[pos])
                }
                dlg.show()
            }
        }.start()
    }

    private fun onGameChosen(app: InstalledGames.App) {
        Prefs.setLiveGame(this, app.pkg, app.label)
        // samakan pilihan game turnamen kalau game ini dikenali dan belum ada mode terpilih
        app.gameId?.let { id -> if (BracketStore.mode == null) BracketStore.gameId = id }
        // langsung terhubung
        Prefs.setFeedEnabled(this, true)
        LiveFeed.stop()
        LiveFeed.start(this)
        refreshLiveUi()
        if (!GameWatcher.hasAccess(this)) {
            AlertDialog.Builder(this)
                .setTitle("Izin Akses penggunaan")
                .setMessage("Supaya Bracket Live tahu kapan ${app.label} dibuka, aktifkan \"Akses penggunaan\" untuk Bracket Live, lalu kembali ke sini.")
                .setPositiveButton("Buka Settings") { _, _ -> openUsageSettings() }
                .setNegativeButton("Nanti", null)
                .show()
        } else {
            Toast.makeText(this, "Terhubung ke ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class GameAdapter(private val items: List<InstalledGames.App>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val app = items[position]
            val row = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(10), dp(20), dp(10))
            }
            row.addView(ImageView(this@SettingsActivity).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            })
            val col = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) }
            }
            col.addView(TextView(this@SettingsActivity).apply {
                text = app.label
                textSize = 15f
            })
            app.gameId?.let { gid ->
                col.addView(TextView(this@SettingsActivity).apply {
                    text = "Dikenali sebagai ${BracketStore.games[gid]?.first ?: gid}"
                    textSize = 11f
                    alpha = 0.7f
                })
            }
            row.addView(col)
            return row
        }
    }

    // ───────────────────────── Bagian: Update & Tentang ─────────────────────────

    // ───────────────────────── Bagian: Performa & diagnostik ─────────────────────────

    private fun buildPerformance() {
        val c = card("Performa & diagnostik", summary = "Perangkat: ${DeviceProfile.tierName(this)} · animasi & overlay menyesuaikan")
        hint(c, DeviceProfile.describe(this))

        label(c, "Mode performa")
        chips(c, listOf("Otomatis", "Hemat", "Maksimal"), Prefs.perfMode(this)) { i ->
            Prefs.setPerfMode(this, i)
            Toast.makeText(this, "Mode performa: ${DeviceProfile.tierName(this)}", Toast.LENGTH_SHORT).show()
        }
        hint(c, "Otomatis menyesuaikan RAM & prosesor. Hemat = animasi singkat, confetti ringan, tanpa blur (cocok untuk HP lawas). Maksimal = semua efek.")

        divider(c)
        label(c, "Catatan error")
        val log = CrashLog.errors(this)
        val logView = tv(log?.takeLast(700) ?: "Tidak ada error tercatat.", 10f, if (log == null) R.color.green else R.color.muted_light, false).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundResource(R.drawable.bg_chip)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        }
        c.addView(logView)
        button(c, "Salin catatan error") {
            val all = (CrashLog.pendingFatal(this) ?: "") + (CrashLog.errors(this) ?: "")
            if (all.isBlank()) {
                Toast.makeText(this, "Belum ada catatan", Toast.LENGTH_SHORT).show()
            } else {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Catatan error Bracket Live", all))
                Toast.makeText(this, "Catatan disalin", Toast.LENGTH_SHORT).show()
            }
        }
        button(c, "Hapus catatan error") {
            CrashLog.clearErrors(this)
            CrashLog.clearFatal(this)
            logView.text = "Tidak ada error tercatat."
            logView.setTextColor(ContextCompat.getColor(this, R.color.green))
        }

        divider(c)
        button(c, "Reset semua pengaturan") {
            PremiumDialog.message(
                this, "Reset pengaturan", "Kembali ke bawaan",
                "Semua pengaturan (tema, overlay, poin, koneksi live) dikembalikan ke bawaan. Data turnamen tidak dihapus. Lanjutkan?",
                "Reset", "Batal",
                onPositive = {
                    Prefs.clearAll(this)
                    DeviceProfile.reset()
                    OverlayService.onSettingsChanged?.invoke()
                    recreate()
                }
            )
        }
    }

    private fun buildUpdate() {
        val c = card("Update aplikasi", summary = "Versi ${updater.versionName()} · build ${updater.currentBuild()}", open = true)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        col.addView(tv("Versi ${updater.versionName()}  (build ${updater.currentBuild()})", 15f, R.color.text, true).apply { typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD) })
        tvUpdateStatus = tv("Ketuk tombol untuk mengecek versi terbaru", 11f, R.color.muted, false)
        col.addView(tvUpdateStatus)
        row.addView(col)
        row.addView(TextView(this).apply {
            text = "Cek Update"
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD))
            setPadding(dp(18), 0, dp(18), 0)
            background = Skin.primary(this@SettingsActivity)
            setTextColor(Skin.onAccent(this@SettingsActivity))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
            setOnClickListener { updater.check(true) }
        })
        c.addView(row)

        // catatan: apa saja yang baru (tampil setelah Cek Update)
        tvUpdateNotes = tv("", 12f, R.color.text, false).apply {
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setLineSpacing(0f, 1.15f)
            setBackgroundResource(R.drawable.bg_chip)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        c.addView(tvUpdateNotes)
        updater.onResult = { info, cur, available ->
            val notes = UpdateChecker.prettyNotes(info.notes)
            val view = tvUpdateNotes
            if (view != null && notes.isNotEmpty()) {
                val head = if (available) "Yang baru di build ${info.build}:" else "Catatan versi terbaru (build ${info.build}):"
                view.text = "$head\n$notes"
                view.visibility = View.VISIBLE
                Anim.reveal(view)
            } else view?.visibility = View.GONE
        }
    }

    private fun buildAbout() {
        val c = card("Tentang", plain = true, open = true)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) } }
        col.addView(tv("Bracket Live", 14f, R.color.text, true))
        col.addView(tv("Versi ${updater.versionName()} · build ${updater.currentBuild()}", 12f, R.color.muted_light, false))
        col.addView(tv("Bracket turnamen untuk live streaming", 11f, R.color.muted, false))
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
            PremiumDialog.message(
                this, "Izin overlay", "Diperlukan sekali saja",
                "Aktifkan izin \"Tampil di atas aplikasi lain\" untuk Bracket Live, lalu kembali ke aplikasi ini. Overlay akan menyala otomatis.",
                "Buka Settings", "Batal",
                onPositive = {
                    pendingOverlay = true
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    } catch (e: Exception) {
                        CrashLog.record(this, "buka izin overlay", e)
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }
                }
            )
            return
        }
        val i = Intent(this, OverlayService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        } catch (e: Exception) {
            CrashLog.record(this, "mulai overlay", e)
            setOverlaySwitch(false)
            Toast.makeText(this, "Overlay gagal dijalankan: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        setOverlaySwitch(true)
        Toast.makeText(this, "Overlay aktif — keluar dari app, ikon akan muncul", Toast.LENGTH_LONG).show()
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        setOverlaySwitch(false)
    }

    // ───────────────────────── Pembangun UI ─────────────────────────

    /** Kartu yang bisa dilipat: ketuk judul untuk membuka/menutup. Mengembalikan wadah isi. */
    private fun card(title: String, neon: Boolean = false, plain: Boolean = false, summary: String? = null, open: Boolean = false): LinearLayout {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(14))
        }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titles.addView(TextView(this, null, 0, R.style.SectionLabel).apply { text = title })
        lastSummary = null
        if (summary != null) {
            val s = tv(summary, 11f, R.color.muted, false).apply {
                setPadding(0, dp(4), 0, 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            titles.addView(s)
            lastSummary = s
        }
        val arrow = tv("▾", 16f, R.color.muted_light, true).apply {
            setPadding(dp(8), 0, dp(4), 0)
            rotation = if (open) 180f else 0f
        }
        head.addView(titles)
        head.addView(arrow)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
            visibility = if (open) View.VISIBLE else View.GONE
        }
        head.setOnClickListener {
            val show = body.visibility != View.VISIBLE
            if (show) Anim.expand(body) else Anim.collapse(body)
            Anim.rotate(arrow, if (show) 180f else 0f)
        }
        outer.addView(head)
        outer.addView(body)
        box.addView(outer)
        return body
    }

    /** Tombol aksi seragam: utama (berwarna) atau abu-abu. Ukuran diatur pemanggil. */
    private fun actionBtn(text: String, primary: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 13f
        setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD))
        setTextColor(if (primary) Color.WHITE else ContextCompat.getColor(this@SettingsActivity, R.color.muted_light))
        if (primary) { background = Skin.primary(this@SettingsActivity); setTextColor(Skin.onAccent(this@SettingsActivity)) } else setBackgroundResource(R.drawable.bg_btn_gray)
        setOnClickListener { onClick() }
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
        parent.addView(tv(text, 11f, R.color.muted, false).apply { setPadding(0, dp(6), 0, 0) })
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
        col.addView(tv(sub, 11f, R.color.muted, false))
        val sw = SwitchCompat(this).apply {
            minimumHeight = dp(44)
            thumbTintList = Skin.switchThumb(this@SettingsActivity)
            trackTintList = Skin.switchTrack(this@SettingsActivity)
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
            Skin.styleChoice(v, i == sel)
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
