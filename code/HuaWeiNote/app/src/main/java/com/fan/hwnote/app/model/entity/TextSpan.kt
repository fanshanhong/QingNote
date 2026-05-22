package com.fan.hwnote.app.model.entity

/**
 * 行内文本样式 span。覆盖范围：[start, end)。
 * value 仅 FONT_SIZE / COLOR 用：FONT_SIZE 取 "small"/"medium"/"large"，COLOR 取 "#RRGGBB"。
 */
data class TextSpan(
    val start: Int,
    val end: Int,
    val type: SpanType,
    val value: String? = null,
)

enum class SpanType {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    FONT_SIZE,
    COLOR,
}
