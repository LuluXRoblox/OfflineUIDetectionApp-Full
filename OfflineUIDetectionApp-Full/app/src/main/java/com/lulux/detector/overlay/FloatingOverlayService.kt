package com.lulux.detector.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.lulux.detector.capture.ScreenCaptureService

class FloatingOverlayService : Service() {
    private var wm: WindowManager? = null
    private var view: TextView? = null

    override fun onCreate() {
        super.onCreate()
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        view=TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setPadding(12,8,12,8)
            text="MASTER: OFF"
        }
        val type=if(android.os.Build.VERSION.SDK_INT>=26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val p=WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        p.gravity=Gravity.TOP or Gravity.START
        p.x=16; p.y=80
        wm?.addView(view,p)
    }

    override fun onDestroy() {
        view?.let { runCatching { wm?.removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
