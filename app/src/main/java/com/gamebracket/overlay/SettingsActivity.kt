package com.gamebracket.overlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/** Pengaturan: toggle overlay, transparansi & lama tahan ikon, serta update aplikasi. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var switchOverlay: SwitchCompat
    private lateinit var tvOverlayState: TextView
    private lateinit var dotOverlay: View
    private lateinit var tvOpacityVal: TextView
    private lateinit var tvHoldVal: TextView
    private lateinit var tvVersion: TextView
    private lateinit var tvUpdateStatus: TextView

    private val updater by lazy { UpdateFlow(this) { tvUpdateStatus.text = it } }

    private var suppressSwitch = false
    private var pendingOverlay = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        switchOverlay = findViewById(R.id.switchOverlay)
        tvOverlayState = findViewById(R.id.tvOverlayState)
        dotOverlay = findViewById(R.id.dotOverlay)
        tvOpacityVal = findViewById(R.id.tvOpacityVal)
        tvHoldVal = findViewById(R.id.tvHoldVal)
        tvVersion = findViewById(R.id.tvVersion)
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        switchOverlay.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitch) return@setOnCheckedChangeListener
            if (checked) startOverlay() else stopOverlay()
        }
        setOverlaySwitch(OverlayService.isRunning)

        // Transparansi panel: 30..100 %
        val sbOpacity = findViewById<SeekBar>(R.id.sbOpacity)
        sbOpacity.progress = Prefs.panelOpacity(this) - 30
        tvOpacityVal.text = "${Prefs.panelOpacity(this)}%"
        sbOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                Prefs.setPanelOpacity(this@SettingsActivity, 30 + p)
                tvOpacityVal.text = "${30 + p}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Lama tahan ikon: 0,3 – 1,5 detik
        val sbHold = findViewById<SeekBar>(R.id.sbHold)
        sbHold.progress = (Prefs.holdMs(this) - 300) / 100
        tvHoldVal.text = holdText(Prefs.holdMs(this))
        sbHold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val ms = 300 + p * 100
                Prefs.setHoldMs(this@SettingsActivity, ms)
                tvHoldVal.text = holdText(ms)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        tvVersion.text = "Versi ${updater.versionName()}  (build ${updater.currentBuild()})"
        findViewById<View>(R.id.btnCheckUpdate).setOnClickListener { updater.check(true) }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.onStart()
        OverlayService.stateListener = { on -> runOnUiThread { setOverlaySwitch(on) } }
    }

    override fun onStop() {
        OverlayService.stateListener = null
        AppVisibility.onStop()
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

    private fun holdText(ms: Int) = String.format(Locale("id"), "%.1f dtk", ms / 1000.0)

    private fun setOverlaySwitch(on: Boolean) {
        suppressSwitch = true
        switchOverlay.isChecked = on
        suppressSwitch = false
        tvOverlayState.text = if (on) "Overlay Aktif" else "Overlay Nonaktif"
        tvOverlayState.setTextColor(ContextCompat.getColor(this, if (on) R.color.green else R.color.muted))
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
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                }
                .setNegativeButton("Batal", null)
                .show()
            return
        }
        val i = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        setOverlaySwitch(true)
        Toast.makeText(this, "Overlay aktif — keluar dari app, ikon B akan muncul", Toast.LENGTH_LONG).show()
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        setOverlaySwitch(false)
    }
}
