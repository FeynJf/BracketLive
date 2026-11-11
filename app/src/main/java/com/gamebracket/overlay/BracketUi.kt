package com.gamebracket.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Membangun tampilan bracket (kolom babak + garis penghubung). Dipakai oleh aplikasi dan overlay.
 */
object BracketUi {

    class Config(
        val cardWidthDp: Int,
        val rowHeightDp: Int,
        val textSp: Float,
        val logoDp: Int,
        val connectorDp: Int
    )

    /**
     * @param pickLogo dipanggil dengan indeks slot babak-1 saat logo diketuk (null = logo tidak bisa diubah)
     * @param onWin dipanggil setelah skor/pemenang berubah; pemanggil harus menggambar ulang
     */
    fun build(
        ctx: Context,
        container: LinearLayout,
        cfg: Config,
        pickLogo: ((Int) -> Unit)?,
        onWin: (String?) -> Unit
    ) {
        container.removeAllViews()
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun col(id: Int) = ContextCompat.getColor(ctx, id)

        val cardW = dp(cfg.cardWidthDp)
        val rowH = dp(cfg.rowHeightDp)
        val labelH = dp(28)
        val baseSlot = rowH * 2 + dp(18)
        val rounds = BracketStore.rounds()

        for (r in 0 until rounds) {
            val slotH = baseSlot * (1 shl r)
            val matches = BracketStore.matchesIn(r)

            val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            column.addView(TextView(ctx).apply {
                text = BracketStore.roundName(r)
                setTextColor(col(R.color.cyan))
                textSize = 9.5f
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_chip)
                layoutParams = LinearLayout.LayoutParams(cardW, dp(22)).apply { bottomMargin = labelH - dp(22) }
            })

            for (m in 0 until matches) {
                val slot = FrameLayout(ctx).apply { layoutParams = LinearLayout.LayoutParams(cardW, slotH) }
                val card = buildMatch(ctx, r, m, cfg, rowH, pickLogo, onWin)
                card.layoutParams = FrameLayout.LayoutParams(cardW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL)
                slot.addView(card)
                column.addView(slot)
            }
            container.addView(column)

            if (r < rounds - 1) {
                val decided = BooleanArray(matches) { BracketStore.winners["r${r}m$it"] != null }
                val conn = BracketConnectorView(ctx)
                conn.layoutParams = LinearLayout.LayoutParams(dp(cfg.connectorDp), labelH + matches * slotH)
                conn.setData(matches / 2, slotH, labelH, decided)
                container.addView(conn)
            }
        }
    }

    private fun buildMatch(
        ctx: Context, r: Int, m: Int, cfg: Config, rowH: Int,
        pickLogo: ((Int) -> Unit)?, onWin: (String?) -> Unit
    ): View {
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun col(id: Int) = ContextCompat.getColor(ctx, id)

        val key = "r${r}m$m"
        val sc = BracketStore.series[key] ?: intArrayOf(0, 0)
        val winner = BracketStore.winners[key]
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_match)
        }

        for (s in 0..1) {
            val slotIndex = m * 2 + s
            val name = BracketStore.getName(r, m, s)
            val isWin = winner == s
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH)
                if (isWin) setBackgroundColor(Color.parseColor("#1500E5FF"))
            }
            // aksen kiri untuk pemenang
            row.addView(View(ctx).apply {
                setBackgroundColor(if (isWin) col(R.color.cyan) else Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(3), LinearLayout.LayoutParams.MATCH_PARENT)
            })
            // logo tim
            val logo = ImageView(ctx).apply {
                val px = dp(cfg.logoDp)
                setImageDrawable(LogoHelper.drawable(ctx, name, BracketStore.getLogo(r, m, s), px))
                layoutParams = LinearLayout.LayoutParams(px, px).apply { marginStart = dp(8); marginEnd = dp(6) }
                alpha = if (winner != null && !isWin) 0.45f else 1f
                if (r == 0 && pickLogo != null) setOnClickListener { pickLogo(slotIndex) }
            }
            row.addView(logo)

            val nameColor = when {
                isWin -> R.color.cyan
                winner != null -> R.color.dim
                else -> R.color.text
            }
            if (r == 0) {
                row.addView(EditText(ctx).apply {
                    setText(name)
                    setTextColor(col(nameColor))
                    setHintTextColor(col(R.color.dim))
                    hint = "Nama tim"
                    textSize = cfg.textSp
                    setTypeface(null, Typeface.BOLD)
                    setBackgroundColor(Color.TRANSPARENT)
                    setPadding(0, 0, 0, 0)
                    minimumHeight = 0
                    minHeight = 0
                    setSingleLine()
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(x: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(x: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(e: android.text.Editable?) {
                            if (slotIndex < BracketStore.players.size) BracketStore.players[slotIndex] = e?.toString() ?: ""
                        }
                    })
                })
            } else {
                row.addView(TextView(ctx).apply {
                    text = name.ifBlank { "—" }
                    setTextColor(col(nameColor))
                    textSize = cfg.textSp
                    setTypeface(null, Typeface.BOLD)
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }

            // tombol skor: ketuk = +1 poin / menangkan match
            row.addView(TextView(ctx).apply {
                text = "${sc[s]}"
                gravity = Gravity.CENTER
                setTextColor(if (isWin) Color.WHITE else col(R.color.muted_light))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setBackgroundResource(if (isWin) R.drawable.bg_btn_primary else R.drawable.bg_chip)
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(26)).apply { marginStart = dp(4); marginEnd = dp(6) }
                setOnClickListener { onWin(BracketStore.registerWin(r, m, s)) }
            })
            if (r > 0) row.setOnClickListener { onWin(BracketStore.registerWin(r, m, s)) }
            box.addView(row)

            if (s == 0) box.addView(View(ctx).apply {
                setBackgroundColor(Color.parseColor("#1F2850"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            })
        }
        return box
    }
}
