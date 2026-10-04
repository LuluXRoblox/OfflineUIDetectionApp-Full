package com.lulux.detector.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.lulux.detector.capture.ScreenCaptureService
import com.lulux.detector.roi.RoiConfig
import com.lulux.detector.storage.LocalStorage
import kotlin.math.abs

/**
 * Panel floating di atas game: status deteksi + tombol ON/OFF, Capture sample, ROI editor.
 * Hanya membaca layar & menampilkan status. Tidak mengirim sentuhan/input apa pun.
 */
class FloatingOverlayService : Service() {
    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var body: LinearLayout? = null
    private var statusView: TextView? = null
    private var masterBtn: Button? = null
    private var editor: FrameLayout? = null

    private val tick = object : Runnable {
        override fun run() {
            updateStatus()
            ui.postDelayed(this, 300)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1002, buildNotification())
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izin 'Tampil di atas aplikasi lain' belum aktif", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildPanel()
        ui.post(tick)
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("capture", "Screen Capture", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, "capture")
            .setContentTitle("Offline UI Detector")
            .setContentText("Panel floating aktif")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun makeButton(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 11f
        b.isAllCaps = false
        b.minHeight = 0
        b.minimumHeight = 0
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(dp(8), dp(4), dp(8), dp(4))
        b.setOnClickListener { onClick() }
        return b
    }

    private fun buildPanel() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xDD10161C.toInt())
        root.setPadding(dp(8), dp(6), dp(8), dp(6))

        val header = TextView(this)
        header.text = "◉ UI Detector  ▾"
        header.setTextColor(Color.WHITE)
        header.textSize = 12f
        header.setPadding(0, 0, 0, dp(4))

        val bodyLayout = LinearLayout(this)
        bodyLayout.orientation = LinearLayout.VERTICAL

        val status = TextView(this)
        status.setTextColor(0xFF9EE493.toInt())
        status.textSize = 11f
        status.typeface = Typeface.MONOSPACE
        status.text = "..."

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val master = makeButton("OFF") { toggleMaster() }
        row.addView(master)
        row.addView(makeButton("Capture") { captureSample() })
        row.addView(makeButton("ROI") { showEditor() })
        row.addView(makeButton("✕") { stopSelf() })

        bodyLayout.addView(status)
        bodyLayout.addView(row)
        root.addView(header)
        root.addView(bodyLayout)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = dp(8)
        p.y = dp(60)

        // drag lewat header, tap = lipat/buka
        header.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var downX = 0f
            var downY = 0f
            var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = p.x
                        startY = p.y
                        downX = e.rawX
                        downY = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (abs(dx) > 6 || abs(dy) > 6) moved = true
                        p.x = startX + dx
                        p.y = startY + dy
                        wm.updateViewLayout(root, p)
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            val b = body
                            if (b != null) {
                                b.visibility = if (b.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                            }
                        }
                    }
                }
                return true
            }
        })

        wm.addView(root, p)
        panel = root
        panelParams = p
        body = bodyLayout
        statusView = status
        masterBtn = master
    }

    private fun toggleMaster() {
        val e = ScreenCaptureService.engine
        if (e == null) {
            Toast.makeText(this, "Start Screen Capture dulu dari app", Toast.LENGTH_SHORT).show()
            return
        }
        e.setEnabled(!e.isEnabled())
        updateStatus()
    }

    private fun captureSample() {
        if (ScreenCaptureService.engine == null) {
            Toast.makeText(this, "Start Screen Capture dulu dari app", Toast.LENGTH_SHORT).show()
            return
        }
        val t = LocalStorage(this).loadTrainTarget()
        if (t == null) {
            Toast.makeText(this, "Pilih target dulu di app (tombol Train ...)", Toast.LENGTH_SHORT).show()
            return
        }
        ScreenCaptureService.lastMessage = "Mengambil sample: ${t.first} = ${t.second} ..."
        ScreenCaptureService.pendingSample = t
    }

    private fun updateStatus() {
        val e = ScreenCaptureService.engine
        val sb = StringBuilder()
        if (e == null) {
            masterBtn?.text = "OFF"
            sb.append("CAPTURE: OFF (mulai dari app)")
        } else {
            val s = e.getState()
            val on = e.isEnabled()
            masterBtn?.text = if (on) "ON" else "OFF"
            sb.append("MASTER : ").append(if (on) "ON" else "OFF")
            sb.append("\nADS    : ").append(if (s.adsOpen) "YA" else "tidak")
                .append(" ").append(String.format("%.2f", s.adsScore))
            sb.append("\nWEAPON : ").append(s.weapon ?: "-")
            sb.append("\nSCOPE  : ").append(s.scope ?: "-")
            sb.append("\nSTANCE : ").append(s.stance.name)
            sb.append("\nCONFIG : ").append(e.getMatchedConfig() ?: "-")
        }
        val m = ScreenCaptureService.lastMessage
        if (m.isNotEmpty()) sb.append("\n").append(m)
        statusView?.text = sb.toString()
    }

    @Suppress("DEPRECATION")
    private fun showEditor() {
        if (editor != null) return
        val real = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(real)

        val view = RoiEditorView(this, LocalStorage(this).loadRoi())
        val frame = FrameLayout(this)
        frame.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(0xDD10161C.toInt())
        bar.addView(makeButton("Simpan") {
            LocalStorage(this).saveRoi(view.toRoiConfig())
            ScreenCaptureService.lastMessage = "ROI tersimpan"
            closeEditor()
        })
        bar.addView(makeButton("Reset") { view.setConfig(RoiConfig()) })
        bar.addView(makeButton("Batal") { closeEditor() })
        val barLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        barLp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        barLp.topMargin = dp(8)
        frame.addView(bar, barLp)

        val p = WindowManager.LayoutParams(
            real.widthPixels,
            real.heightPixels,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = 0
        p.y = 0
        p.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        wm.addView(frame, p)
        editor = frame
        panel?.visibility = View.GONE
    }

    private fun closeEditor() {
        val f = editor
        if (f != null) runCatching { wm.removeView(f) }
        editor = null
        panel?.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        ui.removeCallbacks(tick)
        if (::wm.isInitialized) {
            closeEditor()
            val p = panel
            if (p != null) runCatching { wm.removeView(p) }
            panel = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
