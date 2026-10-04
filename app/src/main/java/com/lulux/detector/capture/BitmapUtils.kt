package com.lulux.detector.capture

import android.graphics.Bitmap
import android.media.Image
import java.nio.ByteBuffer

object BitmapUtils {
    fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride

        val bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        return if (paddedWidth == image.width) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }
}
