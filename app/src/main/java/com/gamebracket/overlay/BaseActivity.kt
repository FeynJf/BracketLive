package com.gamebracket.overlay

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Dasar semua layar: menerapkan tema pilihan pengguna & melacak keterlihatan aplikasi. */
open class BaseActivity : AppCompatActivity() {

    private var appliedKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        appliedKey = ThemeManager.key(this)
        super.onCreate(savedInstanceState)
    }

    /** Setelah layout dipasang: warnai view bertag "skin:..." dengan warna aksen pilihan. */
    override fun onContentChanged() {
        super.onContentChanged()
        Skin.apply(window.decorView)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.onStart()
    }

    override fun onStop() {
        AppVisibility.onStop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // tema diganti di Pengaturan → layar lain ikut dibuat ulang
        if (ThemeManager.key(this) != appliedKey) recreate()
    }
}
