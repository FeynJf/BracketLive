package com.gamebracket.overlay

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Editor sertifikat: ubah teks, logo, dan tema dengan pratinjau langsung, lalu simpan ke Galeri. */
class CertificateActivity : BaseActivity() {

    private lateinit var ivPreview: ImageView
    private lateinit var form: LinearLayout
    private val data = CertificateRenderer.Data()
    private val handler = Handler(Looper.getMainLooper())
    private var latest: Bitmap? = null
    private var pickTarget: ((String) -> Unit)? = null
    private lateinit var btnChampLogo: TextView
    private lateinit var btnOrgLogo: TextView
    private var designValue: TextView? = null
    private var shapeValue: TextView? = null
    private var themeValue: TextView? = null
    private var fontValue: TextView? = null
    private var sigValue: TextView? = null

    private val bgPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val path = LogoHelper.saveBackground(this, uri)
        if (path == null) toast("Gambar tidak bisa dibaca") else pickTarget?.invoke(path)
    }

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val path = LogoHelper.saveFromUri(this, uri)
        if (path == null) toast("Gambar tidak bisa dibaca") else pickTarget?.invoke(path)
    }

    private val refresh = Runnable {
        latest = safeRender(DeviceProfile.previewQuality(this))
        latest?.let { ivPreview.setImageBitmap(it) }
        if (Anim.enabled(this)) {
            ivPreview.alpha = 0.65f
            ivPreview.animate().alpha(1f).setDuration(220).start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_certificate)
        ivPreview = findViewById(R.id.ivPreview)
        form = findViewById(R.id.formContainer)

        val game = BracketStore.gameId?.let { BracketStore.games[it]?.first } ?: ""
        val mode = BracketStore.mode?.name ?: ""
        data.name = BracketStore.champion() ?: ""
        data.championLogo = BracketStore.championLogo()
        val tournament = Prefs.tournamentName(this)
        data.event = if (tournament.isNotBlank()) tournament
        else listOf(game, mode).filter { it.isNotBlank() }.joinToString(" · ")
        data.date = SimpleDateFormat("d MMMM yyyy", Locale("id")).format(Date())

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSave).setOnClickListener { save() }

        addField("Judul sertifikat", data.title) { data.title = it }
        addField("Nama juara", data.name) { data.name = it }
        addField("Peringkat", data.ranking) { data.ranking = it }
        addField("Turnamen / event", data.event) { data.event = it }
        addField("Tanggal", data.date) { data.date = it }
        addField("Nama panitia (jadi tanda tangan)", data.signer, "Ketik nama, otomatis jadi tanda tangan") { data.signer = it }
        addField("Jabatan", data.signerRole) { data.signerRole = it }

        addLabel("Logo")
        val logoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnChampLogo = smallButton("Logo juara") { pickTarget = { p -> data.championLogo = p; updateLogoButtons(); schedule() }; picker.launch("image/*") }
        btnOrgLogo = smallButton("Logo penyelenggara") { pickTarget = { p -> data.orgLogo = p; updateLogoButtons(); schedule() }; picker.launch("image/*") }
        logoRow.addView(btnChampLogo)
        logoRow.addView(btnOrgLogo)
        form.addView(logoRow)
        val clearRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        clearRow.addView(smallButton("Hapus logo juara") { data.championLogo = null; updateLogoButtons(); schedule() })
        clearRow.addView(smallButton("Hapus logo penyelenggara") { data.orgLogo = null; updateLogoButtons(); schedule() })
        form.addView(clearRow)
        updateLogoButtons()

        addLabel("Tampilan sertifikat")
        designValue = pickerRow("Desain (${CertificateRenderer.designNames.size} pilihan)", CertificateRenderer.designNames[data.design]) {
            ListPicker.show(this, "Desain sertifikat", "10 tata letak x 10 bingkai",
                CertificateRenderer.designNames.map { PickRow(it) }, data.design) { i ->
                data.design = i; designValue?.text = CertificateRenderer.designNames[i]; schedule()
            }
        }
        shapeValue = pickerRow("Bentuk (${CertificateRenderer.shapes.size} pilihan)", CertificateRenderer.shapes[data.shape].name) {
            ListPicker.show(this, "Bentuk sertifikat", "${CertificateRenderer.shapes.size} ukuran dan rasio",
                CertificateRenderer.shapes.map { PickRow(it.name, "${it.w} x ${it.h}") }, data.shape) { i ->
                data.shape = i; shapeValue?.text = CertificateRenderer.shapes[i].name; schedule()
            }
        }
        themeValue = pickerRow("Tema warna (${CertificateRenderer.themes.size} pilihan)", CertificateRenderer.themes[data.theme].name) {
            ListPicker.show(this, "Tema warna", "${CertificateRenderer.themes.size} palet",
                CertificateRenderer.themes.map { PickRow(it.name, dots = intArrayOf(it.bg1, it.acc1, it.acc2)) }, data.theme) { i ->
                data.theme = i; themeValue?.text = CertificateRenderer.themes[i].name; schedule()
            }
        }
        fontValue = pickerRow("Font teks (${Fonts.list(this, "cert").size} pilihan)", data.font.ifEmpty { "Bawaan sistem" }) {
            Pickers.font(this, "cert", data.font, "Font sertifikat") { n ->
                data.font = n; fontValue?.text = n.ifEmpty { "Bawaan sistem" }; schedule()
            }
        }
        val signNames = Fonts.list(this, "sign")
        sigValue = pickerRow("Gaya tanda tangan (${signNames.size} pilihan)", signNames.getOrNull(data.sigStyle) ?: "Bawaan") {
            val sample = data.signer.ifBlank { "Nama Panitia" }
            ListPicker.show(this, "Gaya tanda tangan", "${signNames.size} gaya tulisan tangan",
                signNames.map { n -> PickRow(n, face = { Fonts.cached(this, n) }) }, data.sigStyle, confirm = true, preview = sample,
                onPreview = { idx, tv ->
                    val n = signNames[idx]
                    tv.tag = idx
                    tv.text = sample
                    tv.textSize = 30f
                    val f = Fonts.cached(this, n)
                    if (f != null) tv.typeface = f else {
                        tv.text = "Mengunduh…"
                        Fonts.ensure(this, n) { ok ->
                            if (tv.tag == idx) { tv.text = if (ok) sample else "Gagal mengunduh"; if (ok) tv.typeface = Fonts.cached(this, n) }
                        }
                    }
                }
            ) { idx ->
                val n = signNames[idx]
                Fonts.ensure(this, n) { _ ->
                    data.sigStyle = idx; sigValue?.text = n; schedule()
                }
            }
        }

        addLabel("Background sendiri")
        val bgRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bgRow.addView(smallButton("Pilih gambar") {
            pickTarget = { p -> data.bgImage = p; schedule() }
            bgPicker.launch("image/*")
        })
        bgRow.addView(smallButton("Hapus background") { data.bgImage = null; schedule() })
        form.addView(bgRow)
        val scrimRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        scrimRow.addView(TextView(this).apply {
            text = "Redam gambar"
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.muted_light))
        })
        val tvScrim = TextView(this).apply {
            text = "${data.scrim}%"
            textSize = 11f
            setTextColor(ThemeManager.accent1(context))
            setPadding(dp(10), 0, 0, 0)
        }
        scrimRow.addView(SeekBar(this).apply {
            max = 85
            progress = data.scrim
            val tint = ColorStateList.valueOf(ThemeManager.accent1(context))
            progressTintList = tint
            thumbTintList = tint
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    data.scrim = p
                    tvScrim.text = "$p%"
                    if (fromUser) schedule()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        scrimRow.addView(tvScrim)
        form.addView(scrimRow)
        Anim.pressAll(window.decorView)
        refresh.run()
    }

    private fun schedule() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 120)
    }

    private fun updateLogoButtons() {
        btnChampLogo.text = if (data.championLogo != null) "Logo juara ✓" else "Logo juara"
        btnOrgLogo.text = if (data.orgLogo != null) "Logo penyelenggara ✓" else "Logo penyelenggara"
    }

    /** Baris "nilai + keterangan + tombol Pilih". Mengembalikan TextView nilai. */
    private fun pickerRow(caption: String, value: String, onOpen: () -> Unit): TextView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
        val valueTv = TextView(this).apply {
            text = value
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text))
        }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(valueTv)
            addView(TextView(context).apply { text = caption; textSize = 11f; setTextColor(ContextCompat.getColor(context, R.color.muted)) })
        })
        row.addView(TextView(this).apply {
            text = "Pilih"
            gravity = Gravity.CENTER
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.muted_light))
            setPadding(dp(18), 0, dp(18), 0)
            background = Skin.chip(this@CertificateActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
            setOnClickListener { onOpen() }
        })
        form.addView(row)
        return valueTv
    }

    override fun onStart() {
        super.onStart()
        Fonts.addListener("cert") { schedule() }
        Fonts.list(this, "sign").getOrNull(data.sigStyle)?.let { Fonts.ensure(this, it) }
        if (data.font.isNotEmpty()) Fonts.ensure(this, data.font)
    }

    override fun onStop() {
        Fonts.addListener("cert", null)
        super.onStop()
    }

    private fun addLabel(text: String) {
        form.addView(TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(context, R.color.muted))
            textSize = 10f
            letterSpacing = 0.05f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(2), dp(14), 0, dp(6))
        })
    }

    private fun addField(label: String, value: String, hintText: String = "", onChange: (String) -> Unit) {
        addLabel(label)
        form.addView(EditText(this).apply {
            setText(value)
            hint = hintText
            setHintTextColor(ContextCompat.getColor(context, R.color.dim))
            setTextColor(ContextCompat.getColor(context, R.color.text))
            textSize = 14f
            setSingleLine()
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundResource(R.drawable.bg_input)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: ""); schedule() }
            })
        })
    }

    private fun smallButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(context, R.color.muted_light))
        setBackgroundResource(R.drawable.bg_chip)
        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8); topMargin = dp(6) }
        setOnClickListener { onClick() }
    }

    /** Render dengan cadangan: kalau memori habis (HP RAM kecil), ulangi dengan ukuran lebih kecil. */
    private fun safeRender(quality: Float): Bitmap? {
        var q = quality
        repeat(3) {
            try {
                return CertificateRenderer.render(this, data, q)
            } catch (e: OutOfMemoryError) {
                LogoHelper.clearCache()
                q *= 0.7f
            } catch (e: Exception) {
                CrashLog.record(this, "render sertifikat", e)
                return null
            }
        }
        return null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 71) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) save()
            else toast("Izin penyimpanan ditolak, sertifikat tidak bisa disimpan di Android ini")
        }
    }

    private fun save() {
        // Android 9 ke bawah butuh izin penyimpanan saat menyimpan ke Galeri
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 71)
            return
        }
        val bmp = safeRender(1f) ?: run { toast("Gagal membuat sertifikat (memori penuh?)"); return }
        val safe = data.name.ifBlank { "Juara" }.replace(Regex("[^A-Za-z0-9]+"), "_")
        val fileName = "Sertifikat_${safe}_${System.currentTimeMillis() / 1000}.png"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BracketLive")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Tidak bisa membuat file")
                contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val folder = File(dir, "BracketLive").apply { mkdirs() }
                FileOutputStream(File(folder, fileName)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            toast("Sertifikat disimpan ke Galeri (Pictures/BracketLive)")
        } catch (e: Exception) {
            toast("Gagal simpan: ${e.message}")
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
