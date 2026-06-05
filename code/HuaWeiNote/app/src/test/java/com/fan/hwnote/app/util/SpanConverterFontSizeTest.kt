package com.fan.hwnote.app.util

import android.text.SpannableString
import android.text.Spannable
import android.text.style.RelativeSizeSpan
import com.fan.hwnote.app.model.entity.SpanType
import com.fan.hwnote.app.model.entity.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpanConverterFontSizeTest {

    // ----- applyTo: TextSpan -> RelativeSizeSpan -----

    private fun assertApplyRatio(value: String, expectedRatio: Float) {
        val text = "hello"
        val sp = SpannableString(text)
        val spans = listOf(TextSpan(0, text.length, SpanType.FONT_SIZE, value))
        spans.applyTo(sp)
        val applied = sp.getSpans(0, sp.length, RelativeSizeSpan::class.java)
        assertEquals(1, applied.size)
        assertEquals(expectedRatio, applied[0].sizeChange, 0.01f)
    }

    @Test fun applyTo_xs_sets_075() = assertApplyRatio("xs", 0.75f)
    @Test fun applyTo_small_sets_085() = assertApplyRatio("small", 0.85f)
    @Test fun applyTo_medium_sets_100() = assertApplyRatio("medium", 1.0f)
    @Test fun applyTo_large_sets_125() = assertApplyRatio("large", 1.25f)
    @Test fun applyTo_xl_sets_150() = assertApplyRatio("xl", 1.5f)

    // ----- toTextSpans: RelativeSizeSpan -> TextSpan -----

    private fun assertReverseMap(expectedValue: String, ratio: Float) {
        val text = "hello"
        val sp = SpannableString(text)
        sp.setSpan(RelativeSizeSpan(ratio), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val result = sp.toTextSpans()
        assertEquals(1, result.size)
        assertEquals(SpanType.FONT_SIZE, result[0].type)
        assertEquals(expectedValue, result[0].value)
    }

    @Test fun toTextSpans_075_maps_to_xs() = assertReverseMap("xs", 0.75f)
    @Test fun toTextSpans_085_maps_to_small() = assertReverseMap("small", 0.85f)
    @Test fun toTextSpans_100_maps_to_medium() = assertReverseMap("medium", 1.0f)
    @Test fun toTextSpans_125_maps_to_large() = assertReverseMap("large", 1.25f)
    @Test fun toTextSpans_150_maps_to_xl() = assertReverseMap("xl", 1.5f)
}
