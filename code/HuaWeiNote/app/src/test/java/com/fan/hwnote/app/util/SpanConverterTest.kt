package com.fan.hwnote.app.util

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpanConverterTest {

    // ----- TextSpan -> Spannable -----

    @Test fun bold_apply_to_spannable() {
        val sp = SpannableString("hello world")
        listOf(TextSpan(0, 5, SpanType.BOLD)).applyTo(sp)
        val spans = sp.getSpans(0, sp.length, StyleSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(Typeface.BOLD, spans[0].style)
        assertEquals(0, sp.getSpanStart(spans[0]))
        assertEquals(5, sp.getSpanEnd(spans[0]))
    }

    @Test fun italic_apply() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 3, SpanType.ITALIC)).applyTo(sp)
        val spans = sp.getSpans(0, sp.length, StyleSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(Typeface.ITALIC, spans[0].style)
    }

    @Test fun underline_apply() {
        val sp = SpannableString("abc")
        listOf(TextSpan(1, 3, SpanType.UNDERLINE)).applyTo(sp)
        assertEquals(1, sp.getSpans(0, 3, UnderlineSpan::class.java).size)
    }

    @Test fun strike_apply() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 2, SpanType.STRIKETHROUGH)).applyTo(sp)
        assertEquals(1, sp.getSpans(0, 3, StrikethroughSpan::class.java).size)
    }

    @Test fun fontsize_large_apply() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 3, SpanType.FONT_SIZE, "large")).applyTo(sp)
        val arr = sp.getSpans(0, 3, RelativeSizeSpan::class.java)
        assertEquals(1, arr.size)
        assertEquals(1.25f, arr[0].sizeChange, 0.001f)
    }

    @Test fun fontsize_small_apply() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 3, SpanType.FONT_SIZE, "small")).applyTo(sp)
        val arr = sp.getSpans(0, 3, RelativeSizeSpan::class.java)
        assertEquals(0.85f, arr[0].sizeChange, 0.001f)
    }

    @Test fun fontsize_unknown_value_skipped() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 3, SpanType.FONT_SIZE, "huge")).applyTo(sp)
        assertEquals(0, sp.getSpans(0, 3, RelativeSizeSpan::class.java).size)
    }

    @Test fun color_apply() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 3, SpanType.COLOR, "#E53935")).applyTo(sp)
        val arr = sp.getSpans(0, 3, ForegroundColorSpan::class.java)
        assertEquals(1, arr.size)
        assertEquals(Color.parseColor("#E53935"), arr[0].foregroundColor)
    }

    @Test fun color_invalid_value_skipped() {
        val sp = SpannableString("abc")
        listOf(TextSpan(0, 3, SpanType.COLOR, "not-a-color")).applyTo(sp)
        assertEquals(0, sp.getSpans(0, 3, ForegroundColorSpan::class.java).size)
    }

    @Test fun multiple_spans_overlap_apply() {
        val sp = SpannableString("hello world")
        listOf(
            TextSpan(0, 5, SpanType.BOLD),
            TextSpan(3, 8, SpanType.UNDERLINE),
        ).applyTo(sp)
        assertEquals(1, sp.getSpans(0, 5, StyleSpan::class.java).size)
        assertEquals(1, sp.getSpans(3, 8, UnderlineSpan::class.java).size)
    }

    // ----- Spannable -> TextSpan -----

    @Test fun read_back_bold() {
        val sp = SpannableString("hi")
        sp.setSpan(StyleSpan(Typeface.BOLD), 0, 2, 0)
        val list = sp.toTextSpans()
        assertEquals(1, list.size)
        assertEquals(TextSpan(0, 2, SpanType.BOLD), list[0])
    }

    @Test fun read_back_color() {
        val sp = SpannableString("hi")
        sp.setSpan(ForegroundColorSpan(Color.parseColor("#1E88E5")), 0, 2, 0)
        val list = sp.toTextSpans()
        assertEquals(1, list.size)
        assertEquals(SpanType.COLOR, list[0].type)
        assertEquals("#1E88E5".uppercase(), list[0].value!!.uppercase())
    }

    @Test fun read_back_fontsize_large() {
        val sp = SpannableString("hi")
        sp.setSpan(RelativeSizeSpan(1.25f), 0, 2, 0)
        val list = sp.toTextSpans()
        assertEquals(SpanType.FONT_SIZE, list[0].type)
        assertEquals("large", list[0].value)
    }

    @Test fun roundtrip_all_six_types() {
        val original = listOf(
            TextSpan(0, 2, SpanType.BOLD),
            TextSpan(2, 4, SpanType.ITALIC),
            TextSpan(0, 4, SpanType.UNDERLINE),
            TextSpan(4, 6, SpanType.STRIKETHROUGH),
            TextSpan(0, 6, SpanType.FONT_SIZE, "large"),
            TextSpan(0, 6, SpanType.COLOR, "#43A047"),
        )
        val sp = SpannableString("abcdef")
        original.applyTo(sp)
        val recovered = sp.toTextSpans()
        fun List<TextSpan>.norm() = sortedWith(
            compareBy({ it.start }, { it.end }, { it.type.name })
        )
        fun TextSpan.norm(): TextSpan =
            if (type == SpanType.COLOR) copy(value = value?.uppercase()) else this
        val originalN = original.map { it.norm() }.norm()
        val recoveredN = recovered.map { it.norm() }.norm()
        assertEquals(originalN, recoveredN)
    }

    @Test fun empty_input_returns_empty_list() {
        val sp = SpannableString("abc")
        assertTrue(sp.toTextSpans().isEmpty())
    }
}
