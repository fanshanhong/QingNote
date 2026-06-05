package com.fan.hwnote.app.util

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan

private const val SIZE_XS = 0.75f
private const val SIZE_SMALL = 0.85f
private const val SIZE_MEDIUM = 1.0f
private const val SIZE_LARGE = 1.25f
private const val SIZE_XL = 1.5f

fun List<TextSpan>.applyTo(sp: Spannable) {
    val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    for (s in this) {
        if (s.start < 0 || s.end > sp.length || s.start >= s.end) continue
        when (s.type) {
            SpanType.BOLD -> sp.setSpan(StyleSpan(Typeface.BOLD), s.start, s.end, flag)
            SpanType.ITALIC -> sp.setSpan(StyleSpan(Typeface.ITALIC), s.start, s.end, flag)
            SpanType.UNDERLINE -> sp.setSpan(UnderlineSpan(), s.start, s.end, flag)
            SpanType.STRIKETHROUGH -> sp.setSpan(StrikethroughSpan(), s.start, s.end, flag)
            SpanType.FONT_SIZE -> {
                val ratio = when (s.value) {
                    "xs" -> SIZE_XS
                    "small" -> SIZE_SMALL
                    "medium" -> SIZE_MEDIUM
                    "large" -> SIZE_LARGE
                    "xl" -> SIZE_XL
                    else -> null
                }
                if (ratio != null) {
                    sp.setSpan(RelativeSizeSpan(ratio), s.start, s.end, flag)
                }
            }
            SpanType.COLOR -> {
                val c = runCatching { Color.parseColor(s.value) }.getOrNull()
                if (c != null) sp.setSpan(ForegroundColorSpan(c), s.start, s.end, flag)
            }
        }
    }
}

fun Spannable.toTextSpans(): List<TextSpan> {
    val out = mutableListOf<TextSpan>()
    for (s in getSpans(0, length, Any::class.java)) {
        val start = getSpanStart(s)
        val end = getSpanEnd(s)
        if (start < 0 || end <= start) continue
        when (s) {
            is StyleSpan -> when (s.style) {
                Typeface.BOLD -> out += TextSpan(start, end, SpanType.BOLD)
                Typeface.ITALIC -> out += TextSpan(start, end, SpanType.ITALIC)
                else -> Unit
            }
            is UnderlineSpan -> out += TextSpan(start, end, SpanType.UNDERLINE)
            is StrikethroughSpan -> out += TextSpan(start, end, SpanType.STRIKETHROUGH)
            is RelativeSizeSpan -> {
                val v = when {
                    kotlin.math.abs(s.sizeChange - SIZE_XS) < 0.01f -> "xs"
                    kotlin.math.abs(s.sizeChange - SIZE_SMALL) < 0.01f -> "small"
                    kotlin.math.abs(s.sizeChange - SIZE_MEDIUM) < 0.01f -> "medium"
                    kotlin.math.abs(s.sizeChange - SIZE_LARGE) < 0.01f -> "large"
                    kotlin.math.abs(s.sizeChange - SIZE_XL) < 0.01f -> "xl"
                    else -> null
                }
                if (v != null) out += TextSpan(start, end, SpanType.FONT_SIZE, v)
            }
            is ForegroundColorSpan -> {
                val hex = "#%06X".format(0xFFFFFF and s.foregroundColor)
                out += TextSpan(start, end, SpanType.COLOR, hex)
            }
        }
    }
    return out
}
