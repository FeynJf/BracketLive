package com.gamebracket.overlay

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Satu baris pilihan: judul, keterangan, titik warna (opsional), dan font untuk pratinjau (opsional). */
class PickRow(val title: String, val sub: String? = null, val dots: IntArray? = null, val face: () -> Typeface? = { null })

/** Dialog daftar dengan pencarian (dipakai untuk font, desain sertifikat, tema warna, dll). */
object ListPicker {

    /**
     * @param confirm true = ketuk baris hanya memilih (pratinjau), lalu tombol Terapkan menetapkan.
     * @param preview teks contoh yang tampil di atas daftar (untuk pratinjau font); [onPreview] dipanggil tiap baris dipilih.
     */
    fun show(
        act: Activity, title: String, subtitle: String, rows: List<PickRow>, selected: Int,
        confirm: Boolean = false, preview: String? = null,
        onPreview: ((Int, TextView) -> Unit)? = null, onPick: (Int) -> Unit
    ) {
        val d0 = act.resources.displayMetrics.density
        fun dp(v: Int) = (v * d0).toInt()
        val dlg = PremiumDialog(act).title(title, subtitle)
        var current = selected
        var shown: List<Int> = rows.indices.toList()

        var previewView: TextView? = null
        if (preview != null) {
            previewView = TextView(act).apply {
                text = preview
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(act, R.color.text))
                setPadding(dp(12), dp(14), dp(12), dp(14))
                background = Skin.chip(act)
            }
            Fonts.markKeep(previewView)
            dlg.body.addView(previewView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
            if (current in rows.indices) onPreview?.invoke(current, previewView)
        }

        val search = EditText(act).apply {
            hint = "Cari (${rows.size})"
            setHintTextColor(ContextCompat.getColor(act, R.color.dim))
            setTextColor(ContextCompat.getColor(act, R.color.text))
            textSize = 14f
            setSingleLine()
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundResource(R.drawable.bg_input)
        }
        dlg.body.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        val list = ListView(act).apply {
            divider = null
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int) = rows[shown[p]]
            override fun getItemId(p: Int) = shown[p].toLong()
            override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
                val idx = shown[p]
                val row = rows[idx]
                val sel = idx == current
                val box = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = if (sel) Skin.rect(act, ThemeManager.withAlpha(Skin.accent(act), 38), 8f, Skin.accent(act), 1.5f) else null
                }
                row.dots?.forEach { c ->
                    box.addView(View(act).apply {
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(dp(1), 0x44FFFFFF) }
                        layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(5) }
                    })
                }
                val col = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (row.dots != null) marginStart = dp(6) }
                }
                col.addView(TextView(act).apply {
                    text = row.title
                    textSize = 15f
                    setTextColor(ContextCompat.getColor(act, if (sel) R.color.text else R.color.muted_light))
                    if (sel) setTypeface(typeface, Typeface.BOLD)
                    row.face()?.let { f -> setTypeface(f, if (sel) Typeface.BOLD else Typeface.NORMAL); Fonts.markKeep(this) }
                })
                if (row.sub != null) col.addView(TextView(act).apply {
                    text = row.sub
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(act, R.color.muted))
                })
                box.addView(col)
                if (sel) box.addView(View(act).apply {
                    background = Skin.rect(act, Skin.accent(act), 4f)
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
                })
                return box
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, p, _ ->
            val idx = shown[p]
            if (confirm) {
                current = idx
                adapter.notifyDataSetChanged()
                previewView?.let { onPreview?.invoke(idx, it) }
            } else {
                dlg.dismiss()
                onPick(idx)
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                shown = if (q.isEmpty()) rows.indices.toList() else rows.indices.filter { rows[it].title.lowercase().contains(q) }
                adapter.notifyDataSetChanged()
            }
        })
        val maxH = minOf(dp(320), (act.resources.displayMetrics.heightPixels * 0.42f).toInt())
        dlg.body.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH).apply { topMargin = dp(8) })

        if (confirm) {
            dlg.primary("Terapkan") { onPick(current); true }
            dlg.secondary("Batal")
        } else {
            dlg.secondary("Tutup")
        }
        dlg.show()
        if (current > 3) list.post { list.setSelection(maxOf(0, current - 2)) }
    }
}
