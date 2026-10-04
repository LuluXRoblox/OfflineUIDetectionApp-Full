package com.lulux.detector.training

import android.graphics.Bitmap
import com.lulux.detector.roi.RoiRect
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class TemplateRepository(private val dir: File) {
    private val file = File(dir, "templates.json")
    private val groups = mutableMapOf<String, MutableList<Template>>()

    init { load() }

    fun add(group: String, label: String, bitmap: Bitmap) {
        val list = groups.getOrPut(group) { mutableListOf() }
        list += Template(label, ImageFeatures.vector(bitmap), ImageFeatures.W, ImageFeatures.H)
        save()
    }

    fun classify(group: String, frame: Bitmap, roi: RoiRect, threshold: Float = 0.82f): Classification {
        val templates = groups[group].orEmpty()
        if (templates.isEmpty()) return Classification(false, null, 0f)

        val v = ImageFeatures.vector(ImageFeatures.crop(frame, roi))
        val best = templates
            .map { it to ImageFeatures.score(v, it.values) }
            .maxByOrNull { it.second } ?: return Classification(false, null, 0f)

        return Classification(best.second >= threshold, best.first.label, best.second)
    }

    fun labels(group: String): List<String> =
        groups[group].orEmpty().map { it.label }.distinct()

    private fun save() {
        val root = JSONObject()
        groups.forEach { (group, list) ->
            val arr = JSONArray()
            list.forEach { t ->
                val o = JSONObject()
                o.put("label", t.label)
                val a = JSONArray()
                t.values.forEach { a.put(it.toDouble()) }
                o.put("values", a)
                arr.put(o)
            }
            root.put(group, arr)
        }
        file.writeText(root.toString())
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            root.keys().forEach { group ->
                val arr = root.getJSONArray(group)
                val list = mutableListOf<Template>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val a = o.getJSONArray("values")
                    val values = FloatArray(a.length()) { j -> a.getDouble(j).toFloat() }
                    list += Template(o.getString("label"), values, ImageFeatures.W, ImageFeatures.H)
                }
                groups[group] = list
            }
        }
    }
}
