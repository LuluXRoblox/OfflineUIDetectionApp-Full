package com.lulux.detector.training

import android.graphics.Bitmap
import com.lulux.detector.roi.RoiRect
import kotlin.math.abs
import kotlin.math.sqrt

data class Classification(
    val matched: Boolean,
    val label: String?,
    val score: Float
)

object ImageFeatures {
    const val W = 24
    const val H = 24

    fun crop(frame: Bitmap, roi: RoiRect): Bitmap {
        val r = roi.normalized()
        val x = (frame.width * r.x).toInt().coerceIn(0, frame.width - 1)
        val y = (frame.height * r.y).toInt().coerceIn(0, frame.height - 1)
        val w = (frame.width * r.width).toInt().coerceIn(1, frame.width - x)
        val h = (frame.height * r.height).toInt().coerceIn(1, frame.height - y)
        return Bitmap.createBitmap(frame, x, y, w, h)
    }

    fun vector(bitmap: Bitmap): FloatArray {
        val small = Bitmap.createScaledBitmap(bitmap, W, H, true)
        val out = FloatArray(W * H)
        var i = 0
        var sum = 0.0
        var sum2 = 0.0
        for (y in 0 until H) {
            for (x in 0 until W) {
                val p = small.getPixel(x, y)
                val r = (p shr 16 and 255) / 255f
                val g = (p shr 8 and 255) / 255f
                val b = (p and 255) / 255f
                val lum = .299f*r + .587f*g + .114f*b
                out[i++] = lum
                sum += lum
                sum2 += lum*lum
            }
        }
        val mean = sum / out.size
        val sd = sqrt((sum2 / out.size - mean * mean).coerceAtLeast(1e-8)).toFloat()
        for (j in out.indices) out[j] = ((out[j] - mean.toFloat()) / sd).coerceIn(-3f,3f) / 3f
        return out
    }

    fun score(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var d = 0.0
        for (i in a.indices) d += abs(a[i] - b[i])
        val mae = d / a.size
        return (1f - (mae / 2f).toFloat()).coerceIn(0f,1f)
    }
}
