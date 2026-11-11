package com.gamebracket.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView

/** ScrollView yang tingginya dibatasi (dipakai di panel overlay). */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    var maxHeightPx: Int = Int.MAX_VALUE

    /** true = tinggi pas [maxHeightPx] (diatur pengguna); false = mengikuti isi, maksimal [maxHeightPx]. */
    var exactHeight: Boolean = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val limited = View.MeasureSpec.makeMeasureSpec(
            maxHeightPx, if (exactHeight) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST
        )
        super.onMeasure(widthMeasureSpec, limited)
    }
}
