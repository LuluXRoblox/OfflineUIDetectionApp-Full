package com.lulux.detector.config

import com.lulux.detector.detection.DetectionState

class ConfigMatcher(private val configs: MutableList<RecoilConfig>) {
    fun findMatch(state: DetectionState): RecoilConfig? {
        if (!state.adsOpen) return null
        val w = state.weapon ?: return null
        val s = state.scope ?: return null
        return configs.firstOrNull {
            it.enabled &&
            it.weapon.equals(w, true) &&
            it.scope.equals(s, true) &&
            it.stance.equals(state.stance.name, true)
        }
    }

    fun add(config: RecoilConfig) { configs.removeAll {
        it.weapon.equals(config.weapon,true) &&
        it.scope.equals(config.scope,true) &&
        it.stance.equals(config.stance,true)
    }; configs += config }

    fun all(): List<RecoilConfig> = configs.toList()
}
