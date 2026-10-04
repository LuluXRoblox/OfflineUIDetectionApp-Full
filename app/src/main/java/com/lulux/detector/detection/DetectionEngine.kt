package com.lulux.detector.detection

import android.graphics.Bitmap
import com.lulux.detector.config.ConfigMatcher
import com.lulux.detector.roi.RoiConfig
import com.lulux.detector.training.TemplateRepository

class DetectionEngine(
    private val templates: TemplateRepository,
    private val matcher: ConfigMatcher
) {
    @Volatile private var enabled = false
    @Volatile private var state = DetectionState()
    @Volatile private var lastConfig: String? = null

    fun setEnabled(v: Boolean) {
        enabled = v
        if (!v) {
            state = DetectionState()
            lastConfig = null
        }
    }

    fun isEnabled() = enabled
    fun getState() = state
    fun getMatchedConfig() = lastConfig

    fun process(frame: Bitmap, roi: RoiConfig) {
        if (!enabled) return

        // ADS terbuka = mirip sample "ads" DAN lebih mirip dari sample "ads_off" (kalau ada)
        val open = templates.classify("ads", frame, roi.ads)
        val off = templates.classify("ads_off", frame, roi.ads)
        val adsOpen = open.matched && open.score >= off.score
        if (!adsOpen) {
            state = DetectionState(adsOpen = false, adsScore = open.score)
            lastConfig = null
            return
        }

        val weapon = templates.classify("weapon", frame, roi.weapon)
        val scope = templates.classify("scope", frame, roi.scope)
        val prone = templates.classify("stance_prone", frame, roi.prone)
        val crouch = templates.classify("stance_crouch", frame, roi.crouch)

        val stance = when {
            prone.matched && prone.score >= crouch.score -> Stance.PRONE
            crouch.matched -> Stance.CROUCH
            else -> Stance.STAND
        }

        state = DetectionState(
            adsOpen = true,
            weapon = if (weapon.matched) weapon.label else null,
            scope = if (scope.matched) scope.label else null,
            stance = stance,
            confidence = minOf(open.score, weapon.score, scope.score),
            adsScore = open.score
        )

        lastConfig = matcher.findMatch(state)?.let {
            "${it.weapon} / ${it.scope} / ${it.stance}"
        }
    }
}
