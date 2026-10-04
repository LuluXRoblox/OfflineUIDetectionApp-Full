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

// Default diukur dari screenshot HUD PUBG Mobile (landscape), label 1..5.
// Bisa digeser lewat tombol ROI di panel floating.
data class RoiConfig(
    val ads: RoiRect = RoiRect(.882f, .293f, .067f, .145f),     // 3: tombol ADS
    val weapon: RoiRect = RoiRect(.390f, .862f, .220f, .085f),  // 1: slot senjata
    val scope: RoiRect = RoiRect(.693f, .300f, .045f, .095f),   // 2: indikator scope (6x)
    val crouch: RoiRect = RoiRect(.902f, .823f, .065f, .145f),  // 4: tombol stance
    val prone: RoiRect = RoiRect(.813f, .843f, .064f, .139f),   // 5: tombol stance
    val control: RoiRect = RoiRect(.55f, .55f, .25f, .30f)
)
