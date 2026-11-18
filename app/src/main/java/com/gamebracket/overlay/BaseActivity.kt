package com.gamebracket.overlay

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity

/** Dasar semua layar: menerapkan tema pilihan pengguna & melacak keterlihatan aplikasi. */
open class BaseActivity : AppCompatActivity() {

    private var appliedKey = ""
    private val handler = Handler(Looper.getMainLooper())
    private val fontRun = Runnable { Fonts.apply(window.decorView) }
    // tampilan yang dibuat lewat kode ikut mendapat font setelah layout selesai
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (Prefs.uiFont(this).isNotEmpty() || Prefs.titleFont(this).isNotEmpty()) {
            handler.removeCallbacks(fontRun)
            handler.postDelayed(fontRun, 60)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        appliedKey = ThemeManager.key(this)
        super.onCreate(savedInstanceState)
    }

    /** Setelah layout dipasang: warnai view bertag "skin:..." dengan warna aksen pilihan. */
    override fun onContentChanged() {
        super.onContentChanged()
        Skin.apply(window.decorView)
        Fonts.apply(window.decorView)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.onStart()
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        Fonts.addListener(javaClass.name) { Fonts.apply(window.decorView) }
        Fonts.preload(this)
        Fonts.apply(window.decorView)
    }

    override fun onStop() {
        AppVisibility.onStop()
        handler.removeCallbacks(fontRun)
        val vto = window.decorView.viewTreeObserver
        if (vto.isAlive) vto.removeOnGlobalLayoutListener(layoutListener)
        Fonts.addListener(javaClass.name, null)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // tema diganti di Pengaturan → layar lain ikut dibuat ulang
        if (ThemeManager.key(this) != appliedKey) recreate()
    }
}
