package com.gamebracket.overlay

import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Dialog bergaya premium/gamer (kartu neon, judul, isi bebas, dua tombol). */
class PremiumDialog(private val act: Activity) {

    val dialog = Dialog(act, R.style.BL_PremiumDialog)
    private val view: View
    val body: LinearLayout
    private val card: View
    private val title: TextView
    private val subtitle: TextView
    private val btnPrimary: TextView
    private val btnSecondary: TextView

    init {
        ThemeManager.applyTo(dialog.context)
        view = LayoutInflater.from(dialog.context).inflate(R.layout.dialog_premium, null)
        card = view.findViewById(R.id.dlgCard)
        body = view.findViewById(R.id.dlgBody)
        title = view.findViewById(R.id.dlgTitle)
        subtitle = view.findViewById(R.id.dlgSubtitle)
        btnPrimary = view.findViewById(R.id.btnPrimary)
        btnSecondary = view.findViewById(R.id.btnSecondary)
        btnPrimary.visibility = View.GONE
        btnSecondary.visibility = View.GONE
    }

    fun title(t: String, sub: String): PremiumDialog { title.text = t; subtitle.text = sub; return this }

    fun cancelable(b: Boolean): PremiumDialog { dialog.setCancelable(b); return this }

    /** [onClick] mengembalikan true kalau dialog harus ditutup. */
    fun primary(text: String, onClick: () -> Boolean = { true }): PremiumDialog {
        btnPrimary.text = text
        btnPrimary.visibility = View.VISIBLE
        btnPrimary.setOnClickListener { if (onClick()) dismiss() }
        Anim.pressable(btnPrimary)
        return this
    }

    fun secondary(text: String, onClick: () -> Boolean = { true }): PremiumDialog {
        btnSecondary.text = text
        btnSecondary.visibility = View.VISIBLE
        btnSecondary.setOnClickListener { if (onClick()) dismiss() }
        Anim.pressable(btnSecondary)
        return this
    }

    fun hideButtons() { btnPrimary.visibility = View.GONE; btnSecondary.visibility = View.GONE }

    fun message(text: String): TextView {
        val tv = TextView(dialog.context).apply {
            this.text = text
            textSize = 13f
            setLineSpacing(0f, 1.2f)
            setTextColor(ContextCompat.getColor(context, R.color.muted_light))
        }
        body.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return tv
    }

    fun show(): PremiumDialog {
        if (act.isFinishing || act.isDestroyed) return this
        dialog.setContentView(view)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
        dialog.show()
        Anim.dialogIn(card)
        return this
    }

    fun dismiss() {
        try { if (dialog.isShowing) dialog.dismiss() } catch (_: Exception) { }
    }

    companion object {
        /** Dialog pesan singkat dengan dua tombol. */
        fun message(
            act: Activity, title: String, subtitle: String, text: String,
            positive: String, negative: String? = null,
            onPositive: () -> Unit = {}, onNegative: () -> Unit = {}
        ): PremiumDialog {
            val d = PremiumDialog(act).title(title, subtitle)
            d.message(text)
            d.primary(positive) { onPositive(); true }
            if (negative != null) d.secondary(negative) { onNegative(); true }
            return d.show()
        }
    }
}
