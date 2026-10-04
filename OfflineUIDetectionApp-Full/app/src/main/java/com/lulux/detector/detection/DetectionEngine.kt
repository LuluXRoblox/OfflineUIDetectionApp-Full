package com.lulux.detector.detection

import com.lulux.detector.config.ConfigMatcher
import com.lulux.detector.roi.RoiConfig
import com.lulux.detector.training.TemplateRepository
import android.graphics.Bitmap

class DetectionEngine(
    private val templates: TemplateRepository,
    private val matcher: ConfigMatcher
) {
    private var enabled = false
    private var state = DetectionState()
    private var lastConfig: String? = null

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

        val adsResult = templates.classify("ads", frame, roi.ads)
        if (!adsResult.matched) {
            state = DetectionState(adsOpen = false)
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
            weapon = weapon.label,
            scope = scope.label,
            stance = stance,
            confidence = minOf(adsResult.score, weapon.score, scope.score)
        )

        lastConfig = matcher.findMatch(state)?.let {
            "${it.weapon} / ${it.scope} / ${it.stance}"
        }
    }
}
