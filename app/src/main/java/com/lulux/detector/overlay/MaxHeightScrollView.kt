package com.lulux.detector.overlay

import android.content.Context
import android.widget.ScrollView

/** ScrollView yang tingginya dibatasi, supaya isi panel yang panjang bisa di-scroll (layar landscape pendek). */
class MaxHeightScrollView(context: Context, private val maxH: Int) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
    }
}
