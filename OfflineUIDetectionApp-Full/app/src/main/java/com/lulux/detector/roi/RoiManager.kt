package com.lulux.detector.roi

class RoiManager(private var config: RoiConfig = RoiConfig()) {
    fun get(): RoiConfig = config
    fun set(value: RoiConfig) { config = value }
}
