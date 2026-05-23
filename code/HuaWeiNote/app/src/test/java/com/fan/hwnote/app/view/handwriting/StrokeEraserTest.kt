package com.fan.hwnote.app.view.handwriting

import com.fan.hwnote.app.model.entity.BrushType
import com.fan.hwnote.app.model.entity.Stroke
import com.fan.hwnote.app.model.entity.StrokePoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrokeEraserTest {

    private fun stroke(vararg pts: Pair<Int, Int>, width: Int = 3): Stroke =
        Stroke(
            brush = BrushType.PEN,
            color = "#212121",
            width = width,
            points = pts.mapIndexed { i, (x, y) -> StrokePoint(x, y, i * 10) },
        )

    @Test
    fun eraser_far_from_all_strokes_hits_nothing() {
        val s1 = stroke(0 to 0, 10 to 0, 20 to 0)
        val s2 = stroke(0 to 100, 50 to 100)
        val hit = StrokeEraser.hitTest(ex = 500f, ey = 500f, radiusPx = 8f, strokes = listOf(s1, s2))
        assertEquals(emptyList<Stroke>(), hit)
    }

    @Test
    fun eraser_overlapping_one_stroke_hits_only_that_one() {
        val s1 = stroke(0 to 0, 100 to 0)            // 水平线 y=0
        val s2 = stroke(0 to 200, 100 to 200)        // 水平线 y=200
        val hit = StrokeEraser.hitTest(ex = 50f, ey = 5f, radiusPx = 10f, strokes = listOf(s1, s2))
        assertEquals(listOf(s1), hit)
    }

    @Test
    fun eraser_grazing_bounding_box_but_far_from_segment_misses() {
        // L 形 stroke：bbox 覆盖 (0,0)-(100,100) 但 stroke 本身只在两条边
        val s = stroke(0 to 0, 100 to 0, 100 to 100)
        // 橡皮在 bbox 内但远离两条边（中心点 50,50，距两条边都 50px）
        val hit = StrokeEraser.hitTest(ex = 50f, ey = 50f, radiusPx = 5f, strokes = listOf(s))
        assertTrue(hit.isEmpty(), "在 bbox 内但远离实际 stroke 段不该命中")
    }

    @Test
    fun eraser_radius_plus_stroke_half_width_extends_hit_distance() {
        // stroke width=10dp（粗），eraser 圆心距离线段 7px：基础半径 5 + width/2≈5 ⇒ 应命中
        val s = stroke(0 to 0, 100 to 0, width = 10)
        val hit = StrokeEraser.hitTest(ex = 50f, ey = 7f, radiusPx = 5f, strokes = listOf(s))
        assertEquals(listOf(s), hit)
    }

    @Test
    fun single_point_stroke_treated_as_zero_length_segment() {
        // 单点 stroke：算法应当退化成点到点距离判断，而不是抛 NPE 或漏判
        val s = stroke(50 to 50)
        val hit = StrokeEraser.hitTest(ex = 52f, ey = 50f, radiusPx = 5f, strokes = listOf(s))
        assertEquals(listOf(s), hit)
    }
}
