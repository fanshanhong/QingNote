package com.fan.hwnote.app.model.json

import com.fan.hwnote.app.model.entity.Block
import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.model.entity.ChecklistItem
import com.fan.hwnote.app.model.entity.Heading
import com.fan.hwnote.app.model.entity.NoteContent
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.Stroke
import com.fan.hwnote.app.model.entity.StrokePoint
import com.fan.hwnote.app.model.entity.TextSpan
import org.json.JSONArray
import org.json.JSONObject

/**
 * NoteContent ↔ JSON 序列化。
 *
 * 容错策略：
 * - fromJson 解析失败（顶层 JSON 异常 / 非对象）：返回 NoteContent.empty()
 * - block 类型未知 / 必填字段缺失：跳过该块
 * - span 类型未知：跳过该 span
 * - stroke brush 未知 / points 缺失：跳过该 stroke
 *
 * 持久化格式见 PRD §6.2。
 */
object NoteJson {

    fun toJson(content: NoteContent): String {
        val root = JSONObject()
        val blocksArr = JSONArray()
        for (b in content.blocks) blocksArr.put(blockToJson(b))
        root.put("blocks", blocksArr)

        val hw = JSONObject()
        val strokesArr = JSONArray()
        for (s in content.handwriting) strokesArr.put(strokeToJson(s))
        hw.put("strokes", strokesArr)
        root.put("handwriting", hw)

        return root.toString()
    }

    fun fromJson(s: String): NoteContent {
        return try {
            val root = JSONObject(s)
            val blocks = mutableListOf<Block>()
            val arr = root.optJSONArray("blocks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                blockFromJson(obj)?.let { blocks.add(it) }
            }
            val strokes = mutableListOf<Stroke>()
            val hw = root.optJSONObject("handwriting") ?: JSONObject()
            val sArr = hw.optJSONArray("strokes") ?: JSONArray()
            for (i in 0 until sArr.length()) {
                val obj = sArr.optJSONObject(i) ?: continue
                strokeFromJson(obj)?.let { strokes.add(it) }
            }
            NoteContent(blocks, strokes)
        } catch (_: Exception) {
            NoteContent.empty()
        }
    }

    // ----- Block -----

    private fun blockToJson(b: Block): JSONObject = when (b) {
        is Block.TextBlock -> JSONObject().apply {
            put("type", "text")
            put("id", b.id)
            b.heading?.let { put("heading", it.name.lowercase()) }
            put("text", b.text)
            put("spans", spansToJson(b.spans))
        }
        is Block.ImageBlock -> JSONObject().apply {
            put("type", "image")
            put("id", b.id)
            put("fileName", b.fileName)
            put("width", b.width)
            put("height", b.height)
        }
        is Block.ChecklistBlock -> JSONObject().apply {
            put("type", "checklist")
            put("id", b.id)
            val items = JSONArray()
            for (it in b.items) {
                items.put(JSONObject().apply {
                    put("checked", it.checked)
                    put("text", it.text)
                })
            }
            put("items", items)
        }
    }

    private fun blockFromJson(o: JSONObject): Block? = when (o.optString("type")) {
        "text" -> Block.TextBlock(
            id = o.optString("id"),
            heading = o.optString("heading", "")
                .takeIf { it.isNotEmpty() }
                ?.let { runCatching { Heading.valueOf(it.uppercase()) }.getOrNull() },
            text = o.optString("text"),
            spans = spansFromJson(o.optJSONArray("spans") ?: JSONArray()),
        )
        "image" -> Block.ImageBlock(
            id = o.optString("id"),
            fileName = o.optString("fileName"),
            width = o.optInt("width"),
            height = o.optInt("height"),
        )
        "checklist" -> {
            val items = mutableListOf<ChecklistItem>()
            val arr = o.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                items.add(ChecklistItem(
                    checked = it.optBoolean("checked"),
                    text = it.optString("text"),
                ))
            }
            Block.ChecklistBlock(id = o.optString("id"), items = items)
        }
        else -> null
    }

    // ----- TextSpan -----

    private fun spansToJson(spans: List<TextSpan>): JSONArray {
        val arr = JSONArray()
        for (sp in spans) {
            arr.put(JSONObject().apply {
                put("start", sp.start)
                put("end", sp.end)
                put("type", sp.type.name.lowercase())
                sp.value?.let { put("value", it) }
            })
        }
        return arr
    }

    private fun spansFromJson(arr: JSONArray): List<TextSpan> {
        val out = mutableListOf<TextSpan>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = runCatching {
                SpanType.valueOf(o.optString("type").uppercase())
            }.getOrNull() ?: continue
            out.add(TextSpan(
                start = o.optInt("start"),
                end = o.optInt("end"),
                type = type,
                value = if (o.has("value")) o.optString("value") else null,
            ))
        }
        return out
    }

    // ----- Stroke -----

    private fun strokeToJson(s: Stroke): JSONObject = JSONObject().apply {
        put("brush", s.brush.name.lowercase())
        put("color", s.color)
        put("width", s.width)
        val pts = JSONArray()
        for (p in s.points) {
            pts.put(JSONArray().apply {
                put(p.x); put(p.y); put(p.t)
            })
        }
        put("points", pts)
    }

    private fun strokeFromJson(o: JSONObject): Stroke? {
        val brush = runCatching {
            BrushType.valueOf(o.optString("brush").uppercase())
        }.getOrNull() ?: return null
        val arr = o.optJSONArray("points") ?: return null
        val pts = mutableListOf<StrokePoint>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONArray(i) ?: continue
            if (p.length() < 3) continue
            pts.add(StrokePoint(
                x = p.optInt(0),
                y = p.optInt(1),
                t = p.optInt(2),
            ))
        }
        return Stroke(
            brush = brush,
            color = o.optString("color"),
            width = o.optInt("width"),
            points = pts,
        )
    }
}
