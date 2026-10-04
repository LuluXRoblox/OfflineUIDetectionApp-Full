package com.lulux.detector.detection

data class DetectionState(
    val adsOpen: Boolean = false,
    val weapon: String? = null,
    val scope: String? = null,
    val stance: Stance = Stance.STAND,
    val confidence: Float = 0f,
    val adsScore: Float = 0f
)

enum class Stance { STAND, CROUCH, PRONE }
