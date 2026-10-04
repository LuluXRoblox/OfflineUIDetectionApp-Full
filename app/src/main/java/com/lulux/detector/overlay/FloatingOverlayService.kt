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
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.lulux.detector.capture.ScreenCaptureService
import com.lulux.detector.roi.RoiConfig
import com.lulux.detector.storage.LocalStorage
import kotlin.math.abs

/**
 * Panel floating di atas game: status deteksi, ON/OFF, Train (semua training ada di sini), ROI editor.
 * Hanya membaca layar & menampilkan status. Tidak mengirim sentuhan/input apa pun.
 */
class FloatingOverlayService : Service() {
    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private var panel: LinearLayout? = null
    private var body: LinearLayout? = null
    private var trainMenu: LinearLayout? = null
    private var statusView: TextView? = null
    private var masterBtn: Button? = null
    private var editor: FrameLayout? = null
    private var inputWin: LinearLayout? = null
    private var target: Pair<String, String>? = null
    private var countdown = 0

    private val tick = object : Runnable {
        override fun run() {
            updateStatus()
            ui.postDelayed(this, 300)
        }
    }

    private val countdownTick = object : Runnable {
        override fun run() {
            if (countdown > 0) {
                ScreenCaptureService.lastMessage = "Capture dalam ${countdown}d ... siapkan kondisinya"
                countdown--
                ui.postDelayed(this, 1000)
            } else {
                val t = target
                if (t != null) {
                    ScreenCaptureService.lastMessage = "Mengambil sample ..."
                    ScreenCaptureService.pendingSample = t
                }
            }
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
        target = LocalStorage(this).loadTrainTarget()
        val ok = runCatching { buildPanel() }.isSuccess
        if (!ok) {
            Toast.makeText(this, "Gagal menampilkan panel floating", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
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

    private fun hRow(vararg views: View): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (v in views) r.addView(v)
        return r
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

        val master = makeButton("OFF") { toggleMaster() }
        val mainRow = hRow(
            master,
            makeButton("Train") { toggleTrainMenu() },
            makeButton("ROI") { showEditor() },
            makeButton("✕") { stopSelf() }
        )

        // Menu training: pilih target -> tekan Capture (hitung mundur 3 detik) saat kondisi aktif di game
        val train = LinearLayout(this)
        train.orientation = LinearLayout.VERTICAL
        train.visibility = View.GONE
        train.addView(hRow(
            makeButton("ADS buka") { setTarget("ads", "open") },
            makeButton("ADS tutup") { setTarget("ads_off", "off") }
        ))
        train.addView(hRow(
            makeButton("Senjata…") { askLabel("weapon", "Nama senjata, mis. M416") },
            makeButton("Scope…") { askLabel("scope", "Nama scope, mis. 3x") }
        ))
        train.addView(hRow(
            makeButton("4 Crouch") { setTarget("stance_crouch", "CROUCH") },
            makeButton("5 Prone") { setTarget("stance_prone", "PRONE") }
        ))
        train.addView(makeButton("● Capture (3 dtk)") { captureSample() })

        bodyLayout.addView(status)
        bodyLayout.addView(mainRow)
        bodyLayout.addView(train)
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
                        runCatching { wm.updateViewLayout(root, p) }
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
        body = bodyLayout
        trainMenu = train
        statusView = status
        masterBtn = master
    }

    private fun toggleTrainMenu() {
        val m = trainMenu ?: return
        m.visibility = if (m.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun setTarget(group: String, label: String) {
        target = Pair(group, label)
        LocalStorage(this).saveTrainTarget(group, label)
        ScreenCaptureService.lastMessage = "Target: $group = $label"
        updateStatus()
    }

    // Input nama senjata/scope: jendela kecil yang bisa fokus supaya keyboard muncul.
    private fun askLabel(group: String, hint: String) {
        if (inputWin != null) return
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(0xEE10161C.toInt())
        box.setPadding(dp(10), dp(8), dp(10), dp(8))

        val et = EditText(this)
        et.hint = hint
        et.setHintTextColor(Color.GRAY)
        et.setTextColor(Color.WHITE)
        et.setSingleLine(true)
        et.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_DONE
        et.minWidth = dp(220)

        box.addView(et)
        box.addView(hRow(
            makeButton("OK") {
                val l = et.text.toString().trim()
                if (l.isNotEmpty()) setTarget(group, l)
                closeInput()
            },
            makeButton("Batal") { closeInput() }
        ))

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        p.y = dp(16)
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        val ok = runCatching { wm.addView(box, p) }.isSuccess
        if (ok) {
            inputWin = box
            et.requestFocus()
        }
    }

    private fun closeInput() {
        val w = inputWin
        if (w != null) runCatching { wm.removeView(w) }
        inputWin = null
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
        if (target == null) {
            Toast.makeText(this, "Pilih target dulu (ADS / Senjata / Scope / Crouch / Prone)", Toast.LENGTH_SHORT).show()
            return
        }
        ui.removeCallbacks(countdownTick)
        countdown = 3
        ui.post(countdownTick)
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
        val m = trainMenu
        val t = target
        if (m != null && m.visibility == View.VISIBLE && t != null) {
            sb.append("\nTARGET : ").append(t.first).append(" = ").append(t.second)
        }
        val msg = ScreenCaptureService.lastMessage
        if (msg.isNotEmpty()) sb.append("\n").append(msg)
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

        val bar = hRow(
            makeButton("Simpan") {
                LocalStorage(this).saveRoi(view.toRoiConfig())
                ScreenCaptureService.lastMessage = "ROI tersimpan"
                closeEditor()
            },
            makeButton("Reset") { view.setConfig(RoiConfig()) },
            makeButton("Batal") { closeEditor() }
        )
        bar.setBackgroundColor(0xDD10161C.toInt())
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
        val ok = runCatching { wm.addView(frame, p) }.isSuccess
        if (ok) {
            editor = frame
            panel?.visibility = View.GONE
        }
    }

    private fun closeEditor() {
        val f = editor
        if (f != null) runCatching { wm.removeView(f) }
        editor = null
        panel?.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        ui.removeCallbacks(tick)
        ui.removeCallbacks(countdownTick)
        if (::wm.isInitialized) {
            closeInput()
            closeEditor()
            val p = panel
            if (p != null) runCatching { wm.removeView(p) }
            panel = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
