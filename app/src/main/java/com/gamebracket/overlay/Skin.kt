package com.gamebracket.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

/**
 * Permukaan yang memakai warna aksen. Dibuat lewat kode supaya warna aksen bebas (jutaan warna)
 * langsung berlaku. View di XML cukup diberi tag "skin:..." lalu [apply] dipanggil sekali.
 *
 * Tag: skin:primary (tombol utama) · skin:chip_active · skin:accent_text · skin:accent_bg · skin:tint
 */
object Skin {

    private fun d(ctx: Context) = ctx.resources.displayMetrics.density

    fun accent(ctx: Context) = ThemeManager.accent1(ctx)
    fun onAccent(ctx: Context) = ThemeManager.onAccent(accent(ctx))

    fun rect(ctx: Context, fill: Int, radiusDp: Float, stroke: Int? = null, strokeDp: Float = 1f): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * d(ctx)
            setColor(fill)
            if (stroke != null) setStroke((strokeDp * d(ctx)).toInt().coerceAtLeast(1), stroke)
        }

    fun primary(ctx: Context): Drawable = rect(ctx, accent(ctx), 10f)
    fun chipActive(ctx: Context): Drawable = rect(ctx, accent(ctx), 10f)

    fun chip(ctx: Context): Drawable =
        rect(ctx, ThemeManager.color(ctx, R.attr.blSurface2), 10f, ThemeManager.color(ctx, R.attr.blStroke))

    /** Pilihan segmen / chip: aktif = aksen penuh, tidak aktif = permukaan datar. */
    fun styleChoice(v: TextView, active: Boolean) {
        val ctx = v.context
        v.background = if (active) chipActive(ctx) else chip(ctx)
        v.setTextColor(if (active) onAccent(ctx) else androidx.core.content.ContextCompat.getColor(ctx, R.color.muted_light))
        v.compoundDrawableTintList = ColorStateList.valueOf(
            if (active) onAccent(ctx) else androidx.core.content.ContextCompat.getColor(ctx, R.color.muted_light)
        )
    }

    fun switchThumb(ctx: Context): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(onAccent(ctx).let { if (it == android.graphics.Color.WHITE) android.graphics.Color.WHITE else accent(ctx) }, 0xFF8A93A6.toInt())
    )

    fun switchTrack(ctx: Context): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(ThemeManager.withAlpha(accent(ctx), 140), 0xFF2A3042.toInt())
    )

    /** Terapkan semua tag skin di [root] dan anak-anaknya. */
    fun apply(root: View) {
        val ctx = root.context
        val tag = root.tag as? String
        if (tag != null && tag.startsWith("skin:")) {
            when (tag) {
                "skin:primary" -> { root.background = primary(ctx); (root as? TextView)?.setTextColor(onAccent(ctx)) }
                "skin:chip_active" -> { root.background = chipActive(ctx); (root as? TextView)?.setTextColor(onAccent(ctx)) }
                "skin:accent_text" -> (root as? TextView)?.setTextColor(accent(ctx))
                "skin:accent_bg" -> root.background = ColorDrawable(accent(ctx))
                "skin:tint" -> (root as? ImageView)?.imageTintList = ColorStateList.valueOf(accent(ctx))
            }
        }
        if (root is ViewGroup) for (i in 0 until root.childCount) apply(root.getChildAt(i))
    }
}
