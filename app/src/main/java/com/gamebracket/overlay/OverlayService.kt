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
import android.animation.ValueAnimator
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
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
                toast("Gagal: ${e.message}")
                stopSelf()
            }
        }
        return START_STICKY
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
        val v = LayoutInflater.from(this).inflate(R.layout.overlay_bracket, null)
        root = v
        bubble = v.findViewById(R.id.bubble)
        panel = v.findViewById(R.id.panel)
        content = v.findViewById(R.id.overlayContent)
        tvGame = v.findViewById(R.id.tvOverlayGame)
        tvChamp = v.findViewById(R.id.tvOverlayChamp)
        tvStatus = v.findViewById(R.id.tvOverlayStatus)

        panelW = min(resources.displayMetrics.widthPixels - dp(16), dp(460))
        panel.layoutParams = panel.layoutParams.apply { width = panelW }
        val maxH = min(dp(420), (resources.displayMetrics.heightPixels * 0.5f).toInt())
        v.findViewById<MaxHeightScrollView>(R.id.overlayScroll).maxHeightPx = maxH

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

        attachDrag(bubble, true) { expand() }
        attachDrag(v.findViewById(R.id.dragHandle), false, null)

        v.findViewById<View>(R.id.btnClose).setOnClickListener { stopSelf() }
        v.findViewById<View>(R.id.btnMin).setOnClickListener { collapse() }
        v.findViewById<View>(R.id.btnOpenApp).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            collapse()
        }

        wm.addView(v, params)

        // Ikon B hanya muncul saat pengguna berada di luar aplikasi
        AppVisibility.listener = { fg -> applyForeground(fg) }
        applyForeground(AppVisibility.inForeground)
    }

    private fun applyForeground(fg: Boolean) {
        val r = root ?: return
        if (fg) {
            if (expanded) collapse()
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
        render()
        bubble.visibility = View.GONE
        panel.visibility = View.VISIBLE
        params.flags = flagsPanel
        params.x = max(0, (resources.displayMetrics.widthPixels - panelW) / 2)
        params.y = dp(48)
        updateWindow()
    }

    private fun collapse() {
        if (!expanded) return
        expanded = false
        hideKeyboard()
        panel.visibility = View.GONE
        bubble.visibility = View.VISIBLE
        params.flags = flagsBubble
        params.x = bubbleX
        params.y = bubbleY
        updateWindow()
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
                            params.x = ix + dx
                            params.y = max(0, iy + dy)
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
        val size = dp(44)
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
    }

    private fun renderTeams() {
        val sorted = BracketStore.teams.mapIndexed { i, t -> i to t }.sortedByDescending { it.second.score }
        sorted.forEachIndexed { rank, (_, team) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_match)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = rowParams(dp(8))
            }
            row.addView(label("${rank + 1}", 12f, rankColor(rank), true).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(22), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(ImageView(this).apply {
                val px = dp(28)
                setImageDrawable(LogoHelper.drawable(this@OverlayService, team.name, team.logo, px))
                layoutParams = LinearLayout.LayoutParams(px, px).apply { marginStart = dp(4); marginEnd = dp(4) }
            })
            row.addView(nameField(team.name, R.color.text) { team.name = it; updateChamp() })
            row.addView(squareButton("−", false) {
                team.score = max(0, team.score - 1)
                render()
            })
            row.addView(label("${team.score}", 15f, R.color.cyan, true).apply {
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
        val hs = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val holder = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hs.addView(holder)
        content.addView(hs)
        hScroll = hs
        val cfg = BracketUi.Config(cardWidthDp = 156, rowHeightDp = 34, textSp = 12f, logoDp = 22, connectorDp = 20)
        BracketUi.build(this, holder, cfg, null) { msg ->
            msg?.let { setStatus(it) }
            render()
        }
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

    private fun label(text: String, sizeSp: Float, colorRes: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(col(colorRes))
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun nameField(value: String, colorRes: Int, onChange: (String) -> Unit) = EditText(this).apply {
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
        setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_chip)
        layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        setOnClickListener { onClick() }
    }

    private fun actionRow(vararg actions: Pair<String, () -> Unit>): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = rowParams(0).apply { topMargin = dp(4) }
        }
        actions.forEachIndexed { i, (text, action) ->
            row.addView(label(text, 11f, R.color.muted_light, true).apply {
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_btn_gray)
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
        setRunning(false)
        try { root?.let { wm.removeView(it) } } catch (_: Exception) {}
        root = null
        super.onDestroy()
    }
}
