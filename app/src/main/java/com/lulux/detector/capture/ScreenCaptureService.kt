package com.lulux.detector.capture

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import com.lulux.detector.config.ConfigMatcher
import com.lulux.detector.detection.DetectionEngine
import com.lulux.detector.roi.RoiManager
import com.lulux.detector.storage.LocalStorage
import com.lulux.detector.training.TemplateRepository

class ScreenCaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val ACTION_STOP = "STOP"
        var engine: DetectionEngine? = null
        var running = false
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var lastNs = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            1001,
            Notification.Builder(this, "capture")
                .setContentTitle("Offline UI Detector")
                .setContentText("Screen detection active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()
        )

        if (intent == null) return START_NOT_STICKY
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)

        if (data == null) return START_NOT_STICKY
        startCapture(code, data)
        return START_STICKY
    }

    private fun startCapture(code: Int, data: Intent) {
        if (running) return

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = pm.getMediaProjection(code, data)

        val storage = LocalStorage(this)
        val repo = TemplateRepository(filesDir)
        engine = DetectionEngine(repo, ConfigMatcher(storage.loadConfigs()))

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader!!.setOnImageAvailableListener({ ir ->
            val now = System.nanoTime()
            if (now - lastNs < 100_000_000L) return@setOnImageAvailableListener
            lastNs = now

            val image = runCatching { ir.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                val bitmap = BitmapUtils.imageToBitmap(image) ?: return@setOnImageAvailableListener
                engine?.process(bitmap, RoiManager(storage.loadRoi()).get())
                bitmap.recycle()
            } finally {
                image.close()
            }
        }, Handler(Looper.getMainLooper()))

        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture() }
        }, Handler(Looper.getMainLooper()))

        display = projection!!.createVirtualDisplay(
            "OfflineUIDetection",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, null
        )
        running = true
    }

    private fun stopCapture() {
        running = false
        display?.release(); display=null
        reader?.close(); reader=null
        projection?.stop(); projection=null
        engine?.setEnabled(false)
        engine=null
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
