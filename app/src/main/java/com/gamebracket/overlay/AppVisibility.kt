package com.gamebracket.overlay

import android.os.Handler
import android.os.Looper

/**
 * Melacak apakah aplikasi sedang terlihat di layar.
 * Overlay disembunyikan saat aplikasi dibuka dan baru muncul setelah pengguna keluar dari aplikasi.
 */
object AppVisibility {
    private var started = 0
    private val handler = Handler(Looper.getMainLooper())
    private val goBackground = Runnable { set(false) }

    @Volatile var inForeground = false
        private set

    /** Dipanggil di main thread saat status berubah. */
    var listener: ((Boolean) -> Unit)? = null

    fun onStart() {
        started++
        handler.removeCallbacks(goBackground)
        set(true)
    }

    fun onStop() {
        started = maxOf(0, started - 1)
        // jeda singkat supaya rotasi layar / pindah antar-layar tidak membuat overlay berkedip
        if (started == 0) handler.postDelayed(goBackground, 500)
    }

    private fun set(fg: Boolean) {
        if (fg == inForeground) return
        inForeground = fg
        listener?.invoke(fg)
    }
}
