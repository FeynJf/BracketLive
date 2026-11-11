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
import android.widget.EditText
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
class CertificateActivity : AppCompatActivity() {

    private lateinit var ivPreview: ImageView
    private lateinit var form: LinearLayout
    private val data = CertificateRenderer.Data()
    private val handler = Handler(Looper.getMainLooper())
    private var latest: Bitmap? = null
    private var pickTarget: ((String) -> Unit)? = null
    private lateinit var btnChampLogo: TextView
    private lateinit var btnOrgLogo: TextView
    private val themeButtons = mutableListOf<TextView>()

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val path = LogoHelper.saveFromUri(this, uri)
        if (path == null) toast("Gambar tidak bisa dibaca") else pickTarget?.invoke(path)
    }

    private val refresh = Runnable {
        latest = CertificateRenderer.render(this, data)
        ivPreview.setImageBitmap(latest)
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
        data.event = listOf(game, mode).filter { it.isNotBlank() }.joinToString(" · ")
        data.date = SimpleDateFormat("d MMMM yyyy", Locale("id")).format(Date())

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSave).setOnClickListener { save() }

        addField("JUDUL SERTIFIKAT", data.title) { data.title = it }
        addField("NAMA JUARA", data.name) { data.name = it }
        addField("PERINGKAT", data.ranking) { data.ranking = it }
        addField("TURNAMEN / EVENT", data.event) { data.event = it }
        addField("TANGGAL", data.date) { data.date = it }
        addField("NAMA PANITIA (JADI TANDA TANGAN)", data.signer, "Ketik nama, otomatis jadi tanda tangan") { data.signer = it }
        addField("JABATAN", data.signerRole) { data.signerRole = it }

        addLabel("LOGO")
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

        addLabel("TEMA")
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Neon Gamer" to CertificateRenderer.THEME_NEON, "Klasik Emas" to CertificateRenderer.THEME_CLASSIC).forEach { (label, t) ->
            val b = smallButton(label) { data.theme = t; updateThemeButtons(); schedule() }
            themeButtons.add(b)
            themeRow.addView(b)
        }
        form.addView(themeRow)
        updateThemeButtons()

        refresh.run()
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.onStart()
    }

    override fun onStop() {
        AppVisibility.onStop()
        super.onStop()
    }

    private fun schedule() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 120)
    }

    private fun updateLogoButtons() {
        btnChampLogo.text = if (data.championLogo != null) "Logo juara ✓" else "Logo juara"
        btnOrgLogo.text = if (data.orgLogo != null) "Logo penyelenggara ✓" else "Logo penyelenggara"
    }

    private fun updateThemeButtons() {
        themeButtons.forEachIndexed { i, b ->
            val on = data.theme == i
            b.setBackgroundResource(if (on) R.drawable.bg_chip_active else R.drawable.bg_chip)
            b.setTextColor(ContextCompat.getColor(this, if (on) R.color.on_accent else R.color.muted_light))
        }
    }

    private fun addLabel(text: String) {
        form.addView(TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(context, R.color.muted))
            textSize = 10f
            letterSpacing = 0.15f
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

    private fun save() {
        val bmp = CertificateRenderer.render(this, data)
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
