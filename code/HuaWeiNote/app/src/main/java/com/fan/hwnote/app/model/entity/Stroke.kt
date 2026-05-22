package com.fan.hwnote.app.model.entity

/**
 * 单条手写笔画。
 * - color 形如 "#RRGGBB"
 * - width 单位 dp：1=细 / 3=中 / 6=粗
 * - points 是按时间顺序的采样点；坐标系是"内容总坐标"（相对 LinearLayout 顶部）
 */
data class Stroke(
    val brush: BrushType,
    val color: String,
    val width: Int,
    val points: List<StrokePoint>,
)

/** 单个采样点：x/y 像素，t = 自落笔起的相对毫秒。 */
data class StrokePoint(val x: Int, val y: Int, val t: Int)

enum class BrushType {
    PEN,      // 钢笔
    BRUSH,    // 画笔
    MARKER,   // 粗细笔
    PENCIL,   // 铅笔
}
