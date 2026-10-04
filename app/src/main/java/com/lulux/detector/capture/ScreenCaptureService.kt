package com.lulux.detector.capture

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.lulux.detector.config.ConfigMatcher
import com.lulux.detector.detection.DetectionEngine
import com.lulux.detector.roi.RoiConfig
import com.lulux.detector.roi.RoiManager
import com.lulux.detector.storage.LocalStorage
import com.lulux.detector.training.ImageFeatures
import com.lulux.detector.training.TemplateRepository

class ScreenCaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val ACTION_STOP = "STOP"
        var engine: DetectionEngine? = null
        var running = false

        // diisi panel floating, diproses di frame berikutnya
        @Volatile var pendingSample: Pair<String, String>? = null
        @Volatile var lastMessage: String = ""
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastNs = 0L
    private var curW = 0
    private var curH = 0
    private var storage: LocalStorage? = null
    private var repo: TemplateRepository? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) { resizeIfNeeded() }
    }

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
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)

        if (data == null) return START_NOT_STICKY
        startCapture(code, data)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun realMetrics(): DisplayMetrics {
        val dm = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(dm)
        return dm
    }

    private fun newReader(w: Int, h: Int): ImageReader {
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        r.setOnImageAvailableListener({ ir -> runCatching { handleFrame(ir) } }, workerHandler)
        return r
    }

    private fun startCapture(code: Int, data: Intent) {
        if (running) return

        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = pm.getMediaProjection(code, data) ?: return
        projection = proj

        // Ukuran SELALU ukuran layar asli saat ini (landscape saat main game)
        val dm = realMetrics()
        curW = dm.widthPixels
        curH = dm.heightPixels

        val st = LocalStorage(this)
        val rp = TemplateRepository(filesDir)
        storage = st
        repo = rp
        engine = DetectionEngine(rp, ConfigMatcher(st.loadConfigs()))

        val wt = HandlerThread("capture-worker")
        wt.start()
        worker = wt
        workerHandler = Handler(wt.looper)

        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                mainHandler.post { stopCapture(); stopSelf() }
            }
        }, mainHandler)

        val r = newReader(curW, curH)
        reader = r
        display = proj.createVirtualDisplay(
            "OfflineUIDetection",
            curW, curH, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, null
        )
        (getSystemService(DISPLAY_SERVICE) as DisplayManager)
            .registerDisplayListener(displayListener, mainHandler)
        running = true
    }

    // Layar diputar (portrait <-> landscape): ukuran capture harus ikut, kalau tidak ROI meleset.
    private fun resizeIfNeeded() {
        val vd = display ?: return
        val dm = realMetrics()
        if (dm.widthPixels == curW && dm.heightPixels == curH) return
        curW = dm.widthPixels
        curH = dm.heightPixels
        val old = reader
        val nr = newReader(curW, curH)
        reader = nr
        vd.resize(curW, curH, dm.densityDpi)
        vd.surface = nr.surface
        old?.close()
    }

    private fun handleFrame(ir: ImageReader) {
        val image = ir.acquireLatestImage() ?: return
        try {
            val pending = pendingSample
            val now = System.nanoTime()
            if (pending == null && now - lastNs < 100_000_000L) return   // ~10 fps
            lastNs = now

            val st = storage ?: return
            val bitmap = BitmapUtils.imageToBitmap(image) ?: return
            val roi = RoiManager(st.loadRoi()).get()

            if (pending != null) {
                pendingSample = null
                saveSample(pending, bitmap, roi)
            }
            engine?.process(bitmap, roi)
            bitmap.recycle()
        } finally {
            image.close()
        }
    }

    private fun saveSample(t: Pair<String, String>, frame: Bitmap, roi: RoiConfig) {
        val rect = when (t.first) {
            "ads", "ads_off" -> roi.ads
            "weapon" -> roi.weapon
            "scope" -> roi.scope
            "stance_crouch" -> roi.crouch
            "stance_prone" -> roi.prone
            else -> null
        }
        val ok = rect != null && runCatching {
            repo?.add(t.first, t.second, ImageFeatures.crop(frame, rect))
        }.isSuccess
        lastMessage = if (ok) "Sample OK: ${t.first} = ${t.second}" else "Sample GAGAL: ${t.first}"
    }

    private fun stopCapture() {
        running = false
        pendingSample = null
        runCatching {
            (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(displayListener)
        }
        display?.release(); display = null
        reader?.close(); reader = null
        val p = projection
        projection = null
        p?.stop()
        engine?.setEnabled(false)
        engine = null
        worker?.quitSafely(); worker = null
        workerHandler = null
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
