package com.lulux.detector.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.lulux.detector.storage.LocalStorage

/** Executes the configured repeated downward drag through Android AccessibilityService. */
class TouchAutomationService : AccessibilityService() {
    companion object {
        @Volatile var instance: TouchAutomationService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var intervalMs = 500L
    private var distancePx = 20
    private var startX = 0f
    private var startY = 0f

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            performDownwardDrag()
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val s = LocalStorage(this)
        intervalMs = s.loadDragInterval()
        distancePx = s.loadDragDistance()
    }

    fun configure(interval: Long, distance: Int) {
        intervalMs = interval.coerceIn(50L, 60000L)
        distancePx = distance.coerceIn(1, 2000)
        LocalStorage(this).saveDragSettings(intervalMs, distancePx)
    }

    fun setStartPoint(x: Float, y: Float) {
        startX = x
        startY = y
    }

    fun isRunning(): Boolean = running

    fun startDragLoop() {
        if (running || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        running = true
        handler.removeCallbacks(loop)
        handler.post(loop)
    }

    fun stopDragLoop() {
        running = false
        handler.removeCallbacks(loop)
    }

    private fun performDownwardDrag() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        val x = if (startX > 0f) startX else dm.widthPixels / 2f
        val y = if (startY > 0f) startY else dm.heightPixels / 2f
        val endY = (y + distancePx).coerceAtMost(dm.heightPixels - 2f)

        val path = Path().apply {
            moveTo(x.coerceIn(1f, dm.widthPixels - 2f), y.coerceIn(1f, dm.heightPixels - 2f))
            lineTo(x.coerceIn(1f, dm.widthPixels - 2f), endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { stopDragLoop() }

    override fun onDestroy() {
        stopDragLoop()
        if (instance === this) instance = null
        super.onDestroy()
    }
}
