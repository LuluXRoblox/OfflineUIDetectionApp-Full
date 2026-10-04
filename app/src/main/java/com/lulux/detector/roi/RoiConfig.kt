package com.lulux.detector.roi

data class RoiRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    fun normalized() = copy(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        width = width.coerceIn(0.001f, 1f),
        height = height.coerceIn(0.001f, 1f)
    )
}

data class RoiConfig(
    val ads: RoiRect = RoiRect(.45f,.35f,.10f,.10f),
    val weapon: RoiRect = RoiRect(.80f,.05f,.15f,.12f),
    val scope: RoiRect = RoiRect(.43f,.02f,.14f,.08f),
    val crouch: RoiRect = RoiRect(.78f,.70f,.10f,.10f),
    val prone: RoiRect = RoiRect(.68f,.70f,.10f,.10f),
    val control: RoiRect = RoiRect(.55f,.55f,.25f,.30f)
)
