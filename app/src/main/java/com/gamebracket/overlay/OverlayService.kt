package com.gamebracket.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Overlay mengambang (native).
 * - Tertutup  : hanya ikon aplikasi "B" yang bisa digeser.
 * - Diketuk   : membuka panel bracket yang bisa diedit (nama, skor, pemenang).
 */
class OverlayService : Service() {

    companion object {
        @Volatile var isRunning = false

        /** Dipanggil (di main thread) saat overlay menyala/mati, supaya toggle di app ikut sinkron. */
        @Volatile var stateListener: ((Boolean) -> Unit)? = null

        /** Pengaturan (ukuran/ikon/desain) berubah → overlay yang sedang jalan menyesuaikan langsung. */
        @Volatile var onSettingsChanged: (() -> Unit)? = null
    }

    private lateinit var wm: WindowManager
    private var root: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var bubble: View
    private lateinit var panel: View
    private lateinit var content: LinearLayout
    private lateinit var tvGame: TextView
    private lateinit var tvChamp: TextView
    private lateinit var tvStatus: TextView

    private var expanded = false
    private var bubbleX = 0
    private var bubbleY = 0
    private var panelW = 0
    private lateinit var bubbleIcon: View
    private lateinit var ui: Context
    private var scale = 1f   // ukuran ikon
    private var zoom = 1f    // zoom isi panel
    private lateinit var scroll: MaxHeightScrollView
    private lateinit var gripRight: View
    private lateinit var gripLeft: View
    private lateinit var holdRing: HoldRingView
    private var holdAnim: ValueAnimator? = null
    private var holdDone = false
    private var tapCount = 0
    private var hScroll: HorizontalScrollView? = null
    private var savedScrollX = 0

