package com.gamebracket.overlay

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Dialog pemilih: grid pilihan (tema, desain panel, tombol, ikon) dan pemilih warna aksen bebas. */
object Pickers {

    private fun dp(a: Activity, v: Int) = (v * a.resources.displayMetrics.density).toInt()

    /** Dialog berisi grid [count] pilihan. [tile] membuat isi tiap pilihan (indeks, terpilih?). */
    fun grid(
        act: Activity, title: String, subtitle: String, count: Int, selected: Int, columns: Int,
        tile: (Int, Boolean) -> View, onPick: (Int) -> Unit
    ) {
        val d = PremiumDialog(act).title(title, subtitle)
        val scroll = MaxHeightScrollView(act).apply {
            maxHeightPx = (act.resources.displayMetrics.heightPixels * 0.6f).toInt()
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val grid = GridLayout(act).apply { columnCount = columns }
        for (i in 0 until count) {
            val cell = FrameLayout(act).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(act, 3), dp(act, 3), dp(act, 3), dp(act, 3))
                }
                addView(tile(i, i == selected))
                setOnClickListener { d.dismiss(); onPick(i) }
            }
            Anim.pressable(cell)
            grid.addView(cell)
        }
        scroll.addView(grid)
        d.body.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        d.secondary("Tutup")
        d.show()
        if (selected > columns * 2) scroll.post { scroll.scrollTo(0, (selected / columns) * dp(act, 84) - dp(act, 100)) }
    }

    private fun frame(act: Activity, selected: Boolean, content: View, caption: String, captionSp: Float = 9f): View {
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(act, 5), dp(act, 5), dp(act, 5), dp(act, 5))
            background = if (selected) Skin.rect(act, Color.TRANSPARENT, 10f, Skin.accent(act), 2f)
            else Skin.rect(act, ThemeManager.color(act, R.attr.blSurface2), 10f, ThemeManager.color(act, R.attr.blStroke))
        }
        box.addView(content)
        box.addView(TextView(act).apply {
            text = caption
            textSize = captionSp
            maxLines = 2
            gravity = Gravity.CENTER
            setPadding(0, dp(act, 4), 0, 0)
            setTextColor(ContextCompat.getColor(act, if (selected) R.color.text else R.color.muted_light))
            if (selected) setTypeface(typeface, Typeface.BOLD)
        })
        return box
    }

    // ───────────── Tema UI ─────────────

    fun themeTile(act: Activity, i: Int, selected: Boolean): View {
        val p = ThemeManager.basePreview[i]
        val thumb = FrameLayout(act).apply {
            layoutParams = LinearLayout.LayoutParams(dp(act, 88), dp(act, 56))
            background = Skin.rect(act, p[1], 8f, 0x33FFFFFF)
        }
        thumb.addView(View(act).apply { setBackgroundColor(p[0]) }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 12), Gravity.TOP))
        thumb.addView(View(act).apply { background = Skin.rect(act, p[2], 4f) },
            FrameLayout.LayoutParams(dp(act, 70), dp(act, 22), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(act, 6) })
        thumb.addView(View(act).apply { background = Skin.rect(act, Skin.accent(act), 2f) },
            FrameLayout.LayoutParams(dp(act, 26), dp(act, 4), Gravity.BOTTOM or Gravity.START).apply { bottomMargin = dp(act, 12); marginStart = dp(act, 12) })
        return frame(act, selected, thumb, ThemeManager.baseNames[i])
    }

    // ───────────── Desain UI ─────────────

    fun uiDesignTile(act: Activity, i: Int, selected: Boolean): View {
        val c = ThemeManager.designContext(act, i)
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(act, 7), dp(act, 7), dp(act, 7), dp(act, 7))
            background = Skin.card(c)
            layoutParams = LinearLayout.LayoutParams(dp(act, 88), dp(act, 58))
        }
        card.addView(View(act).apply {
            background = Skin.chip(c)
            layoutParams = LinearLayout.LayoutParams(dp(act, 52), dp(act, 14))
        })
        card.addView(View(act).apply {
            background = Skin.primary(c)
            layoutParams = LinearLayout.LayoutParams(dp(act, 34), dp(act, 14)).apply { topMargin = dp(act, 6) }
        })
        return frame(act, selected, card, ThemeManager.uiDesignNames[i], 8f)
    }

    // ───────────── Desain panel ─────────────

    fun panelTile(act: Activity, i: Int, selected: Boolean): View {
        val ui = ThemeManager.themedContext(act)
        val thumb = FrameLayout(act).apply {
            layoutParams = LinearLayout.LayoutParams(dp(act, 92), dp(act, 62))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(ThemeManager.accent2(ui), ThemeManager.accent3(ui), ThemeManager.accent1(ui))
            ).apply { cornerRadius = dp(act, 8).toFloat() }
        }
        thumb.addView(TextView(act).apply {
            text = "BRACKET\nTim 1    0"
            textSize = 8f
            setTextColor(Color.WHITE)
            setPadding(dp(act, 8), dp(act, 7), dp(act, 8), dp(act, 6))
            background = PanelDesign.background(ui, i, 10, maxOf(Prefs.panelOpacity(act), 60))
        }, FrameLayout.LayoutParams(dp(act, 76), dp(act, 46), Gravity.CENTER))
        return frame(act, selected, thumb, PanelDesign.names[i])
    }

    // ───────────── Tombol & ikon ─────────────

    fun bubbleTile(act: Activity, style: Int, selected: Boolean): View {
        val ui = ThemeManager.themedContext(act)
        val size = dp(act, 44)
        val img = ImageView(act).apply { setImageDrawable(BubbleFactory.background(ui, style, size)) }
        val cell = FrameLayout(act).apply { layoutParams = LinearLayout.LayoutParams(size, size) }
        cell.addView(img, FrameLayout.LayoutParams(size, size))
        val glyph = ImageView(act)
        BubbleFactory.applyIcon(ui, glyph, Prefs.bubbleIcon(act).let { if (it == BubbleFactory.CUSTOM_ICON) 0 else it }, null, (size * 0.6f).toInt())
        cell.addView(glyph, FrameLayout.LayoutParams((size * 0.6f).toInt(), (size * 0.6f).toInt(), Gravity.CENTER))
        return frame(act, selected, cell, BubbleFactory.styleNames[style], 8f)
    }

    fun iconTile(act: Activity, idx: Int, selected: Boolean): View {
        val ui = ThemeManager.themedContext(act)
        val size = dp(act, 44)
        val cell = FrameLayout(act).apply { layoutParams = LinearLayout.LayoutParams(size, size) }
        cell.addView(ImageView(act).apply { setImageDrawable(BubbleFactory.background(ui, Prefs.bubbleStyle(act), size)) }, FrameLayout.LayoutParams(size, size))
        val glyph = ImageView(act)
        val custom = Prefs.bubbleCustom(act)
        if (idx == BubbleFactory.CUSTOM_ICON && custom == null) glyph.setImageResource(R.drawable.ic_ov_camera)
        else BubbleFactory.applyIcon(ui, glyph, idx, custom, (size * 0.6f).toInt())
        cell.addView(glyph, FrameLayout.LayoutParams((size * 0.6f).toInt(), (size * 0.6f).toInt(), Gravity.CENTER))
        return frame(act, selected, cell, BubbleFactory.iconNames[idx], 8f)
    }

    // ───────────── Warna aksen bebas ─────────────

    fun accentDialog(act: Activity, onApplied: () -> Unit) {
        val d = PremiumDialog(act).title("Warna aksen", "16,7 juta pilihan")
        val hsv = FloatArray(3)
        var color = Prefs.accentColor(act)
        Color.colorToHSV(color, hsv)
        var updating = false

        val preview = TextView(act).apply {
            text = "Contoh tombol"
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }
        d.body.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 48)))

        // pilihan cepat
        val presets = GridLayout(act).apply { columnCount = 8 }
        val hexField = EditText(act)
        val sbH = SeekBar(act); val sbS = SeekBar(act); val sbV = SeekBar(act)

        fun track(vararg cols: Int) = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, cols).apply {
            cornerRadius = dp(act, 5).toFloat()
            setSize(-1, dp(act, 10))
        }

        fun refresh(fromHex: Boolean = false) {
            updating = true
            color = Color.HSVToColor(hsv)
            preview.background = Skin.rect(act, color, 10f)
            preview.setTextColor(ThemeManager.onAccent(color))
            sbH.progress = hsv[0].toInt().coerceIn(0, 359)
            sbS.progress = (hsv[1] * 100).toInt()
            sbV.progress = (hsv[2] * 100).toInt()
            sbS.progressDrawable = track(Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2])), Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2])))
            sbV.progressDrawable = track(Color.BLACK, Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f)))
            if (!fromHex) hexField.setText("#%06X".format(color and 0xFFFFFF))
            updating = false
        }

        fun styleSeek(sb: SeekBar, max: Int, onChange: (Int) -> Unit) {
            sb.max = max
            sb.thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            sb.setPadding(dp(act, 6), dp(act, 8), dp(act, 6), dp(act, 8))
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser && !updating) { onChange(p); refresh() } }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        sbH.progressDrawable = track(
            0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt()
        )
        styleSeek(sbH, 359) { hsv[0] = it.toFloat() }
        styleSeek(sbS, 100) { hsv[1] = it / 100f }
        styleSeek(sbV, 100) { hsv[2] = it.coerceAtLeast(15) / 100f }

        ThemeManager.accentPresets.forEach { (name, c) ->
            val sw = View(act).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(dp(act, 1), 0x44FFFFFF) }
                contentDescription = name
                layoutParams = GridLayout.LayoutParams().apply { width = dp(act, 30); height = dp(act, 30); setMargins(dp(act, 4), dp(act, 4), dp(act, 4), dp(act, 4)) }
                setOnClickListener { Color.colorToHSV(c, hsv); refresh() }
            }
            Anim.pressable(sw)
            presets.addView(sw)
        }
        d.body.addView(presets, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(act, 10) })

        fun sliderRow(name: String, sb: SeekBar) {
            d.body.addView(TextView(act, null, 0, R.style.SectionLabel).apply { text = name; setPadding(0, dp(act, 10), 0, 0) })
            d.body.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        sliderRow("Corak (hue)", sbH)
        sliderRow("Kepekatan", sbS)
        sliderRow("Kecerahan", sbV)

        d.body.addView(TextView(act, null, 0, R.style.SectionLabel).apply { text = "Kode warna"; setPadding(0, dp(act, 10), 0, dp(act, 6)) })
        hexField.apply {
            setSingleLine()
            setTextColor(ContextCompat.getColor(act, R.color.text))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(dp(act, 14), 0, dp(act, 14), 0)
            setBackgroundResource(R.drawable.bg_input)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (updating) return
                    val t = s?.toString()?.trim()?.removePrefix("#") ?: return
                    if (t.length == 6 && t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                        Color.colorToHSV(0xFF000000.toInt() or t.toInt(16), hsv)
                        refresh(fromHex = true)
                    }
                }
            })
        }
        d.body.addView(hexField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 44)))

        refresh()
        d.primary("Terapkan") { Prefs.setAccentColor(act, color); onApplied(); true }
        d.secondary("Batal")
        d.show()
    }

    // ───────────── Font (700 / 300 / 300) ─────────────

    /** Pemilih font dengan pencarian dan pratinjau. Font diunduh sekali, setelah itu offline. */
    fun font(act: Activity, which: String, current: String, title: String, onChosen: (String) -> Unit) {
        val names = listOf("") + Fonts.list(act, which)
        val rows = names.map { n ->
            PickRow(if (n.isEmpty()) "Bawaan sistem" else n, face = { if (n.isEmpty()) null else Fonts.cached(act, n) })
        }
        val sample = "Bracket Live · Juara 123"
        ListPicker.show(
            act, title, "${names.size - 1} font · diunduh sekali, lalu bisa offline", rows,
            names.indexOf(current).coerceAtLeast(0), confirm = true, preview = sample,
            onPreview = { idx, tv ->
                val n = names[idx]
                tv.tag = idx
                tv.text = sample
                if (n.isEmpty()) {
                    tv.typeface = Typeface.DEFAULT
                } else {
                    val f = Fonts.cached(act, n)
                    if (f != null) tv.typeface = f else {
                        tv.text = "Mengunduh font…"
                        Fonts.ensure(act, n) { ok ->
                            if (tv.tag == idx) {
                                tv.text = if (ok) sample else "Gagal mengunduh (cek internet)"
                                if (ok) tv.typeface = Fonts.cached(act, n)
                            }
                        }
                    }
                }
            }
        ) { idx ->
            val n = names[idx]
            if (n.isEmpty()) onChosen("")
            else Fonts.ensure(act, n) { ok ->
                if (ok) onChosen(n)
                else android.widget.Toast.makeText(act, "Font belum bisa diunduh, cek koneksi internet", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
