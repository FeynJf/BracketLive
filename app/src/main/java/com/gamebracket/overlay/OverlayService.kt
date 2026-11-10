package com.gamebracket.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * Floating native overlay (no WebView / HTML).
 */
class OverlayService : Service() {

    companion object {
        @Volatile var isRunning = false
    }

    private lateinit var wm: WindowManager
    private var view: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private var minimized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (!Settings.canDrawOverlays(this)) {
            toast("Izin overlay belum aktif")
            stopSelf()
            return START_NOT_STICKY
        }
        Handler(Looper.getMainLooper()).post {
            try {
                if (view == null) showOverlay()
                isRunning = true
                toast("Overlay aktif")
            } catch (e: Exception) {
                toast("Gagal: ${e.message}")
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startFg() {
        val id = "gb_native_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(id, "Bracket Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, id)
            .setContentTitle("Game Bracket")
            .setContentText("Overlay native aktif")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(2002, n)
    }

    private fun showOverlay() {
        val v = LayoutInflater.from(this).inflate(R.layout.overlay_bracket, null)
        view = v
        v.findViewById<TextView>(R.id.tvOverlayInfo).text = BracketStore.summaryText()
        v.findViewById<TextView>(R.id.tvOverlayChamp).text =
            "Juara: ${BracketStore.champion() ?: "—"}"

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40
        params.y = 160

        val handle = v.findViewById<View>(R.id.dragHandle)
        handle.setOnTouchListener(object : View.OnTouchListener {
            var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = ix + (e.rawX - tx).toInt()
                        params.y = iy + (e.rawY - ty).toInt()
                        try { wm.updateViewLayout(v, params) } catch (_: Exception) {}
                        return true
                    }
                }
                return false
            }
        })

        v.findViewById<TextView>(R.id.btnClose).setOnClickListener { stopSelf() }
        v.findViewById<TextView>(R.id.btnMin).setOnClickListener {
            minimized = !minimized
            v.findViewById<View>(R.id.tvOverlayInfo).visibility =
                if (minimized) View.GONE else View.VISIBLE
            v.findViewById<View>(R.id.tvOverlayChamp).visibility =
                if (minimized) View.GONE else View.VISIBLE
        }

        wm.addView(v, params)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) =
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, m, Toast.LENGTH_LONG).show()
        }

    override fun onDestroy() {
        isRunning = false
        try { view?.let { wm.removeView(it) } } catch (_: Exception) {}
        view = null
        super.onDestroy()
    }
}
