package com.lulux.detector.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.lulux.detector.roi.RoiConfig
import com.lulux.detector.roi.RoiRect
import kotlin.math.abs

/** Kotak ROI yang bisa digeser (badan kotak) dan diubah ukurannya (kotak kecil di pojok kanan-bawah). */
class RoiEditorView(context: Context, initial: RoiConfig) : View(context) {

    private class Box(
        val name: String,
        val color: Int,
        var x: Float,
        var y: Float,
        var w: Float,
        var h: Float
    )

    private val density = context.resources.displayMetrics.density
    private val boxes = mutableListOf<Box>()
    private var control: RoiRect = initial.control
    private var active: Box? = null
    private var selected: Box? = null
    private var mode = 0          // 1 = geser, 2 = ubah ukuran
    private var lastX = 0f
    private var lastY = 0f

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
    }
    private val hitR = 28f * density
    private val handleHalf = 8f * density

    init { setConfig(initial) }

    fun setConfig(c: RoiConfig) {
        boxes.clear()
        control = c.control
        boxes += Box("1 Weapon", 0xFFFFC107.toInt(), c.weapon.x, c.weapon.y, c.weapon.width, c.weapon.height)
        boxes += Box("2 Scope", 0xFF4FC3F7.toInt(), c.scope.x, c.scope.y, c.scope.width, c.scope.height)
        boxes += Box("3 ADS", 0xFFFF5252.toInt(), c.ads.x, c.ads.y, c.ads.width, c.ads.height)
        boxes += Box("4 Crouch", 0xFF69F0AE.toInt(), c.crouch.x, c.crouch.y, c.crouch.width, c.crouch.height)
        boxes += Box("5 Prone", 0xFFE040FB.toInt(), c.prone.x, c.prone.y, c.prone.width, c.prone.height)
        selected = boxes.firstOrNull()
        invalidate()
    }

    /** Geser kotak terpilih sebanyak dxPx/dyPx piksel layar (positif = kanan/bawah). */
    fun nudge(dxPx: Int, dyPx: Int) {
        val b = selected ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        b.x = (b.x + dxPx / vw).coerceIn(0f, 1f - b.w)
        b.y = (b.y + dyPx / vh).coerceIn(0f, 1f - b.h)
        invalidate()
    }

    /** Ubah ukuran kotak terpilih sebanyak dwPx/dhPx piksel layar. */
    fun resizeBy(dwPx: Int, dhPx: Int) {
        val b = selected ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        b.w = (b.w + dwPx / vw).coerceIn(0.005f, 1f - b.x)
        b.h = (b.h + dhPx / vh).coerceIn(0.005f, 1f - b.y)
        invalidate()
    }

    fun toRoiConfig(): RoiConfig {
        fun rect(i: Int): RoiRect {
            val b = boxes[i]
            return RoiRect(b.x, b.y, b.w, b.h)
        }
        return RoiConfig(
            ads = rect(2),
            weapon = rect(0),
            scope = rect(1),
            crouch = rect(3),
            prone = rect(4),
            control = control
        )
    }

    override fun onDraw(canvas: Canvas) {
        val vw = width.toFloat()
        val vh = height.toFloat()
        for (b in boxes) {
            val l = b.x * vw
            val t = b.y * vh
            val r = (b.x + b.w) * vw
            val bt = (b.y + b.h) * vh
            fillPaint.color = (b.color and 0x00FFFFFF) or 0x33000000
            canvas.drawRect(l, t, r, bt, fillPaint)
            strokePaint.color = b.color
            strokePaint.strokeWidth = (if (b === selected) 4f else 2f) * density
            canvas.drawRect(l, t, r, bt, strokePaint)
            fillPaint.color = b.color
            canvas.drawRect(r - handleHalf, bt - handleHalf, r + handleHalf, bt + handleHalf, fillPaint)
            textPaint.color = b.color
            canvas.drawText(b.name, l, t - 4f * density, textPaint)
        }
        val sel = selected
        if (sel != null) {
            textPaint.color = sel.color
            canvas.drawText(
                sel.name + ": x=" + (sel.x * vw).toInt() + " y=" + (sel.y * vh).toInt() +
                    " w=" + (sel.w * vw).toInt() + " h=" + (sel.h * vh).toInt() + " px",
                12f * density, vh - 40f * density, textPaint
            )
        }
        textPaint.color = Color.WHITE
        canvas.drawText(
            "Geser kotak ke ikon HUD. Tarik kotak kecil di pojok kanan-bawah untuk ubah ukuran.",
            12f * density, vh - 16f * density, textPaint
        )
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = null
                mode = 0
                for (b in boxes.asReversed()) {
                    val hx = (b.x + b.w) * vw
                    val hy = (b.y + b.h) * vh
                    if (abs(e.x - hx) < hitR && abs(e.y - hy) < hitR) {
                        active = b
                        mode = 2
                        break
                    }
                }
                if (active == null) {
                    for (b in boxes.asReversed()) {
                        if (e.x >= b.x * vw && e.x <= (b.x + b.w) * vw &&
                            e.y >= b.y * vh && e.y <= (b.y + b.h) * vh
                        ) {
                            active = b
                            mode = 1
                            break
                        }
                    }
                }
                val hit = active
                if (hit != null) selected = hit
                lastX = e.x
                lastY = e.y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val b = active
                if (b != null) {
                    val dx = (e.x - lastX) / vw
                    val dy = (e.y - lastY) / vh
                    lastX = e.x
                    lastY = e.y
                    if (mode == 1) {
                        b.x = (b.x + dx).coerceIn(0f, 1f - b.w)
                        b.y = (b.y + dy).coerceIn(0f, 1f - b.h)
                    } else if (mode == 2) {
                        b.w = (b.w + dx).coerceIn(0.01f, 1f - b.x)
                        b.h = (b.h + dy).coerceIn(0.01f, 1f - b.y)
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                active = null
                mode = 0
            }
        }
        return true
    }
}
