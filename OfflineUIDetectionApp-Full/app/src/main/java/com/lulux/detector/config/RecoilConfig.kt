package com.lulux.detector.config

data class RecoilConfig(
    val weapon: String,
    val scope: String,
    val stance: String,
    val enabled: Boolean = true
)
