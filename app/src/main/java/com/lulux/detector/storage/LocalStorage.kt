package com.lulux.detector.storage

import android.content.Context
import com.lulux.detector.config.RecoilConfig
import com.lulux.detector.roi.RoiConfig
import com.lulux.detector.roi.RoiRect
import org.json.JSONArray
import org.json.JSONObject

class LocalStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("detector", Context.MODE_PRIVATE)

    fun saveRoi(r: RoiConfig) {
        val o = JSONObject()
        fun putRect(k:String, x:RoiRect) {
            o.put(k, JSONObject().apply {
                put("x",x.x); put("y",x.y); put("w",x.width); put("h",x.height)
            })
        }
        putRect("ads",r.ads); putRect("weapon",r.weapon); putRect("scope",r.scope)
        putRect("crouch",r.crouch); putRect("prone",r.prone); putRect("control",r.control)
        prefs.edit().putString("roi",o.toString()).apply()
    }

    fun loadRoi(): RoiConfig {
        val s = prefs.getString("roi", null) ?: return RoiConfig()
        return runCatching {
            val o=JSONObject(s)
            fun getRect(k:String):RoiRect {
                val q=o.getJSONObject(k)
                return RoiRect(q.getDouble("x").toFloat(),q.getDouble("y").toFloat(),
                    q.getDouble("w").toFloat(),q.getDouble("h").toFloat())
            }
            RoiConfig(getRect("ads"),getRect("weapon"),getRect("scope"),getRect("crouch"),getRect("prone"),getRect("control"))
        }.getOrDefault(RoiConfig())
    }

    fun saveConfigs(list: List<RecoilConfig>) {
        val a=JSONArray()
        list.forEach {
            a.put(JSONObject().apply {
                put("weapon",it.weapon); put("scope",it.scope)
                put("stance",it.stance); put("enabled",it.enabled)
            })
        }
        prefs.edit().putString("configs",a.toString()).apply()
    }

    fun loadConfigs(): MutableList<RecoilConfig> {
        val a=runCatching { JSONArray(prefs.getString("configs","[]")) }.getOrDefault(JSONArray())
        val out=mutableListOf<RecoilConfig>()
        for(i in 0 until a.length()) {
            val o=a.getJSONObject(i)
            out += RecoilConfig(o.getString("weapon"),o.getString("scope"),o.getString("stance"),o.optBoolean("enabled",true))
        }
        return out
    }

    fun saveTrainTarget(group: String, label: String) {
        prefs.edit().putString("train_group", group).putString("train_label", label).apply()
    }

    fun loadTrainTarget(): Pair<String, String>? {
        val g = prefs.getString("train_group", null) ?: return null
        val l = prefs.getString("train_label", null) ?: return null
        return Pair(g, l)
    }

    fun loadDragInterval(): Long = prefs.getLong("drag_interval_ms", 500L).coerceIn(50L, 60000L)

    fun loadDragDistance(): Int = prefs.getInt("drag_distance_px", 20).coerceIn(1, 2000)

    fun saveDragSettings(intervalMs: Long, distancePx: Int) {
        prefs.edit()
            .putLong("drag_interval_ms", intervalMs.coerceIn(50L, 60000L))
            .putInt("drag_distance_px", distancePx.coerceIn(1, 2000))
            .apply()
    }

    fun loadThreshold(): Float = prefs.getFloat("threshold", 0.88f)

    fun saveThreshold(v: Float) {
        prefs.edit().putFloat("threshold", v).apply()
    }
}
