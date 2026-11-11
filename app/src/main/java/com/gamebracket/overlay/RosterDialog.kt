package com.gamebracket.overlay

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog

/** Dialog untuk mengisi roster anggota tim (mis. 8 anggota). */
object RosterDialog {

    fun show(ctx: Context, title: String, size: Int, current: List<String>, onSave: (List<String>) -> Unit) {
        val d = ctx.resources.displayMetrics.density
        val fields = ArrayList<EditText>()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), (8 * d).toInt())
        }
        for (i in 0 until size) {
            val et = EditText(ctx).apply {
                hint = "Anggota ${i + 1}"
                setText(current.getOrNull(i) ?: "")
                setSingleLine()
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
            fields.add(et)
            box.addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val scroll = ScrollView(ctx).apply { addView(box) }
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Simpan") { _, _ -> onSave(fields.map { it.text.toString().trim() }) }
            .setNegativeButton("Batal", null)
            .show()
    }
}
