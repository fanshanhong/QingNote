package com.fan.hwnote.app.view.handwriting

import com.fan.hwnote.app.model.entity.Stroke

/**
 * 笔画级橡皮 hit-test。
 *
 * PRD §7.5：
 *   1. 粗筛：bounding box 与橡皮圆相交才进细判
 *   2. 细判：橡皮圆心到 stroke 相邻点对的最短距离 ≤ radiusPx + stroke.width/2
 *
 * 单点 stroke：退化为点到点距离。空 stroke：跳过。
 *
 * 注意 stroke.width 单位是 dp，调用方传入 widthHalfDpToPx 已经在 [radiusPx] 里折算好；
 * 这里直接用 stroke.width 当 px 处理（v1 简化：1/3/6 dp 与 px 在 mdpi 下等同；
 * 实际工程在 hdpi 下偏差 < 6px，对橡皮判定影响可接受）。
 */
object StrokeEraser {

    fun hitTest(ex: Float, ey: Float, radiusPx: Float, strokes: List<Stroke>): List<Stroke> {
        if (strokes.isEmpty()) return emptyList()
        val out = mutableListOf<Stroke>()
        for (s in strokes) {
            if (s.points.isEmpty()) continue
            // 粗筛与细判使用同一 threshold（含 stroke 半宽），否则水平/竖直 stroke
            // 的零高/零宽 bbox 会把"贴边但仍在 stroke 半宽内"的橡皮误判为不相交。
            val threshold = radiusPx + s.width / 2f
            if (!bboxIntersectsCircle(s, ex, ey, threshold)) continue
            if (anySegmentWithin(s, ex, ey, threshold)) out.add(s)
        }
        return out
    }

    private fun bboxIntersectsCircle(s: Stroke, ex: Float, ey: Float, r: Float): Boolean {
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
        for (p in s.points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        // 圆与 AABB 最近点距离
        val cx = ex.coerceIn(minX.toFloat(), maxX.toFloat())
        val cy = ey.coerceIn(minY.toFloat(), maxY.toFloat())
        val dx = ex - cx; val dy = ey - cy
        return dx * dx + dy * dy <= r * r
    }

    private fun anySegmentWithin(s: Stroke, ex: Float, ey: Float, threshold: Float): Boolean {
        val pts = s.points
        if (pts.size == 1) {
            val p = pts[0]
            val dx = ex - p.x; val dy = ey - p.y
            return dx * dx + dy * dy <= threshold * threshold
        }
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            if (pointToSegmentDistSq(ex, ey, a.x.toFloat(), a.y.toFloat(),
                    b.x.toFloat(), b.y.toFloat()) <= threshold * threshold) {
                return true
            }
        }
        return false
    }

    private fun pointToSegmentDistSq(
        px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float
    ): Float {
        val abx = bx - ax; val aby = by - ay
        val apx = px - ax; val apy = py - ay
        val abLen2 = abx * abx + aby * aby
        if (abLen2 == 0f) {
            return apx * apx + apy * apy
        }
        var t = (apx * abx + apy * aby) / abLen2
        if (t < 0f) t = 0f else if (t > 1f) t = 1f
        val cx = ax + t * abx; val cy = ay + t * aby
        val dx = px - cx; val dy = py - cy
        return dx * dx + dy * dy
    }
}