    private val flagsBubble = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    // Panel butuh fokus supaya keyboard bisa muncul saat edit nama tim
    private val flagsPanel = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        ui = ThemeManager.themedContext(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startFg()
        } catch (e: Exception) {
            // Android 12+ bisa menolak service foreground; Android 14 butuh tipe yang benar
            CrashLog.record(this, "startForeground", e)
            toast("Overlay gagal dijalankan: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Izin overlay belum aktif")
            stopSelf()
            return START_NOT_STICKY
        }
        setRunning(true)
        Handler(Looper.getMainLooper()).post {
            try {
                if (root == null) showOverlay()
                toast("Overlay aktif — keluar dari app untuk melihat ikon B")
            } catch (e: Exception) {
                CrashLog.record(this, "tampilkan overlay", e)
                toast("Gagal menampilkan overlay: ${e.message}")
                stopSelf()
            }
        }
        return START_STICKY
    }

    /** Rotasi, layar lipat, atau ukuran layar berubah → sesuaikan ukuran & jaga overlay tetap di dalam layar. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (root == null) return
        Handler(Looper.getMainLooper()).post {
            try {
                applyLook()
                val sw = resources.displayMetrics.widthPixels
                val sh = resources.displayMetrics.heightPixels
                val bs = dp((54 * scale).toInt())
                bubbleX = bubbleX.coerceIn(0, max(0, sw - bs))
                bubbleY = bubbleY.coerceIn(0, max(0, sh - bs))
                if (expanded) {
                    params.x = max(0, (sw - panelW) / 2)
                    params.y = min(params.y, max(0, sh - dp(160)))
                } else {
                    params.x = bubbleX
                    params.y = bubbleY
                }
                updateWindow()
            } catch (e: Exception) {
                CrashLog.record(this, "rotasi overlay", e)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) LogoHelper.clearCache()
    }

    private fun setRunning(v: Boolean) {
        isRunning = v
        Handler(Looper.getMainLooper()).post { stateListener?.invoke(v) }
    }

    private fun startFg() {
        val id = "gb_native_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(id, "Bracket Live Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, id)
            .setContentTitle("Bracket Live")
            .setContentText("Overlay aktif")
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(Color.parseColor("#00E5FF"))
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(2002, n)
    }

    // ───────────────────────── Window ─────────────────────────

    private fun showOverlay() {
        val v = LayoutInflater.from(ui).inflate(R.layout.overlay_bracket, null)
        root = v
        bubble = v.findViewById(R.id.bubble)
        bubbleIcon = v.findViewById(R.id.bubbleCore)
        holdRing = v.findViewById(R.id.holdRing)
        panel = v.findViewById(R.id.panel)
        content = v.findViewById(R.id.overlayContent)
        scroll = v.findViewById(R.id.overlayScroll)
        gripRight = v.findViewById(R.id.gripRight)
        gripLeft = v.findViewById(R.id.gripLeft)
        attachResize(gripRight, true)
        attachResize(gripLeft, false)
        tvGame = v.findViewById(R.id.tvOverlayGame)
        tvChamp = v.findViewById(R.id.tvOverlayChamp)
        tvStatus = v.findViewById(R.id.tvOverlayStatus)

        applyLook()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flagsBubble,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        params.x = dp(12)
        params.y = dp(200)

        attachBubble(bubble)
        attachDrag(v.findViewById(R.id.dragHandle), false, null)

        v.findViewById<View>(R.id.btnClose).setOnClickListener { stopSelf() }
        v.findViewById<View>(R.id.btnMin).setOnClickListener { collapse() }
        v.findViewById<View>(R.id.btnOpacity).setOnClickListener { cycleOpacity() }
        v.findViewById<View>(R.id.btnOpenApp).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            collapse()
        }

        wm.addView(v, params)

        // Ikon B hanya muncul saat pengguna berada di luar aplikasi
        OverlayService.onSettingsChanged = { applyLook() }
        LiveFeed.setListener("overlay") { changed -> if (changed && expanded && root?.findFocus() !is EditText) render() }
        // proses bisa dimulai ulang oleh sistem tanpa membuka app → sambungkan lagi koneksi live
        if (Prefs.feedEnabled(this) && !LiveFeed.running) LiveFeed.start(this)
        AppVisibility.listener = { fg -> applyForeground(fg) }
        applyForeground(AppVisibility.inForeground)
    }

    /** Ukuran, desain tombol, ikon, dan latar panel dari Pengaturan. */
    private fun applyLook() {
        val r = root ?: return
        scale = Prefs.overlayScale(this) / 100f
        ui = ThemeManager.themedContext(this)

        val outer = dp((54 * scale).toInt())
        val core = dp((44 * scale).toInt())
        bubble.layoutParams = bubble.layoutParams.apply { width = outer; height = outer }
        bubbleIcon.layoutParams = bubbleIcon.layoutParams.apply { width = core; height = core }
        r.findViewById<View>(R.id.holdRing).layoutParams = r.findViewById<View>(R.id.holdRing).layoutParams.apply { width = outer; height = outer }
        (r.findViewById<View>(R.id.holdRing) as HoldRingView).setColors(
            ThemeManager.accent1(ui), ThemeManager.accent3(ui), ThemeManager.accent2(ui)
        )

        val style = Prefs.bubbleStyle(this)
        val iconIdx = Prefs.bubbleIcon(this)
        val custom = Prefs.bubbleCustom(this)
        r.findViewById<ImageView>(R.id.bubbleBg).setImageDrawable(BubbleFactory.background(ui, style, core))
        val img = r.findViewById<ImageView>(R.id.bubbleImg)
        val imgSize = if (iconIdx == BubbleFactory.CUSTOM_ICON && custom != null) (core * 0.78f).toInt() else (core * 0.6f).toInt()
        img.layoutParams = img.layoutParams.apply { width = imgSize; height = imgSize }
        BubbleFactory.applyIcon(ui, img, iconIdx, custom, imgSize)

        // panel: lebar, tinggi, zoom isi (terpisah dari ukuran ikon)
        zoom = Prefs.panelZoom(this) / 100f
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val wPct = Prefs.panelWidthPct(this)
        panelW = ((screenW - dp(16)) * wPct / 100).let { if (wPct >= 100) min(it, dp(640)) else it }
        panel.layoutParams = panel.layoutParams.apply { width = panelW }
        val hPct = Prefs.panelHeightPct(this)
        scroll.exactHeight = hPct > 0
        scroll.maxHeightPx = if (hPct > 0) screenH * hPct / 100 else min(dp((420 * zoom).toInt()), (screenH * 0.5f).toInt())
        applyPanelStyle()
        if (expanded) params.flags = panelFlags()
        if (expanded) render()
        updateWindow()
    }

    private fun applyForeground(fg: Boolean) {
        val r = root ?: return
        if (fg) {
            if (expanded) collapse(false)
            r.visibility = View.GONE
        } else {
            r.visibility = View.VISIBLE
        }
    }

    private fun expand() {
        if (expanded) return
        expanded = true
        bubbleX = params.x
        bubbleY = params.y
        applyPanelStyle()
        render()
        Anim.enterChildren(content, 90, 45)
        bubble.visibility = View.GONE
        panel.visibility = View.VISIBLE
        gripRight.visibility = View.VISIBLE
        gripLeft.visibility = View.VISIBLE
        params.flags = panelFlags()
        params.x = max(0, (resources.displayMetrics.widthPixels - panelW) / 2)
        params.y = dp(48)
        updateWindow()
        // animasi buka: muncul dari atas sambil membesar
        panel.alpha = 0f
        panel.scaleX = 0.88f
        panel.scaleY = 0.88f
        panel.pivotX = panelW / 2f
        panel.pivotY = 0f
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(240).setInterpolator(DecelerateInterpolator(1.6f)).start()
    }

    private fun collapse(animated: Boolean = true) {
        if (!expanded) return
        expanded = false
        hideKeyboard()
        val finish = {
            panel.animate().cancel()
            panel.visibility = View.GONE
            gripRight.visibility = View.GONE
            gripLeft.visibility = View.GONE
            panel.alpha = 1f
            panel.scaleX = 1f
            panel.scaleY = 1f
            bubble.visibility = View.VISIBLE
            params.flags = flagsBubble
            params.x = bubbleX
            params.y = bubbleY
            updateWindow()
            if (animated) {
                // ikon "pop" kembali
                bubbleIcon.scaleX = 0.5f
                bubbleIcon.scaleY = 0.5f
                bubbleIcon.animate().scaleX(1f).scaleY(1f)
                    .setDuration(300).setInterpolator(OvershootInterpolator(2.5f)).start()
            }
        }
        if (animated) {
            panel.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f)
                .setDuration(160).withEndAction { finish() }.start()
        } else {
            finish()
        }
    }

    /** Flag jendela saat panel terbuka (+ blur latar untuk desain "Kaca Blur", Android 12+). */
    private fun panelFlags(): Int {
        var f = flagsPanel
        if (Prefs.panelStyle(this) == PanelDesign.BLUR && DeviceProfile.allowBlur(this) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && wm.isCrossWindowBlurEnabled) {
            f = f or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            params.blurBehindRadius = dp(24)
        }
        return f
    }

    /** Latar panel sesuai desain, sudut, dan transparansi dari Pengaturan. */
    private fun applyPanelStyle() {
        panel.background = PanelDesign.background(ui, Prefs.panelStyle(this), Prefs.panelRadius(this), Prefs.panelOpacity(this))
    }

    /** Ubah ukuran panel dengan menggeser pojok bawah. Hasilnya disimpan di Pengaturan. */
    private fun attachResize(grip: View, fromRight: Boolean) {
        grip.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var startW = 0; var startH = 0; var startX = 0
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                val screenW = resources.displayMetrics.widthPixels
                val screenH = resources.displayMetrics.heightPixels
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = e.rawX; sy = e.rawY
                        startW = panelW; startH = scroll.height; startX = params.x
                        view.animate().scaleX(1.25f).scaleY(1.25f).setDuration(100).start()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - sx).toInt()
                        val dy = (e.rawY - sy).toInt()
                        val minW = dp(240)
                        val maxW = screenW - dp(8)
                        val newW = (if (fromRight) startW + dx else startW - dx).coerceIn(minW, maxW)
                        val newH = (startH + dy).coerceIn(dp(100), screenH - dp(220))
                        if (!fromRight) params.x = (startX + (startW - newW)).coerceIn(0, screenW - minW)
                        panelW = newW
                        panel.layoutParams = panel.layoutParams.apply { width = newW }
                        scroll.exactHeight = true
                        scroll.maxHeightPx = newH
                        panel.requestLayout()
                        updateWindow()
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                        Prefs.setPanelWidthPct(this@OverlayService, panelW * 100 / (screenW - dp(16)))
                        Prefs.setPanelHeightPct(this@OverlayService, scroll.maxHeightPx * 100 / screenH)
                        setStatus("Ukuran panel disimpan")
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun cycleOpacity() {
        val steps = listOf(35, 60, 85)
        val cur = Prefs.panelOpacity(this)
        val next = steps.firstOrNull { it > cur + 2 } ?: steps.first()
        Prefs.setPanelOpacity(this, next)
        applyPanelStyle()
        setStatus("Transparansi panel $next%")
    }

    // ───────────────────────── Ikon: tahan untuk membuka ─────────────────────────

    private fun pressDown() {
        bubbleIcon.animate().scaleX(0.9f).scaleY(0.9f)
            .setDuration(120).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun pressUp() {
        bubbleIcon.animate().scaleX(1f).scaleY(1f)
            .setDuration(260).setInterpolator(OvershootInterpolator(3f)).start()
    }

    private fun startHold() {
        holdRing.progress = 0f
        holdAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Prefs.holdMs(this@OverlayService).toLong()
            interpolator = LinearInterpolator()
            addUpdateListener { holdRing.progress = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled) onHoldComplete() }
            })
            start()
        }
    }

    private fun cancelHold() {
        holdAnim?.cancel()
        holdAnim = null
        val cur = holdRing.progress
        if (cur > 0f) {
            ValueAnimator.ofFloat(cur, 0f).apply {
                duration = 150
                addUpdateListener { holdRing.progress = it.animatedValue as Float }
                start()
            }
        }
    }

    private fun onHoldComplete() {
        holdDone = true
        holdAnim = null
        holdRing.progress = 0f
        bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        pressUp()
        expand()
    }

    private fun onShortTap() {
        ObjectAnimator.ofFloat(
            bubbleIcon, "translationX",
            0f, -dp(4).toFloat(), dp(4).toFloat(), -dp(3).toFloat(), dp(3).toFloat(), 0f
        ).setDuration(280).start()
        if (tapCount++ < 2) toast("Tahan ikon B untuk membuka")
    }

    private fun attachBubble(target: View) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        target.setOnTouchListener(object : View.OnTouchListener {
            var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var moved = false
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY
                        moved = false
                        holdDone = false
                        pressDown()
                        startHold()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                            moved = true
                            cancelHold()
                        }
                        if (moved) {
                            val bs = dp((54 * scale).toInt())
                            params.x = (ix + dx).coerceIn(0, max(0, resources.displayMetrics.widthPixels - bs))
                            params.y = (iy + dy).coerceIn(0, max(0, resources.displayMetrics.heightPixels - bs))
                            updateWindow()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (holdDone) return true
                        cancelHold()
                        pressUp()
                        if (moved) snapToEdge() else onShortTap()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        cancelHold()
                        pressUp()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun updateWindow() {
        try { root?.let { wm.updateViewLayout(it, params) } } catch (_: Exception) {}
    }

    private fun hideKeyboard() {
        val r = root ?: return
        r.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(r.windowToken, 0)
    }

    /** Geser view; kalau hanya diketuk (tidak bergeser) panggil [onTap]. Ikon menempel ke tepi layar. */
    private fun attachDrag(target: View, snap: Boolean, onTap: (() -> Unit)?) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        target.setOnTouchListener(object : View.OnTouchListener {
            var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var moved = false
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                        if (moved) {
                            params.x = (ix + dx).coerceIn(0, max(0, resources.displayMetrics.widthPixels - panelW))
                            params.y = max(0, iy + dy).coerceAtMost(max(0, resources.displayMetrics.heightPixels - dp(120)))
                            updateWindow()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) onTap?.invoke() else if (snap && !expanded) snapToEdge()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun snapToEdge() {
        val screenW = resources.displayMetrics.widthPixels
        val size = dp((54 * scale).toInt())
        val target = if (params.x + size / 2 < screenW / 2) dp(4) else screenW - size - dp(4)
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 160
            addUpdateListener {
                params.x = it.animatedValue as Int
                updateWindow()
            }
            start()
        }
    }

    // ───────────────────────── Isi panel ─────────────────────────

    private fun render() {
        try {
            renderInner()
        } catch (e: Exception) {
            CrashLog.record(this, "render overlay", e)
            content.removeAllViews()
            content.addView(label("Gagal menampilkan isi: ${e.message}", 11f, R.color.red, false))
        }
    }

    private fun renderInner() {
        hScroll?.let { savedScrollX = it.scrollX }
        hScroll = null
        content.removeAllViews()
        tvGame.text = BracketStore.gameId?.let { BracketStore.games[it]?.first } ?: "Belum ada game"
        val mode = BracketStore.mode
        if (mode == null) {
            content.addView(label("Belum ada mode aktif.\nBuka aplikasi lalu pilih game & mode.", 12f, R.color.muted, false).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(28), dp(8), dp(28))
            })
        } else {
            val info = if (mode.type == "br") mode.name else "${mode.name} · BO${BracketStore.boFormat}"
            content.addView(label(info.uppercase(), 9f, R.color.muted, true).apply {
                letterSpacing = 0.1f
                setPadding(dp(2), 0, 0, dp(6))
            })
            if (mode.type == "br") renderTeams() else renderBracket()
        }
        updateChamp()
        Anim.pressAll(content)
    }

    private fun renderTeams() {
        val sorted = BracketStore.teams.mapIndexed { i, t -> i to t }.sortedByDescending { it.second.score }
        sorted.forEachIndexed { rank, (_, team) ->
            val row = LinearLayout(ui).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_ov_card)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = rowParams(dp(8))
            }
            row.addView(label("${rank + 1}", 12f, rankColor(rank), true).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(22), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(ImageView(ui).apply {
                val px = dp(28)
                setImageDrawable(LogoHelper.drawable(this@OverlayService, team.name, team.logo, px))
                layoutParams = LinearLayout.LayoutParams(px, px).apply { marginStart = dp(4); marginEnd = dp(4) }
            })
            row.addView(nameField(team.name, R.color.text) { team.name = it; updateChamp() })
            row.addView(squareButton("−", false) {
                team.score = max(0, team.score - 1)
                render()
            })
            row.addView(label("${team.score}", 15f, R.color.text, true).apply {
                setTextColor(ThemeManager.accent1(ui))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(squareButton("+", true) {
                team.score = team.score + 1
                render()
            })
            content.addView(row)
        }
        content.addView(actionRow(
            "+ Tim" to {
                BracketStore.teams.add(BracketStore.Team("Tim ${BracketStore.teams.size + 1}"))
                BracketStore.size = BracketStore.teams.size
                render()
            },
            "Reset Skor" to {
                BracketStore.teams.forEach { it.score = 0 }
                setStatus("Skor direset")
                render()
            }
        ))
    }

    private fun renderBracket() {
        val hs = HorizontalScrollView(ui).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val holder = LinearLayout(ui).apply { orientation = LinearLayout.HORIZONTAL }
        hs.addView(holder)
        content.addView(hs)
        hScroll = hs
        val cfg = BracketUi.Config(
            cardWidthDp = (156 * zoom).toInt(), rowHeightDp = (34 * zoom).toInt(), textSp = 12f * zoom,
            logoDp = (22 * zoom).toInt(), connectorDp = 20,
            cardBg = R.drawable.bg_ov_card, chipBg = R.drawable.bg_ov_chip
        )
        BracketUi.build(ui, holder, cfg, pickLogo = null, onWin = { msg ->
            msg?.let { setStatus(it) }
            render()
        })
        val x = savedScrollX
        hs.post { hs.scrollTo(x, 0) }

        content.addView(actionRow(
            "Reset Hasil" to {
                BracketStore.resetMatches()
                setStatus("Hasil direset")
                render()
            }
        ))
    }

    private fun updateChamp() {
        tvChamp.text = BracketStore.champion() ?: "—"
    }

    private fun setStatus(msg: String) { tvStatus.text = msg }

    // ───────────────────────── Helper view ─────────────────────────

    private fun col(id: Int) = ContextCompat.getColor(this, id)

    private fun rankColor(rank: Int) = when (rank) {
        0 -> R.color.gold
        1 -> R.color.silver
        2 -> R.color.bronze
        else -> R.color.muted
    }

    private fun rowParams(bottom: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = bottom }

    private fun label(text: String, sizeSp: Float, colorRes: Int, bold: Boolean) = TextView(ui).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(col(colorRes))
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun nameField(value: String, colorRes: Int, onChange: (String) -> Unit) = EditText(ui).apply {
        setText(value)
        setTextColor(col(colorRes))
        setHintTextColor(col(R.color.dim))
        hint = "Nama tim"
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dp(4), 0, dp(4), 0)
        minimumHeight = 0
        minHeight = 0
        setSingleLine()
        imeOptions = EditorInfo.IME_ACTION_DONE
        layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f)
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { onChange(s?.toString() ?: "") }
        })
    }

    private fun squareButton(text: String, primary: Boolean, onClick: () -> Unit) = label(text, 16f, R.color.text, true).apply {
        gravity = Gravity.CENTER
        setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_ov_chip)
        layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        setOnClickListener { onClick() }
    }

    private fun actionRow(vararg actions: Pair<String, () -> Unit>): View {
        val row = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = rowParams(0).apply { topMargin = dp(4) }
        }
        actions.forEachIndexed { i, (text, action) ->
            row.addView(label(text, 11f, R.color.muted_light, true).apply {
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_ov_chip)
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    if (i > 0) marginStart = dp(8)
                }
                setOnClickListener { action() }
            })
        }
        return row
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(m: String) =
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, m, Toast.LENGTH_LONG).show()
        }

    override fun onDestroy() {
        AppVisibility.listener = null
        OverlayService.onSettingsChanged = null
        LiveFeed.setListener("overlay", null)
        setRunning(false)
        try { root?.let { wm.removeView(it) } } catch (_: Exception) {}
        root = null
        super.onDestroy()
    }
}
