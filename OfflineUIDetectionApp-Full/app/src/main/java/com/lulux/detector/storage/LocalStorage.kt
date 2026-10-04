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
        fun put(k:String, x:RoiRect) {
            o.put(k, JSONObject().apply {
                put("x",x.x); put("y",x.y); put("w",x.width); put("h",x.height)
            })
        }
        put("ads",r.ads); put("weapon",r.weapon); put("scope",r.scope)
        put("crouch",r.crouch); put("prone",r.prone); put("control",r.control)
        prefs.edit().putString("roi",o.toString()).apply()
    }

    fun loadRoi(): RoiConfig {
        val s = prefs.getString("roi", null) ?: return RoiConfig()
        return runCatching {
            val o=JSONObject(s)
            fun get(k:String):RoiRect {
                val q=o.getJSONObject(k)
                return RoiRect(q.getDouble("x").toFloat(),q.getDouble("y").toFloat(),
                    q.getDouble("w").toFloat(),q.getDouble("h").toFloat())
            }
            RoiConfig(get("ads"),get("weapon"),get("scope"),get("crouch"),get("prone"),get("control"))
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
}
